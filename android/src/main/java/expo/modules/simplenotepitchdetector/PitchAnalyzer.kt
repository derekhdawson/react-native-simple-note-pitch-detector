package expo.modules.simplepitchdetector

import android.util.Log
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.android.AudioDispatcherFactory
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
 * Pure HPS pitch detector for the full piano range (A0-C8, 27.5-4186Hz).
 * HPS with h=2,3,4,5 finds the fundamental by multiplying harmonic magnitudes.
 * Works for both missing fundamentals (low notes) and present fundamentals (high notes).
 * No MPM, no hybrid — single algorithm, single processor.
 */
class PitchAnalyzer {

    private val notes = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

    private lateinit var onPitchDetected: (PitchData) -> Unit
    private var onStatus: ((String, String) -> Unit)? = null
    private var isRecording = false
    private var levelThreshold = -30f
    private var sampleRate = 44100
    private var bufferSize = 2048
    private var algorithmName = "hps"

    private var dispatcher: AudioDispatcher? = null
    private var runner: Thread? = null

    private var fftSize = 0
    private var freqResolution = 0f
    private var hpsFft: FFT? = null
    private var fftBuffer: FloatArray? = null
    private var magnitudes: FloatArray? = null
    private var hpsProduct: FloatArray? = null
    private var hannWindow: FloatArray? = null

    // Full piano range: A0 (27.5Hz) to C8 (4186Hz)
    private val searchMinHz = 26f
    private val searchMaxHz = 4200f

    private val hpsProcessor = object : AudioProcessor {
        override fun process(audioEvent: AudioEvent): Boolean {
            val buffer = audioEvent.floatBuffer
            val dB = audioEvent.getdBSPL().toFloat()

            if (dB > levelThreshold) {
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

        // Search for peak in full piano range
        val minBin = (searchMinHz / freqResolution).toInt().coerceAtLeast(1)
        val maxBin = (searchMaxHz / freqResolution).toInt().coerceAtMost(hpsSize - 1)

        var peakBin = minBin
        var peakVal = hps[minBin]
        for (i in minBin + 1..maxBin) {
            if (hps[i] > peakVal) { peakBin = i; peakVal = hps[i] }
        }

        // SNR check: compare peak to local noise floor
        // Use ±200 bins (~270Hz) for wider context, or fall back to search bounds
        val snrStart = (peakBin - 200).coerceAtLeast(minBin)
        val snrEnd = (peakBin + 200).coerceAtMost(maxBin)
        var sum = 0.0
        for (i in snrStart..snrEnd) { sum += hps[i].toDouble() }
        val mean = sum / (snrEnd - snrStart + 1)
        val snr = if (mean > 0.0) peakVal.toDouble() / mean else 0.0
        if (snr < 10.0) return -1f

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

    private fun prepare() {
        try {
            val overlap = (bufferSize * 3) / 4
            logStatus("debug", "prepare() sampleRate=$sampleRate, bufferSize=$bufferSize, overlap=$overlap, algorithm=PURE_HPS_FULL_RANGE")

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
            logStatus("debug", "HPS: fftSize=$fftSize, freqRes=${freqResolution}Hz/bin, search=${searchMinHz}-${searchMaxHz}Hz, maxHpsHz=$maxHpsHz")

            dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, bufferSize, overlap)
            dispatcher?.addAudioProcessor(hpsProcessor)

            logStatus("debug", "Pure HPS full-range processor attached")
        } catch (e: Exception) {
            logStatus("error", "Error in prepare(): ${e.message}")
            throw e
        }
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
            logStatus("debug", "start() called")
            prepare()
            runner = Thread(dispatcher)
            logStatus("debug", "Thread created, starting...")
            runner?.start()
            isRecording = true
            logStatus("debug", "Recording started successfully")
        } catch (e: Exception) {
            logStatus("error", "Error in start(): ${e.message}")
            isRecording = false
        }
    }

    fun stop() {
        dispatcher?.stop()
        runner?.interrupt()
        isRecording = false
    }

    fun isRecording(): Boolean = isRecording
}
