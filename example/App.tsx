import * as React from "react";
import { useState, useRef } from "react";
import { StyleSheet, Text, View } from "react-native";

import * as ReactNativeSimpleNotePitchDetector from "react-native-simple-note-pitch-detector";

export default function App() {

  const [note, setNote] = useState("");
  const startTime = useRef(Date.now());

  React.useEffect(() => {
    startTime.current = Date.now();
    ReactNativeSimpleNotePitchDetector.start();
    ReactNativeSimpleNotePitchDetector.onChangeNote((event) => {
      const elapsed = ((Date.now() - startTime.current) / 1000).toFixed(2);
      console.log(
        `[${elapsed}s] note=${event.note}${event.octave} ` +
        `freq=${event.frequency.toFixed(1)}Hz ` +
        `off=${event.offset.toFixed(1)}%`
      );
      setNote(event.note);
    })
  }, []);

  return (
    <View style={styles.container}>
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
  },
  noteText: {
    fontSize: 50
  }
});
