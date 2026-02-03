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

/**
 * Set the buffer size for pitch detection.
 * Must be called before start() or will restart the engine if already running.
 *
 * Buffer size affects the trade-off between frequency range and responsiveness:
 * - Smaller buffer (2048-4096): Better for high frequencies, faster response, but less accurate for low notes
 * - Larger buffer (8192-16384): Better for low frequencies, more accurate, but slightly more latency
 *
 * iOS defaults to 8192, Android defaults to 2048.
 *
 * Recommended values:
 * - 4096: Good balance for most use cases
 * - 8192: Better for bass instruments or full piano range (iOS default)
 * - 2048: Better for high-pitched instruments, faster response (Android default)
 *
 * @param size - Buffer size (must be power of 2: 1024, 2048, 4096, 8192, 16384)
 */
export function setBufferSize(size: number) {
  return ReactNativeSimpleNotePitchDetectorModule.setBufferSize(size);
}

/**
 * Get the current buffer size.
 * @returns Current buffer size
 */
export function getBufferSize(): number {
  return ReactNativeSimpleNotePitchDetectorModule.getBufferSize();
}

/**
 * Set the pitch estimation algorithm.
 * Must be called before start() or will restart the engine if already running.
 *
 * Available algorithms differ by platform:
 *
 * **iOS (Beethoven library):**
 * - "yin" (default) - YIN algorithm, good for monophonic instruments
 * - "hps" - Harmonic Product Spectrum
 * - "barycentric" - Barycentric interpolation
 * - "quadradic" - Quadratic interpolation
 * - "jains" - Jain's method
 * - "quinnsFirst" - Quinn's first estimator
 * - "quinnsSecond" - Quinn's second estimator
 * - "maxValue" - Maximum value method
 *
 * **Android (TarsosDSP library):**
 * - "fft_yin" (default) - FFT-based YIN, faster than pure YIN
 * - "yin" - Pure YIN algorithm
 * - "mpm" - McLeod Pitch Method, good for speech and music
 * - "fft_pitch" - FFT bin with most energy
 * - "dynamic_wavelet" - Dynamic wavelet algorithm
 * - "amdf" - Average Magnitude Difference Function
 *
 * @param algorithm - Algorithm name (case-insensitive)
 */
export function setAlgorithm(algorithm: string) {
  return ReactNativeSimpleNotePitchDetectorModule.setAlgorithm(algorithm);
}

/**
 * Get the current algorithm name.
 * @returns Current algorithm name
 */
export function getAlgorithm(): string {
  return ReactNativeSimpleNotePitchDetectorModule.getAlgorithm();
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
