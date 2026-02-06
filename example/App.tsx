import * as React from "react";
import { useState, useRef } from "react";
import { StyleSheet, Text, View, Platform, PermissionsAndroid } from "react-native";

import * as ReactNativeSimpleNotePitchDetector from "react-native-simple-note-pitch-detector";

export default function App() {

  const [note, setNote] = useState("");
  const [status, setStatus] = useState("initializing...");
  const startTime = useRef(Date.now());

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
      // Very low threshold to catch everything
      ReactNativeSimpleNotePitchDetector.setLevelThreshold(-100);
      // Larger buffer for low frequency detection
      ReactNativeSimpleNotePitchDetector.setBufferSize(4096);
      // Try MPM algorithm (often better for musical instruments)
      ReactNativeSimpleNotePitchDetector.setAlgorithm("mpm");
      ReactNativeSimpleNotePitchDetector.start();

      noteSubscription = ReactNativeSimpleNotePitchDetector.onChangeNote((event) => {
        const elapsed = ((Date.now() - startTime.current) / 1000).toFixed(2);
        console.log(
          `[${elapsed}s] note=${event.note}${event.octave} ` +
          `freq=${event.frequency.toFixed(1)}Hz ` +
          `off=${event.offset.toFixed(1)}%`
        );
        setNote(event.note);
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
        note === "" ? <Text>waiting for note...</Text> : <Text style={styles.noteText}>{note}</Text>
      }
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
});
