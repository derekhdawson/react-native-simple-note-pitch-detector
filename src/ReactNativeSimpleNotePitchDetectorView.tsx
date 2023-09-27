import { requireNativeViewManager } from 'expo-modules-core';
import * as React from 'react';

import { ReactNativeSimpleNotePitchDetectorViewProps } from './ReactNativeSimpleNotePitchDetector.types';

const NativeView: React.ComponentType<ReactNativeSimpleNotePitchDetectorViewProps> =
  requireNativeViewManager('ReactNativeSimpleNotePitchDetector');

export default function ReactNativeSimpleNotePitchDetectorView(props: ReactNativeSimpleNotePitchDetectorViewProps) {
  return <NativeView {...props} />;
}
