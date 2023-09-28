package expo.modules.simplenotepitchdetector

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.simplepitchdetector.PitchAnalyzer
import androidx.core.os.bundleOf

class ReactNativeSimpleNotePitchDetectorModule : Module() {

  private val pitchAnalyzer = PitchAnalyzer()

  override fun definition() = ModuleDefinition {

    Name("ReactNativeSimpleNotePitchDetector")

    Events("onChangePitch")

    Function("isRecording") {
      pitchAnalyzer.isRecording()
    }

    Function("start") {
      pitchAnalyzer.addOnChangePitchListener { note: String, frequency: Double ->
        this@ReactNativeSimpleNotePitchDetectorModule.sendEvent(
          "onChangePitch",
          bundleOf("note" to note, "frequency" to frequency)
        )
      }

      pitchAnalyzer.start()
    }

    Function("stop") {
        pitchAnalyzer.stop()
    }
  }
}
