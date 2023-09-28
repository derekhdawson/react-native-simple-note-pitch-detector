package expo.modules.simplenotepitchdetector

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
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

//      val context = appContext.reactContext!!
//      var activity = appContext.currentActivity!!
//      var permissionsGranted = ActivityCompat.checkSelfPermission(context, permissions[0]) == PackageManager.PERMISSION_GRANTED
//      if (!permissionsGranted) {
//        ActivityCompat.requestPermissions(activity, permissions, 200)
//      } else {
//        val pitchAnalyzer = PitchAnalyzer {
//          this@ReactNativeSimpleNotePitchDetectorModule.sendEvent(
//            "onChange",
//            bundleOf("tone" to it)
//          )
//        }
//        pitchAnalyzer.start()
//      }
    }
  }
}
