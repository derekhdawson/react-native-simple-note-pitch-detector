export type ChangeEventPayload = {
  /** Note name without octave (e.g., "C#", "D", "Eb") */
  note: string;
  /** Octave number (e.g., 4 for middle C) */
  octave: number;
  /** Raw frequency in Hz */
  frequency: number;
  /** Offset from perfect pitch as percentage (-50 to +50, negative = flat, positive = sharp) */
  offset: number;
};
