package expo.modules.simplepitchdetector

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.round


class PitchAnalyzer {

    private val notes = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

    private lateinit var onChangePitch: (String, Double) -> Unit
    private var isRecording = false

    private var dispatcher: AudioDispatcher? = null
    private var processor: AudioProcessor? = null
    private var runner: Thread? = null

    private val handler = PitchDetectionHandler { res, e ->
        val pitchInHz = res.pitch
        process(pitchInHz)
    }

    private fun prepare() {
        processor =
            PitchProcessor(PitchEstimationAlgorithm.FFT_YIN, 22050f, 1024, handler)
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0)
        dispatcher?.addAudioProcessor(processor)
    }

    private fun process(pitchInHz: Float) {
        val freq = round(12 * (log2(pitchInHz / 440) / log2(2f)) + 69)
        val octave = (floor(freq / 12) - 1).toInt()
        val index = freq % 12

        if (!index.isNaN() && pitchInHz > 0) {
            val note = notes[index.toInt()]

            this.onChangePitch("$note$octave", pitchInHz.toDouble())
        }
    }

    fun addOnChangePitchListener(onChangePitch: (String, Double) -> Unit) {
        this.onChangePitch = onChangePitch
    }

    fun start() {
        prepare()
        runner = Thread(dispatcher)
        runner?.start()
        isRecording = true
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