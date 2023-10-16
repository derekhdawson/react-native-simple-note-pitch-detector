package expo.modules.simplepitchdetector

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
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

    private lateinit var onChangeNote: (String) -> Unit
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
        val index = round(12 * (log2(pitchInHz / 440) / log2(2f)) + 69) % 12

        if (!index.isNaN() && pitchInHz > 0) {
            val note = notes[index.toInt()]
            var noteAndDecibel = NoteAndDecibel(note, decibel)

            if (counter == notesBuffer.size) {
                var mostFrequentNote = getMostFrequentNote(notesBuffer)

                if (mostFrequentNote != null) {
                    onChangeNote(mostFrequentNote)
                }

                counter = 0
            }

            notesBuffer[counter] = noteAndDecibel
            counter += 1
        }
    }

    fun addOnChangeNoteListener(onChangeNote: (String) -> Unit) {
        this.onChangeNote = onChangeNote
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

    private fun getMostFrequentNote(notes: Array<NoteAndDecibel>) : String? {
        val noteToDecibel = mutableMapOf<String, Float>();
        notes.forEach {
            if (!noteToDecibel.containsKey(it.note)) {
                noteToDecibel[it.note] = 0f
            }

            noteToDecibel[it.note] = noteToDecibel[it.note]!! + it.decibel
        }

        var mostFrequentNote = ""
        var maxDecibel = 0f

        noteToDecibel.forEach {
            if (mostFrequentNote == "" || it.value < maxDecibel) {
                mostFrequentNote = it.key
                maxDecibel = it.value
            }
        }

        if (mostFrequentNote == "") {
            return null
        }

        return mostFrequentNote
    }
}