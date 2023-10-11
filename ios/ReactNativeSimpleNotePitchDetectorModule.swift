import ExpoModulesCore
import Beethoven
import Pitchy


let NOTE_BUFFER_SIZE = 3

public class ReactNativeSimpleNotePitchDetectorModule: Module {
    
    var notes = Array(repeating: "", count: NOTE_BUFFER_SIZE)
    var counter = 0
    
    public func definition() -> ModuleDefinition {

        Name("ReactNativeSimpleNotePitchDetector")

        Events("onChangeNote")

        Function("start") {
            pitchEngine.start()
        }

        Function("stop") {
            pitchEngine.stop()
        }
        
        Function("isRecording") {
            return pitchEngine.active
        }
    }
    
    lazy var pitchEngine: PitchEngine = { [weak self] in
        let config = Config(estimationStrategy: .yin)
        let pitchEngine = PitchEngine(config: config, delegate: self)
        pitchEngine.levelThreshold = -30
        return pitchEngine
    }()
    
    func getMostFrequentNote(notes: [String]) -> String? {
        var counts = [String: Int]()

        notes.forEach { counts[$0] = (counts[$0] ?? 0) + 1 }
        
        if let (value, count) = counts.max(by: {$0.1 < $1.1}) {
            if (count <= 1) {
                return nil
            }
            return value
        }

        return nil
    }
}

extension ReactNativeSimpleNotePitchDetectorModule: PitchEngineDelegate {

    public func pitchEngine(_ pitchEngine: PitchEngine, didReceivePitch pitch: Pitch) {
      
        let offsetPercentage = pitch.closestOffset.percentage
        let absOffsetPercentage = abs(offsetPercentage)

        guard absOffsetPercentage > 1.0 else {
            return
        }
        
        if (counter == notes.count) {
            var note = getMostFrequentNote(notes: notes)
            if (note != nil) {
                self.sendEvent("onChangeNote", ["note" :  note])
            }
            counter = 0
        }
        
        do {
            let note = try Note(frequency: pitch.wave.frequency)
            let noteStr = note.string
            notes[counter] = String(noteStr.prefix(noteStr.count - 1))
            counter += 1
        } catch {
            
        }
    }

    public func pitchEngine(_ pitchEngine: PitchEngine, didReceiveError error: Error) {

    }

    public func pitchEngineWentBelowLevelThreshold(_ pitchEngine: PitchEngine) {

    }
}
