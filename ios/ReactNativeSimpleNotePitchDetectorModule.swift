import ExpoModulesCore
import Beethoven
import Pitchy

public class ReactNativeSimpleNotePitchDetectorModule: Module {
    
    lazy var pitchEngine: PitchEngine = { [weak self] in
        let config = Config(estimationStrategy: .yin)
        let pitchEngine = PitchEngine(config: config, delegate: self)
        pitchEngine.levelThreshold = -30
        return pitchEngine
    }()
    
    var isRecording = false

    public func definition() -> ModuleDefinition {

        Name("ReactNativeSimpleNotePitchDetector")

        Events("onChangePitch")

        Function("start") {
            pitchEngine.start()
            isRecording = true
        }

        Function("stop") {
            pitchEngine.stop()
            isRecording = false
        }
        
        Function("isRecording") {
            return isRecording
        }
    }
}

extension ReactNativeSimpleNotePitchDetectorModule: PitchEngineDelegate {

    public func pitchEngine(_ pitchEngine: PitchEngine, didReceivePitch pitch: Pitch) {
      
        let offsetPercentage = pitch.closestOffset.percentage
        let absOffsetPercentage = abs(offsetPercentage)

        guard absOffsetPercentage > 1.0 else {
            return
        }

        self.sendEvent("onChangePitch", ["note" :  pitch.note.string, "frequency" : pitch.frequency])
    }

    public func pitchEngine(_ pitchEngine: PitchEngine, didReceiveError error: Error) {

    }

    public func pitchEngineWentBelowLevelThreshold(_ pitchEngine: PitchEngine) {

    }
}
