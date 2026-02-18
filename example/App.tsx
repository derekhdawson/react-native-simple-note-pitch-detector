import * as React from "react";
import { useState, useRef, useCallback } from "react";
import { StyleSheet, Text, View, Platform, PermissionsAndroid, ScrollView } from "react-native";

import * as ReactNativeSimpleNotePitchDetector from "react-native-simple-note-pitch-detector";

export default function App() {

  const [note, setNote] = useState("");
  const [noteDisplay, setNoteDisplay] = useState("");
  const [status, setStatus] = useState("initializing...");
  const [logs, setLogs] = useState<string[]>([]);
  const startTime = useRef(Date.now());
  const scrollViewRef = useRef<ScrollView>(null);

  const addLog = useCallback((msg: string) => {
    setLogs(prev => [...prev.slice(-50), msg]);
  }, []);

  React.useEffect(() => {
    let statusSubscription: { remove: () => void } | null = null;
    let noteSubscription: { remove: () => void } | null = null;

    const init = async () => {
      // Request microphone permission on Android
      if (Platform.OS === "android") {
        const granted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.RECORD_AUDIO
        );
        console.log(`[Permission] RECORD_AUDIO: ${granted}`);
        setStatus(`Permission: ${granted}`);
        if (granted !== PermissionsAndroid.RESULTS.GRANTED) {
          setStatus("Microphone permission denied");
          return;
        }
      }

      // Listen for native status/debug messages
      statusSubscription = ReactNativeSimpleNotePitchDetector.onStatus((event) => {
        console.log(`[Native ${event.level}] ${event.message}`);
        setStatus(`[${event.level}] ${event.message}`);
      });

      startTime.current = Date.now();
      setStatus("Starting pitch detection...");
      // Very sensitive for testing — catches everything
      ReactNativeSimpleNotePitchDetector.setLevelThreshold(-100);
      // Buffer for pitch detection (8192 = good balance of latency + low freq resolution)
      ReactNativeSimpleNotePitchDetector.setBufferSize(8192);
      // MPM detects more low notes than YIN; octave errors corrected by HPS
      ReactNativeSimpleNotePitchDetector.setAlgorithm("mpm");
      ReactNativeSimpleNotePitchDetector.start();

      noteSubscription = ReactNativeSimpleNotePitchDetector.onChangeNote((event) => {
        const elapsed = ((Date.now() - startTime.current) / 1000).toFixed(2);
        const logMsg = `[${elapsed}s] ${event.note}${event.octave} freq=${event.frequency.toFixed(1)}Hz off=${event.offset.toFixed(1)}%`;
        console.log(logMsg);
        addLog(logMsg);
        setNote(event.note);
        setNoteDisplay(`${event.note}${event.octave}`);
      });
    };

    init();

    return () => {
      statusSubscription?.remove();
      noteSubscription?.remove();
      ReactNativeSimpleNotePitchDetector.stop();
    };
  }, []);

  return (
    <View style={styles.container}>
      <Text style={styles.statusText}>{status}</Text>
      {
        note === "" ? <Text>waiting for note...</Text> : <Text style={styles.noteText}>{noteDisplay}</Text>
      }
      <ScrollView
        ref={scrollViewRef}
        style={styles.logContainer}
        onContentSizeChange={() => scrollViewRef.current?.scrollToEnd()}
      >
        {logs.map((log, i) => (
          <Text key={i} style={styles.logText}>{log}</Text>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#fff",
    alignItems: "center",
    justifyContent: "center",
    padding: 20,
  },
  statusText: {
    fontSize: 14,
    color: "#666",
    marginBottom: 20,
    textAlign: "center",
  },
  noteText: {
    fontSize: 50,
  },
  logContainer: {
    flex: 1,
    width: "100%",
    marginTop: 20,
    backgroundColor: "#f5f5f5",
    borderRadius: 8,
    padding: 10,
  },
  logText: {
    fontSize: 11,
    fontFamily: Platform.OS === "ios" ? "Menlo" : "monospace",
    color: "#333",
    lineHeight: 16,
  },
});
