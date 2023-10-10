package expo.modules.simplenotepitchdetector

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.simplepitchdetector.PitchAnalyzer
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
      pitchAnalyzer.addOnChangeNoteListener { note: String ->
        this@ReactNativeSimpleNotePitchDetectorModule.sendEvent(
          "onChangeNote",
          bundleOf("note" to note)
        )
      }

      pitchAnalyzer.start()
    }

    Function("stop") {
        pitchAnalyzer.stop()
    }
  }
}
