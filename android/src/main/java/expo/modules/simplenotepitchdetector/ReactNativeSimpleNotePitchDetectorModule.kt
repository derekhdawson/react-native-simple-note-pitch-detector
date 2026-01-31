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
  }
}
