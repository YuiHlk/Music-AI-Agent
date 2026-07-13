package com.musicai.agent.application;

import com.musicai.agent.domain.GuitarTuning;
import com.musicai.agent.domain.TimeSignature;

public record CreationConstraints(
        int measures,
        int tempo,
        String keySignature,
        String style,
        GuitarTuning tuning,
        TimeSignature timeSignature) {

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
    }

    public static CreationConstraints defaultRockRiff() {
        return new CreationConstraints(8, 120, "E minor", "rock", GuitarTuning.STANDARD,
                TimeSignature.FOUR_FOUR);
    }
}
