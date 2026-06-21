import ExpoModulesCore
import Beethoven
import Pitchy
import AVFoundation


// Beethoven's built-in InputSignalTracker runs a second AVCaptureSession
// just to read audio levels. On iOS 26 that returns -inf, so the tap's
// `averageLevel > threshold` check is always false and pitch detection
// never sees a single buffer. This replacement uses ONE AVAudioEngine and
// computes the level directly from each buffer.
final class BufferLevelSignalTracker: NSObject, SignalTracker {
    weak var delegate: SignalTrackerDelegate?
    var levelThreshold: Float?

    private let bufferSize: AVAudioFrameCount
    private var audioEngine: AVAudioEngine?
    private var lastLevel: Float = -160.0
    private let bus = 0

    var mode: SignalTrackerMode { .record }
    var averageLevel: Float? { lastLevel }
    var peakLevel: Float? { lastLevel }

    init(bufferSize: AVAudioFrameCount = 8192, delegate: SignalTrackerDelegate? = nil) {
        self.bufferSize = bufferSize
        self.delegate = delegate
    }

    func start() throws {
        let engine = AVAudioEngine()
        audioEngine = engine

        let inputNode = engine.inputNode
        let format = inputNode.outputFormat(forBus: bus)

        inputNode.installTap(onBus: bus, bufferSize: bufferSize, format: format) { [weak self] buffer, time in
            guard let self = self else { return }
            guard let channelData = buffer.floatChannelData?[0] else { return }

            let frameLength = Int(buffer.frameLength)
            var sumSquares: Float = 0
            for i in 0..<frameLength {
                let sample = channelData[i]
                sumSquares += sample * sample
            }
            let rms = sqrt(sumSquares / Float(frameLength))
            let level = 20 * log10f(max(rms, 1e-10))
            self.lastLevel = level

            let threshold = self.levelThreshold ?? -160.0
            DispatchQueue.main.async {
                if level > threshold {
                    self.delegate?.signalTracker(self, didReceiveBuffer: buffer, atTime: time)
                } else {
                    self.delegate?.signalTrackerWentBelowLevelThreshold(self)
                }
            }
        }

        try engine.start()
    }

    func stop() {
        audioEngine?.inputNode.removeTap(onBus: bus)
        audioEngine?.stop()
        audioEngine = nil
    }
}


public class ReactNativeSimpleNotePitchDetectorModule: Module {

    // Configurable buffer size - can be changed from JS
    // Larger buffer = better low frequency detection, more latency
    // Smaller buffer = better high frequency detection, less latency
    private var bufferSize: UInt32 = 8192
    private var estimationStrategy: EstimationStrategy = .yin
    private var _pitchEngine: PitchEngine?
    private var belowThresholdLogged = false

    private func sendStatus(_ level: String, _ message: String) {
        self.sendEvent("onStatus", [
            "level": level,
            "message": message
        ])
    }

    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(
                .playAndRecord,
                options: [.defaultToSpeaker, .allowBluetoothHFP]
            )
            try session.setActive(true, options: .notifyOthersOnDeactivation)
            self.sendStatus(
                "debug",
                "AVAudioSession configured: category=playAndRecord, sampleRate=\(session.sampleRate), inputChannels=\(session.inputNumberOfChannels), inputAvailable=\(session.isInputAvailable)"
            )
        } catch {
            self.sendStatus("error", "Failed to configure AVAudioSession: \(error.localizedDescription)")
        }
    }

    public func definition() -> ModuleDefinition {

        Name("ReactNativeSimpleNotePitchDetector")

        Events("onChangeNote", "onStatus")

        Function("start") {
            self.sendStatus("debug", "start() called with bufferSize=\(self.bufferSize), algorithm=\(self.estimationStrategy)")
            self.configureAudioSession()
            self.pitchEngine.start()
            self.sendStatus("debug", "Recording started successfully")
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
        let signalTracker = BufferLevelSignalTracker(bufferSize: bufferSize)
        let engine = PitchEngine(config: config, signalTracker: signalTracker, delegate: self)
        // Default threshold - can be adjusted from JS via setLevelThreshold()
        engine.levelThreshold = -60
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
        self.sendStatus("error", "PitchEngine error: \(error.localizedDescription)")
    }

    public func pitchEngineWentBelowLevelThreshold(_ pitchEngine: PitchEngine) {
        // Log once per session to avoid spamming
        if !belowThresholdLogged {
            self.sendStatus("debug", "Audio level below threshold (audio is reaching Beethoven but too quiet)")
            belowThresholdLogged = true
        }
    }
}
