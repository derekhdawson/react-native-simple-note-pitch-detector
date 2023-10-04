import * as React from "react";

import { ReactNativeSimpleNotePitchDetectorViewProps } from "./ReactNativeSimpleNotePitchDetector.types";

export default function ExpoSettingsView(
  props: ReactNativeSimpleNotePitchDetectorViewProps
) {
  return (
    <div>
      <span>{props.name}</span>
    </div>
  );
}
