import ExpoModulesCore
import Beethoven
import Pitchy


public class ReactNativeSimpleNotePitchDetectorModule: Module {

    // Configurable buffer size - can be changed from JS
    // Larger buffer = better low frequency detection, more latency
    // Smaller buffer = better high frequency detection, less latency
    private var bufferSize: UInt32 = 8192
    private var estimationStrategy: EstimationStrategy = .yin
    private var _pitchEngine: PitchEngine?

    public func definition() -> ModuleDefinition {

        Name("ReactNativeSimpleNotePitchDetector")

        Events("onChangeNote")

        Function("start") {
            self.pitchEngine.start()
        }

        Function("stop") {
            self.pitchEngine.stop()
        }

        Function("isRecording") {
            return self.pitchEngine.active
        }

        // Allow JS to configure the level threshold
        Function("setLevelThreshold") { (threshold: Double) in
            self.pitchEngine.levelThreshold = Float(threshold)
        }

        // Allow JS to configure the buffer size
        // Must be called before start() or after stop() to take effect
        // Common values: 4096 (better for high frequencies), 8192 (balanced), 16384 (better for low frequencies)
        Function("setBufferSize") { (size: Int) in
            let wasActive = self._pitchEngine?.active ?? false
            if wasActive {
                self._pitchEngine?.stop()
            }
            self.bufferSize = UInt32(size)
            self._pitchEngine = nil // Force recreation with new buffer size
            if wasActive {
                self.pitchEngine.start()
            }
        }

        // Get current buffer size
        Function("getBufferSize") {
            return Int(self.bufferSize)
        }

        // Allow JS to configure the estimation algorithm
        // iOS options: "yin", "hps", "barycentric", "quadradic", "jains", "quinnsFirst", "quinnsSecond", "maxValue"
        // Must be called before start() or will restart the engine
        Function("setAlgorithm") { (algorithm: String) in
            let wasActive = self._pitchEngine?.active ?? false
            if wasActive {
                self._pitchEngine?.stop()
            }

            switch algorithm.lowercased() {
            case "yin":
                self.estimationStrategy = .yin
            case "hps":
                self.estimationStrategy = .hps
            case "barycentric":
                self.estimationStrategy = .barycentric
            case "quadradic":
                self.estimationStrategy = .quadradic
            case "jains":
                self.estimationStrategy = .jains
            case "quinnsfirst":
                self.estimationStrategy = .quinnsFirst
            case "quinnssecond":
                self.estimationStrategy = .quinnsSecond
            case "maxvalue":
                self.estimationStrategy = .maxValue
            default:
                // Default to YIN if unknown
                self.estimationStrategy = .yin
            }

            self._pitchEngine = nil // Force recreation with new algorithm
            if wasActive {
                self.pitchEngine.start()
            }
        }

        // Get current algorithm name
        Function("getAlgorithm") {
            switch self.estimationStrategy {
            case .yin: return "yin"
            case .hps: return "hps"
            case .barycentric: return "barycentric"
            case .quadradic: return "quadradic"
            case .jains: return "jains"
            case .quinnsFirst: return "quinnsFirst"
            case .quinnsSecond: return "quinnsSecond"
            case .maxValue: return "maxValue"
            }
        }
    }

    var pitchEngine: PitchEngine {
        if let engine = _pitchEngine {
            return engine
        }
        let config = Config(bufferSize: bufferSize, estimationStrategy: estimationStrategy)
        let engine = PitchEngine(config: config, delegate: self)
        // Default threshold - can be adjusted from JS via setLevelThreshold()
        engine.levelThreshold = -30
        _pitchEngine = engine
        return engine
    }
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
