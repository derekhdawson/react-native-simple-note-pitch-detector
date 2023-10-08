import ExpoModulesCore
import Beethoven
import Pitchy

public class ReactNativeSimpleNotePitchDetectorModule: Module {
    
    var bufferSize = 3
    var notes = Array(repeating: "", count: 3)
    var counter = 0
    
    lazy var pitchEngine: PitchEngine = { [weak self] in
        let config = Config(estimationStrategy: .yin)
        let pitchEngine = PitchEngine(config: config, delegate: self)
        pitchEngine.levelThreshold = -30
        return pitchEngine
    }()
    
    func mostFrequent(array: [String]) -> String? {
        var counts = [String: Int]()

        // Count the values with using forEach
        array.forEach { counts[$0] = (counts[$0] ?? 0) + 1 }

        // Find the most frequent value and its count with max(by:)
        if let (value, count) = counts.max(by: {$0.1 < $1.1}) {
            if (count <= 1) {
                return nil
            }
            return value
        }

        return nil
    }
    
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
        
        if (counter == bufferSize) {
            var result = mostFrequent(array: notes)
            if (result != nil) {
                self.sendEvent("onChangePitch", ["note" :  result, "soundPressure" : ""])
            }
            counter = 0
        }
        
        do {
            let note = try Note(frequency: pitch.wave.frequency)
            notes[counter] = note.string
            counter += 1
        } catch {
            
        }

//        self.sendEvent("onChangePitch", ["note" :  pitch.note.string, "soundPressure" : pitchEngine.signalLevel])
    }

    public func pitchEngine(_ pitchEngine: PitchEngine, didReceiveError error: Error) {

    }

    public func pitchEngineWentBelowLevelThreshold(_ pitchEngine: PitchEngine) {

    }
}
