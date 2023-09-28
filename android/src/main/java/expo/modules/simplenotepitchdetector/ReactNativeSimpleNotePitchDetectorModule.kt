package expo.modules.simplenotepitchdetector

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.simplepitchdetector.PitchAnalyzer
import androidx.core.os.bundleOf

class ReactNativeSimpleNotePitchDetectorModule : Module() {

  override fun definition() = ModuleDefinition {
    Name("ReactNativeSimpleNotePitchDetector")

    Events("onChangePitch")

    Function("start") {

      val pitchAnalyzer = PitchAnalyzer { tone: String, frequency: Double ->
        this@ReactNativeSimpleNotePitchDetectorModule.sendEvent(
          "onChangePitch",
          bundleOf("tone" to tone, "frequency" to frequency)
        )
      }

      pitchAnalyzer.start()
    }
  }
}
