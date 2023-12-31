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
