package com.musicai.agent.domain;

import java.util.Locale;
import java.util.Map;

public record KeySignature(String tonic, int rootPitchClass, Mode mode, int fifths) {

    private static final Map<String, Integer> PITCH_CLASSES = Map.ofEntries(
            Map.entry("C", 0), Map.entry("C#", 1), Map.entry("DB", 1),
            Map.entry("D", 2), Map.entry("D#", 3), Map.entry("EB", 3),
            Map.entry("E", 4), Map.entry("FB", 4), Map.entry("E#", 5),
            Map.entry("F", 5), Map.entry("F#", 6), Map.entry("GB", 6),
            Map.entry("G", 7), Map.entry("G#", 8), Map.entry("AB", 8),
            Map.entry("A", 9), Map.entry("A#", 10), Map.entry("BB", 10),
            Map.entry("B", 11), Map.entry("CB", 11));
    private static final Map<String, Integer> MAJOR_FIFTHS = Map.ofEntries(
            Map.entry("C", 0), Map.entry("G", 1), Map.entry("D", 2), Map.entry("A", 3),
            Map.entry("E", 4), Map.entry("B", 5), Map.entry("F#", 6), Map.entry("C#", 7),
            Map.entry("F", -1), Map.entry("BB", -2), Map.entry("EB", -3), Map.entry("AB", -4),
            Map.entry("DB", -5), Map.entry("GB", -6), Map.entry("CB", -7));
    private static final Map<String, Integer> MINOR_FIFTHS = Map.ofEntries(
            Map.entry("A", 0), Map.entry("E", 1), Map.entry("B", 2), Map.entry("F#", 3),
            Map.entry("C#", 4), Map.entry("G#", 5), Map.entry("D#", 6), Map.entry("A#", 7),
            Map.entry("D", -1), Map.entry("G", -2), Map.entry("C", -3), Map.entry("F", -4),
            Map.entry("BB", -5), Map.entry("EB", -6), Map.entry("AB", -7));

    public KeySignature {
        if (!PITCH_CLASSES.containsKey(tonic)) {
            throw new IllegalArgumentException("Unsupported key tonic: " + tonic);
        }
    }

    public static KeySignature parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Key signature must not be blank");
        }
        String normalized = value.strip().replace("♯", "#").replace("♭", "b")
                .toUpperCase(Locale.ROOT);
        String tonic = normalized.split("\\s+")[0];
        Mode mode = normalized.contains("MAJOR") || normalized.contains("大调") ? Mode.MAJOR : Mode.MINOR;
        Integer pitchClass = PITCH_CLASSES.get(tonic);
        if (pitchClass == null) {
            throw new IllegalArgumentException("Unsupported key signature: " + value);
        }
        int fifths = (mode == Mode.MAJOR ? MAJOR_FIFTHS : MINOR_FIFTHS).getOrDefault(tonic, 0);
        return new KeySignature(tonic, pitchClass, mode, fifths);
    }

    public enum Mode {
        MAJOR,
        MINOR
    }
}
