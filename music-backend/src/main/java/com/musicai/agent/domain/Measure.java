package com.musicai.agent.domain;

import java.util.List;

public record Measure(int number, List<MusicalEvent> events) {
    public Measure {
        if (number <= 0) {
            throw new IllegalArgumentException("Measure number must be positive");
        }
        events = List.copyOf(events);
        if (events.isEmpty()) {
            throw new IllegalArgumentException("A measure must contain events");
        }
    }

    public RhythmicDuration totalDuration() {
        return events.stream().map(MusicalEvent::duration).reduce(RhythmicDuration.ZERO, RhythmicDuration::add);
    }

    public void validateDuration(TimeSignature timeSignature) {
        if (!totalDuration().equals(timeSignature.measureDuration())) {
            throw new IllegalStateException("Measure " + number + " has duration " + totalDuration()
                    + ", expected " + timeSignature.measureDuration());
        }
    }
}
