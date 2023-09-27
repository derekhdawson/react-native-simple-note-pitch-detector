import { NativeModulesProxy, EventEmitter, Subscription } from 'expo-modules-core';

// Import the native module. On web, it will be resolved to ReactNativeSimpleNotePitchDetector.web.ts
// and on native platforms to ReactNativeSimpleNotePitchDetector.ts
import ReactNativeSimpleNotePitchDetectorModule from './ReactNativeSimpleNotePitchDetectorModule';
import ReactNativeSimpleNotePitchDetectorView from './ReactNativeSimpleNotePitchDetectorView';
import { ChangeEventPayload, ReactNativeSimpleNotePitchDetectorViewProps } from './ReactNativeSimpleNotePitchDetector.types';

// Get the native constant value.
export const PI = ReactNativeSimpleNotePitchDetectorModule.PI;

export function hello(): string {
  return ReactNativeSimpleNotePitchDetectorModule.hello();
}

export async function setValueAsync(value: string) {
  return await ReactNativeSimpleNotePitchDetectorModule.setValueAsync(value);
}

const emitter = new EventEmitter(ReactNativeSimpleNotePitchDetectorModule ?? NativeModulesProxy.ReactNativeSimpleNotePitchDetector);

export function addChangeListener(listener: (event: ChangeEventPayload) => void): Subscription {
  return emitter.addListener<ChangeEventPayload>('onChange', listener);
}

export { ReactNativeSimpleNotePitchDetectorView, ReactNativeSimpleNotePitchDetectorViewProps, ChangeEventPayload };
