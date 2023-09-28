package expo.modules.simplepitchdetector

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeMap
import kotlin.math.log2
import kotlin.math.round


class PitchAnalyzer {

    private val tones = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

    private var onChangePitch: (String, Double) -> Unit

    private var dispatcher: AudioDispatcher? = null
    private var processor: AudioProcessor? = null
    private var runner: Thread? = null

    constructor(callback: (String, Double) -> Unit) {
        this.onChangePitch = callback
    }

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
        val index = round(12 * (log2(pitchInHz / 440) / log2(2f)) + 69) % 12

        if (!index.isNaN() && pitchInHz > 0) {
            val tone = tones[index.toInt()]
            val frequency = pitchInHz.toDouble()

            this.onChangePitch(tone, frequency)
        }
    }

    fun start() {
        prepare()
        runner = Thread(dispatcher)
        runner?.start()
    }
    fun stop() {
        dispatcher?.stop()
        runner?.interrupt()
    }
}