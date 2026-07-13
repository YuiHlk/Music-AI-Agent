package com.musicai.agent.domain;

import java.util.Objects;

public record ChordNote(Pitch pitch, FretPosition fretPosition) {
    public ChordNote {
        Objects.requireNonNull(pitch, "pitch");
        Objects.requireNonNull(fretPosition, "fretPosition");
    }
}
