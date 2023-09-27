import { StyleSheet, Text, View } from 'react-native';

import * as ReactNativeSimpleNotePitchDetector from 'react-native-simple-note-pitch-detector';

export default function App() {
  return (
    <View style={styles.container}>
      <Text>{ReactNativeSimpleNotePitchDetector.hello()}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
