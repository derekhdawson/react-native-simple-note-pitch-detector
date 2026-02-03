import { ChangeEventPayload } from "./ReactNativeSimpleNotePitchDetector.types";

export default {
  start: () => {},
  stop: () => {},
  isRecording: () => false,
  onChangePitch: (_: ChangeEventPayload) => {},
  setLevelThreshold: (_: number) => {},
  setBufferSize: (_: number) => {},
  getBufferSize: () => 8192,
  setAlgorithm: (_: string) => {},
  getAlgorithm: () => "yin",
};
