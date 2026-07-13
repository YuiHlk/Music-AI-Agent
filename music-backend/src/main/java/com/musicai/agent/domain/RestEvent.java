package com.musicai.agent.domain;

import java.util.Objects;

public record RestEvent(RhythmicDuration duration) implements MusicalEvent {
    public RestEvent {
        Objects.requireNonNull(duration, "duration");
        if (duration.equals(RhythmicDuration.ZERO)) {
            throw new IllegalArgumentException("A rest must have a positive duration");
        }
    }
}
