package expo.modules.simplenotepitchdetector

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.simplepitchdetector.PitchAnalyzer
import expo.modules.simplepitchdetector.PitchData
import androidx.core.os.bundleOf

class ReactNativeSimpleNotePitchDetectorModule : Module() {

  private val pitchAnalyzer = PitchAnalyzer()

  override fun definition() = ModuleDefinition {

    Name("ReactNativeSimpleNotePitchDetector")

    Events("onChangeNote")

    Function("isRecording") {
      pitchAnalyzer.isRecording()
    }

    Function("start") {
      pitchAnalyzer.setOnPitchDetectedListener { pitchData: PitchData ->
        this@ReactNativeSimpleNotePitchDetectorModule.sendEvent(
          "onChangeNote",
          bundleOf(
            "note" to pitchData.note,
            "octave" to pitchData.octave,
            "frequency" to pitchData.frequency.toDouble(),
            "offset" to pitchData.offset.toDouble()
          )
        )
      }

      pitchAnalyzer.start()
    }

    Function("stop") {
      pitchAnalyzer.stop()
    }

    Function("setLevelThreshold") { threshold: Double ->
      pitchAnalyzer.setLevelThreshold(threshold.toFloat())
    }

    // Allow JS to configure the buffer size
    // Must be called before start() to take effect
    // Common values: 1024 (better for high frequencies), 2048 (balanced), 4096 (better for low frequencies)
    Function("setBufferSize") { size: Int ->
      pitchAnalyzer.setBufferSize(size)
    }

    // Get current buffer size
    Function("getBufferSize") {
      pitchAnalyzer.getBufferSize()
    }

    // Allow JS to configure the estimation algorithm
    // Android options: "yin", "fft_yin", "mpm", "fft_pitch", "dynamic_wavelet", "amdf"
    // Must be called before start() to take effect
    Function("setAlgorithm") { algorithm: String ->
      pitchAnalyzer.setAlgorithm(algorithm)
    }

    // Get current algorithm name
    Function("getAlgorithm") {
      pitchAnalyzer.getAlgorithm()
    }
  }
}
