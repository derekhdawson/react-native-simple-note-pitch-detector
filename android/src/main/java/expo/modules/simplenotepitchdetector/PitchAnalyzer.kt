package expo.modules.simplepitchdetector

import android.util.Log
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchDetectionResult
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import be.tarsos.dsp.util.fft.FFT
import kotlin.math.log2
import kotlin.math.round
import kotlin.math.ln
import kotlin.math.cos
import kotlin.math.PI

private const val TAG = "PitchAnalyzer"

data class PitchData(
    val note: String,
    val octave: Int,
    val frequency: Float,
    val amplitude: Float,
    val offset: Float
)

/**
 * Dual-dispatcher pitch detector:
 * - HPS (Harmonic Product Spectrum, h=2,3,4,5) on its own AudioDispatcher for low notes (<200Hz)
 * - MPM (McLeod Pitch Method) on a separate AudioDispatcher for mid/high notes (>=200Hz)
 *
 * Two completely separate microphone streams to avoid any shared state or buffer corruption.
 * Previous experiments proved that ANY combination of HPS+MPM in the same dispatcher degrades HPS.
 */
class PitchAnalyzer {

    private val notes = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

    private lateinit var onPitchDetected: (PitchData) -> Unit
    private var onStatus: ((String, String) -> Unit)? = null
    private var isRecording = false
    private var levelThreshold = -30f
    private var sampleRate = 44100
    private var bufferSize = 8192
    private var algorithmName = "hps"

    // HPS dispatcher (low notes)
    private var hpsDispatcher: AudioDispatcher? = null
    private var hpsThread: Thread? = null

    // MPM dispatcher (mid/high notes)
    private var mpmDispatcher: AudioDispatcher? = null
    private var mpmThread: Thread? = null

    // HPS pre-allocated buffers
    private var fftSize = 0
    private var freqResolution = 0f
    private var hpsFft: FFT? = null
    private var fftBuffer: FloatArray? = null
    private var magnitudes: FloatArray? = null
    private var hpsProduct: FloatArray? = null
    private var hannWindow: FloatArray? = null

    // Adaptive noise gate: tracks background noise floor per-dispatcher
    // A note must be this many dB above the noise floor to be emitted
    private val noiseGateMarginDb = 10f
    // Exponential moving average decay for noise floor (0.01 = slow adaptation)
    private val noiseFloorAlpha = 0.01f
    @Volatile private var hpsNoiseFloor = -60f
    @Volatile private var yinNoiseFloor = -60f

    // HPS searches low range only: A0 (27.5Hz) to ~G3 (196Hz)
    private val hpsMinHz = 26f
    private val hpsMaxHz = 200f

    // MPM crossover: only emit MPM results >= this frequency
    private val mpmMinHz = 200f
    // MPM max: reject garbage above piano range (C8 = 4186Hz)
    private val mpmMaxHz = 4200f

    private fun updateNoiseFloor(currentFloor: Float, dB: Float): Float {
        // Only update noise floor when signal is quiet (close to current floor)
        // This prevents played notes from raising the floor
        return if (dB < currentFloor + noiseGateMarginDb) {
            currentFloor + noiseFloorAlpha * (dB - currentFloor)
        } else {
            currentFloor
        }
    }

    private val hpsProcessor = object : AudioProcessor {
        override fun process(audioEvent: AudioEvent): Boolean {
            val buffer = audioEvent.floatBuffer
            val dB = audioEvent.getdBSPL().toFloat()

            // Update noise floor estimate
            hpsNoiseFloor = updateNoiseFloor(hpsNoiseFloor, dB)

            // Adaptive gate: only process if dB is above noise floor + margin
            if (dB > hpsNoiseFloor + noiseGateMarginDb) {
                try {
                    val pitch = detectPitchHPS(buffer)
                    if (pitch > 0f) {
                        emitPitch(pitch, dB)
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "HPS failed: ${ex.message}")
                }
            }
            return true
        }

        override fun processingFinished() {}
    }

    private fun detectPitchHPS(buffer: FloatArray): Float {
        val fft = hpsFft ?: return -1f
        val fftBuf = fftBuffer ?: return -1f
        val mags = magnitudes ?: return -1f
        val hps = hpsProduct ?: return -1f
        val window = hannWindow ?: return -1f

        // Window and zero-pad
        for (i in buffer.indices) {
            fftBuf[i] = buffer[i] * window[i]
        }
        for (i in buffer.size until fftSize) {
            fftBuf[i] = 0f
        }

        // FFT
        fft.forwardTransform(fftBuf)
        fft.modulus(fftBuf, mags)

        // HPS: multiply magnitudes at harmonics 2, 3, 4, 5
        val hpsSize = mags.size / 5
        for (i in 0 until hpsSize) {
            hps[i] = mags[2 * i] * mags[3 * i] * mags[4 * i] * mags[5 * i]
        }

        // Search for peak in low range only
        val minBin = (hpsMinHz / freqResolution).toInt().coerceAtLeast(1)
        val maxBin = (hpsMaxHz / freqResolution).toInt().coerceAtMost(hpsSize - 1)

        var peakBin = minBin
        var peakVal = hps[minBin]
        for (i in minBin + 1..maxBin) {
            if (hps[i] > peakVal) { peakBin = i; peakVal = hps[i] }
        }

        // SNR check
        val snrStart = (peakBin - 200).coerceAtLeast(minBin)
        val snrEnd = (peakBin + 200).coerceAtMost(maxBin)
        var sum = 0.0
        for (i in snrStart..snrEnd) { sum += hps[i].toDouble() }
        val mean = sum / (snrEnd - snrStart + 1)
        val snr = if (mean > 0.0) peakVal.toDouble() / mean else 0.0
        if (snr < 12.0) return -1f

        return interpolatePeak(hps, peakBin, minBin, maxBin) * freqResolution
    }

    private fun interpolatePeak(spectrum: FloatArray, peakBin: Int, minBin: Int, maxBin: Int): Float {
        if (peakBin <= minBin || peakBin >= maxBin) return peakBin.toFloat()
        val prev = spectrum[peakBin - 1].coerceAtLeast(1e-30f)
        val curr = spectrum[peakBin].coerceAtLeast(1e-30f)
        val next = spectrum[peakBin + 1].coerceAtLeast(1e-30f)
        val logPrev = ln(prev)
        val logCurr = ln(curr)
        val logNext = ln(next)
        val denom = logPrev - 2f * logCurr + logNext
        return if (denom != 0f) {
            peakBin + 0.5f * (logPrev - logNext) / denom
        } else {
            peakBin.toFloat()
        }
    }

    private fun prepareHPS() {
        val overlap = (bufferSize * 3) / 4
        logStatus("debug", "prepareHPS() sampleRate=$sampleRate, bufferSize=$bufferSize, overlap=$overlap")

        // 4x zero-padding for frequency resolution
        fftSize = bufferSize * 4
        freqResolution = sampleRate.toFloat() / fftSize
        hpsFft = FFT(fftSize)
        fftBuffer = FloatArray(fftSize)
        magnitudes = FloatArray(fftSize / 2)
        hpsProduct = FloatArray(fftSize / (2 * 5))
        hannWindow = FloatArray(bufferSize) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (bufferSize - 1)))).toFloat()
        }

        val maxHpsHz = (fftSize / (2 * 5)) * freqResolution
        logStatus("debug", "HPS: fftSize=$fftSize, freqRes=${freqResolution}Hz/bin, search=${hpsMinHz}-${hpsMaxHz}Hz, maxHpsHz=$maxHpsHz")

        hpsDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, bufferSize, overlap)
        hpsDispatcher?.addAudioProcessor(hpsProcessor)

        logStatus("debug", "HPS dispatcher prepared (low notes <${hpsMaxHz}Hz)")
    }

    private fun prepareMPM() {
        val mpmBufferSize = 2048
        val mpmOverlap = (mpmBufferSize * 3) / 4
        logStatus("debug", "prepareMPM() sampleRate=$sampleRate, bufferSize=$mpmBufferSize, overlap=$mpmOverlap")

        val pitchHandler = PitchDetectionHandler { result: PitchDetectionResult, event: AudioEvent ->
            val pitch = result.pitch
            val dB = event.getdBSPL().toFloat()

            // Update YIN noise floor
            yinNoiseFloor = updateNoiseFloor(yinNoiseFloor, dB)

            if (pitch > 0 && pitch >= mpmMinHz && pitch <= mpmMaxHz && result.isPitched
                && dB > yinNoiseFloor + noiseGateMarginDb) {
                emitPitch(pitch, dB)
            }
        }

        // Use YIN instead of MPM — McLeodPitchMethod.peakPicking throws AssertionError
        // when two AudioRecord instances compete for the microphone
        val yinPitchProcessor = PitchProcessor(PitchEstimationAlgorithm.YIN, sampleRate.toFloat(), mpmBufferSize, pitchHandler)
        val safeYinProcessor = object : AudioProcessor {
            override fun process(audioEvent: AudioEvent): Boolean {
                try {
                    return yinPitchProcessor.process(audioEvent)
                } catch (e: Exception) {
                    Log.w(TAG, "YIN error (ignored): ${e.message}")
                    return true
                }
            }
            override fun processingFinished() {
                yinPitchProcessor.processingFinished()
            }
        }

        mpmDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, mpmBufferSize, mpmOverlap)
        mpmDispatcher?.addAudioProcessor(safeYinProcessor)

        logStatus("debug", "YIN dispatcher prepared (mid/high notes ${mpmMinHz}-${mpmMaxHz}Hz)")
    }

    private fun emitPitch(pitchInHz: Float, decibel: Float) {
        if (pitchInHz <= 0 || pitchInHz.isNaN()) return

        val midiNote = 12 * log2(pitchInHz / 440f) + 69
        val roundedMidiNote = round(midiNote).toInt()
        val noteIndex = ((roundedMidiNote % 12) + 12) % 12
        val octave = (roundedMidiNote / 12) - 1
        val centsOff = (midiNote - roundedMidiNote) * 100

        onPitchDetected(PitchData(
            note = notes[noteIndex],
            octave = octave,
            frequency = pitchInHz,
            amplitude = decibel,
            offset = centsOff
        ))
    }

    fun setOnPitchDetectedListener(listener: (PitchData) -> Unit) {
        this.onPitchDetected = listener
    }

    fun setOnStatusListener(listener: (String, String) -> Unit) {
        this.onStatus = listener
    }

    private fun logStatus(level: String, message: String) {
        when (level) {
            "debug" -> Log.d(TAG, message)
            "error" -> Log.e(TAG, message)
            "verbose" -> Log.v(TAG, message)
            else -> Log.d(TAG, message)
        }
        onStatus?.invoke(level, message)
    }

    fun setLevelThreshold(threshold: Float) {
        this.levelThreshold = threshold
    }

    fun setSampleRate(rate: Int) {
        this.sampleRate = rate
    }

    fun getSampleRate(): Int = this.sampleRate

    fun setBufferSize(size: Int) {
        this.bufferSize = size
    }

    fun getBufferSize(): Int = this.bufferSize

    fun setAlgorithm(name: String) {
        this.algorithmName = name.lowercase()
    }

    fun getAlgorithm(): String = this.algorithmName

    fun start() {
        try {
            logStatus("debug", "start() called — dual dispatcher mode (HPS + MPM)")

            // Start HPS dispatcher first
            prepareHPS()
            hpsThread = Thread(hpsDispatcher)
            hpsThread?.name = "HPS-Thread"
            hpsThread?.start()
            logStatus("debug", "HPS thread started")

            // Start MPM dispatcher second
            prepareMPM()
            mpmThread = Thread(mpmDispatcher)
            mpmThread?.name = "MPM-Thread"
            mpmThread?.start()
            logStatus("debug", "MPM thread started")

            isRecording = true
            logStatus("debug", "Dual dispatcher recording started successfully")
        } catch (e: Exception) {
            logStatus("error", "Error in start(): ${e.message}")
            // Clean up whatever was started
            stop()
            isRecording = false
        }
    }

    fun stop() {
        hpsDispatcher?.stop()
        mpmDispatcher?.stop()
        hpsThread?.interrupt()
        mpmThread?.interrupt()
        hpsDispatcher = null
        mpmDispatcher = null
        hpsThread = null
        mpmThread = null
        isRecording = false
    }

    fun isRecording(): Boolean = isRecording
}
