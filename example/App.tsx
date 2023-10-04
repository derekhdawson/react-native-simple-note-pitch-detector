import * as React from "react";
import { StyleSheet, Text, View } from "react-native";

import * as ReactNativeSimpleNotePitchDetector from "react-native-simple-note-pitch-detector";

export default function App() {
  React.useEffect(() => {
    ReactNativeSimpleNotePitchDetector.start();
  }, []);

  return (
    <View style={styles.container}>
      <Text>hello</Text>
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
});
