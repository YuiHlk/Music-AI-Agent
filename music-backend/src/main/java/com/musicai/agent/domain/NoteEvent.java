package com.musicai.agent.domain;

import java.util.Objects;

public record NoteEvent(Pitch pitch, RhythmicDuration duration, FretPosition fretPosition) implements MusicalEvent {
    public NoteEvent {
        Objects.requireNonNull(pitch, "pitch");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(fretPosition, "fretPosition");
        if (duration.equals(RhythmicDuration.ZERO)) {
            throw new IllegalArgumentException("A note must have a positive duration");
        }
    }
}
