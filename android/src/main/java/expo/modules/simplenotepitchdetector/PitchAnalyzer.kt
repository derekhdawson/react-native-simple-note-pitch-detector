package expo.modules.simplepitchdetector

import android.util.Log
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import kotlin.math.log2
import kotlin.math.round
import kotlin.math.pow
import kotlin.math.abs

private const val TAG = "PitchAnalyzer"

data class PitchData(
    val note: String,
    val octave: Int,
    val frequency: Float,
    val amplitude: Float,
    val offset: Float
)

class PitchAnalyzer {

    private val notes = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

    private lateinit var onPitchDetected: (PitchData) -> Unit
    private var onStatus: ((String, String) -> Unit)? = null
    private var isRecording = false
    private var levelThreshold = -30f
    // Configurable buffer size - can be changed from JS
    // Larger buffer = better low frequency detection, more latency
    // Smaller buffer = better high frequency detection, less latency
    private var bufferSize = 2048
    // Configurable algorithm - can be changed from JS
    private var algorithm = PitchEstimationAlgorithm.FFT_YIN
    private var algorithmName = "fft_yin"

    private var dispatcher: AudioDispatcher? = null
    private var processor: AudioProcessor? = null
    private var runner: Thread? = null

    private val handler = PitchDetectionHandler { res, e ->
        val pitchInHz = res.pitch
        val decibel = e.getdBSPL().toFloat()
        // Only process if above the level threshold
        if (decibel > levelThreshold) {
            // Note: Not logging every pitch detection to JS to avoid flooding
            // Use adb logcat for verbose pitch logs
            Log.v(TAG, "Pitch detected: ${pitchInHz}Hz at ${decibel}dB")
            process(pitchInHz, decibel)
        }
    }

    private fun prepare() {
        try {
            logStatus("debug", "prepare() called with bufferSize=$bufferSize, algorithm=$algorithmName")
            processor = PitchProcessor(algorithm, 22050f, bufferSize, handler)
            logStatus("debug", "PitchProcessor created successfully")
            dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, bufferSize, 0)
            logStatus("debug", "AudioDispatcher created successfully: $dispatcher")
            dispatcher?.addAudioProcessor(processor)
            logStatus("debug", "AudioProcessor added to dispatcher")
        } catch (e: Exception) {
            logStatus("error", "Error in prepare(): ${e.message}")
            throw e
        }
    }

    private fun process(pitchInHz: Float, decibel: Float) {
        if (pitchInHz <= 0 || pitchInHz.isNaN()) {
            return
        }

        // Calculate MIDI note number (A4 = 440Hz = MIDI 69)
        val midiNote = 12 * log2(pitchInHz / 440f) + 69
        val roundedMidiNote = round(midiNote).toInt()

        // Calculate note index (0-11) and octave
        val noteIndex = ((roundedMidiNote % 12) + 12) % 12
        val octave = (roundedMidiNote / 12) - 1

        // Calculate offset from perfect pitch (in cents, then convert to percentage)
        // 100 cents = 1 semitone, so we express as percentage of a semitone
        val centsOff = (midiNote - roundedMidiNote) * 100
        val offsetPercentage = centsOff // Already in a reasonable range (-50 to +50)

        val note = notes[noteIndex]

        val pitchData = PitchData(
            note = note,
            octave = octave,
            frequency = pitchInHz,
            amplitude = decibel,
            offset = offsetPercentage
        )

        onPitchDetected(pitchData)
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

    fun setBufferSize(size: Int) {
        this.bufferSize = size
    }

    fun getBufferSize(): Int {
        return this.bufferSize
    }

    fun setAlgorithm(name: String) {
        this.algorithmName = name.lowercase()
        this.algorithm = when (this.algorithmName) {
            "yin" -> PitchEstimationAlgorithm.YIN
            "fft_yin" -> PitchEstimationAlgorithm.FFT_YIN
            "mpm" -> PitchEstimationAlgorithm.MPM
            "fft_pitch" -> PitchEstimationAlgorithm.FFT_PITCH
            "dynamic_wavelet" -> PitchEstimationAlgorithm.DYNAMIC_WAVELET
            "amdf" -> PitchEstimationAlgorithm.AMDF
            else -> PitchEstimationAlgorithm.FFT_YIN // Default
        }
    }

    fun getAlgorithm(): String {
        return this.algorithmName
    }

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

    fun isRecording(): Boolean {
        return isRecording
    }
}