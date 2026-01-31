import {
  NativeModulesProxy,
  EventEmitter,
  Subscription,
} from "expo-modules-core";

import ReactNativeSimpleNotePitchDetectorModule from "./ReactNativeSimpleNotePitchDetectorModule";
import {
  ChangeEventPayload,
  ReactNativeSimpleNotePitchDetectorViewProps,
} from "./ReactNativeSimpleNotePitchDetector.types";

export function start() {
  return ReactNativeSimpleNotePitchDetectorModule.start();
}

export function stop() {
  return ReactNativeSimpleNotePitchDetectorModule.stop();
}

export function isRecording() {
  return ReactNativeSimpleNotePitchDetectorModule.isRecording();
}

/**
 * Set the minimum audio level threshold for pitch detection.
 * Values are in dB (e.g., -30 means sounds quieter than -30dB are ignored).
 * Lower values = more sensitive (picks up quieter sounds).
 * Default is -30.
 * @param threshold - Level threshold in dB (e.g., -30, -35, -40)
 */
export function setLevelThreshold(threshold: number) {
  return ReactNativeSimpleNotePitchDetectorModule.setLevelThreshold(threshold);
}

const emitter = new EventEmitter(
  ReactNativeSimpleNotePitchDetectorModule ??
    NativeModulesProxy.ReactNativeSimpleNotePitchDetector
);

export function onChangeNote(
  listener: (event: ChangeEventPayload) => void
): Subscription {
  return emitter.addListener<ChangeEventPayload>("onChangeNote", listener);
}

export { ReactNativeSimpleNotePitchDetectorViewProps, ChangeEventPayload };
