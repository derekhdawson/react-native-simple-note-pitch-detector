import {
  NativeModulesProxy,
  EventEmitter,
  Subscription,
} from "expo-modules-core";

import ReactNativeSimpleNotePitchDetectorModule from "./ReactNativeSimpleNotePitchDetectorModule";
import ReactNativeSimpleNotePitchDetectorView from "./ReactNativeSimpleNotePitchDetectorView";
import {
  ChangeEventPayload,
  ReactNativeSimpleNotePitchDetectorViewProps,
} from "./ReactNativeSimpleNotePitchDetector.types";

export function start() {
  return ReactNativeSimpleNotePitchDetectorModule.start();
}

const emitter = new EventEmitter(
  ReactNativeSimpleNotePitchDetectorModule ??
    NativeModulesProxy.ReactNativeSimpleNotePitchDetector
);

export function onChangePitch(
  listener: (event: ChangeEventPayload) => void
): Subscription {
  return emitter.addListener<ChangeEventPayload>("onChangePitch", listener);
}

export {
  ReactNativeSimpleNotePitchDetectorView,
  ReactNativeSimpleNotePitchDetectorViewProps,
  ChangeEventPayload,
};
