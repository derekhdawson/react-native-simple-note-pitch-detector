import ExpoModulesCore

public class ReactNativeSimpleNotePitchDetectorModule: Module {

  public func definition() -> ModuleDefinition {

    Name("ReactNativeSimpleNotePitchDetector")

    Events("onChange")

    Function("hello") {
      return "Hello world! 👋"
    }

    // Defines a JavaScript function that always returns a Promise and whose native code
    // is by default dispatched on the different thread than the JavaScript runtime runs on.
    AsyncFunction("setValueAsync") { (value: String) in
      // Send an event to JavaScript.
      self.sendEvent("onChange", [
        "value": value
      ])
    }

  }
}
