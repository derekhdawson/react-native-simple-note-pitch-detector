import ExpoModulesCore
import Beethoven
import Pitchy


public class ReactNativeSimpleNotePitchDetectorModule: Module {

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

        // Allow JS to configure the level threshold
        Function("setLevelThreshold") { (threshold: Double) in
            self.pitchEngine.levelThreshold = Float(threshold)
        }
    }

    lazy var pitchEngine: PitchEngine = { [weak self] in
        // Larger buffer (8192) = more accurate pitch detection, slightly more latency
        // Using .yin which is generally best for monophonic instruments
        let config = Config(bufferSize: 8192, estimationStrategy: .yin)
        let pitchEngine = PitchEngine(config: config, delegate: self)
        // Default threshold - can be adjusted from JS via setLevelThreshold()
        pitchEngine.levelThreshold = -30
        return pitchEngine
    }()
}

extension ReactNativeSimpleNotePitchDetectorModule: PitchEngineDelegate {

    public func pitchEngine(_ pitchEngine: PitchEngine, didReceivePitch pitch: Pitch) {

        let frequency = pitch.wave.frequency

        // Offset from the nearest note (in percentage, can be negative or positive)
        // Negative = flat, Positive = sharp
        let offsetPercentage = pitch.closestOffset.percentage

        do {
            let note = try Note(frequency: frequency)
            let noteStr = note.string
            // Remove the octave number (last character) to get just the note name
            let noteName = String(noteStr.prefix(noteStr.count - 1))
            let octave = note.octave

            // Send all raw data to JS - let the app decide how to filter
            self.sendEvent("onChangeNote", [
                "note": noteName,
                "octave": octave,
                "frequency": frequency,
                "offset": offsetPercentage
            ])
        } catch {
            // Could not determine note from frequency - skip
        }
    }

    public func pitchEngine(_ pitchEngine: PitchEngine, didReceiveError error: Error) {
        // Optionally send error events to JS
    }

    public func pitchEngineWentBelowLevelThreshold(_ pitchEngine: PitchEngine) {
        // Audio dropped below threshold - could notify JS if needed
    }
}
