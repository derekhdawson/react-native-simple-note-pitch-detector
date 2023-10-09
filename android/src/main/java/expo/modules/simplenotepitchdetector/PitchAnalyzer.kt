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

data class NoteAndDecibel(
    val note: String = "",
    val decibel: Float = 0f
)

class PitchAnalyzer {

    private val notes = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
    private val notesBuffer = Array(8) { NoteAndDecibel() }
    private var counter = 0

    private lateinit var onChangePitch: (String) -> Unit
    private var isRecording = false

    private var dispatcher: AudioDispatcher? = null
    private var processor: AudioProcessor? = null
    private var runner: Thread? = null

    private val handler = PitchDetectionHandler { res, e ->
        val pitchInHz = res.pitch
        val decibel = e.getdBSPL().toFloat()
        process(pitchInHz, decibel)
    }

    private fun prepare() {
        processor =
            PitchProcessor(PitchEstimationAlgorithm.FFT_YIN, 22050f, 1024, handler)
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0)
        dispatcher?.addAudioProcessor(processor)
    }

    private fun process(pitchInHz: Float, decibel: Float) {
        val freq = round(12 * (log2(pitchInHz / 440) / log2(2f)) + 69)
        val octave = (floor(freq / 12) - 1).toInt()
        val index = freq % 12

        if (!index.isNaN() && pitchInHz > 0) {
            val note = notes[index.toInt()]
            var noteAndDecibel = NoteAndDecibel(note, decibel)

            if (counter == notesBuffer.size) {
                var s = ""
                for (noteAndDecibel in notesBuffer) {
                    s += noteAndDecibel.note + "*" + noteAndDecibel.decibel + ","
                }
                this.onChangePitch(s.substring(0, s.length - 1))
                counter = 0
            }

            notesBuffer[counter] = noteAndDecibel
            counter += 1
        }
    }

    fun addOnChangePitchListener(onChangePitch: (String) -> Unit) {
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