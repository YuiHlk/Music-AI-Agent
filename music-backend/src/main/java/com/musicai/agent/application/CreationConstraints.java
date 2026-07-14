package com.musicai.agent.application;

import com.musicai.agent.domain.GuitarTuning;
import com.musicai.agent.domain.TimeSignature;

public record CreationConstraints(
        int measures,
        int tempo,
        String keySignature,
        String style,
        GuitarTuning tuning,
        TimeSignature timeSignature,
        MusicalMood mood,
        RhythmicFeel rhythmicFeel,
        int complexity,
        long variationSeed) {

    public CreationConstraints {
        if (measures < 1 || measures > 128) {
            throw new IllegalArgumentException("Measure count must be between 1 and 128");
        }
        if (tempo < 20 || tempo > 300) {
            throw new IllegalArgumentException("Tempo must be between 20 and 300 BPM");
        }
        if (keySignature == null || keySignature.isBlank() || style == null || style.isBlank()) {
            throw new IllegalArgumentException("Key signature and style are required");
        }
        if (tuning == null || timeSignature == null || mood == null || rhythmicFeel == null) {
            throw new IllegalArgumentException("Tuning, time signature, mood and rhythmic feel are required");
        }
        if (complexity < 1 || complexity > 5) {
            throw new IllegalArgumentException("Complexity must be between 1 and 5");
        }
        style = style.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public CreationConstraints(int measures, int tempo, String keySignature, String style,
                               GuitarTuning tuning, TimeSignature timeSignature) {
        this(measures, tempo, keySignature, style, tuning, timeSignature,
                MusicalMood.ENERGETIC, defaultFeel(style), 3, 0L);
    }

    public static CreationConstraints defaultRockRiff() {
        return new CreationConstraints(8, 120, "E minor", "rock", GuitarTuning.STANDARD,
                TimeSignature.FOUR_FOUR, MusicalMood.ENERGETIC, RhythmicFeel.STRAIGHT, 3, 0L);
    }

    private static RhythmicFeel defaultFeel(String style) {
        return style != null && style.toLowerCase(java.util.Locale.ROOT).contains("metal")
                ? RhythmicFeel.DRIVING : RhythmicFeel.STRAIGHT;
    }
}
