package com.musicai.agent.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record ChordEvent(List<ChordNote> notes, RhythmicDuration duration) implements MusicalEvent {
    public ChordEvent {
        notes = List.copyOf(notes);
        Objects.requireNonNull(duration, "duration");
        if (notes.size() < 2 || notes.size() > 6) {
            throw new IllegalArgumentException("A guitar chord must contain between two and six notes");
        }
        if (duration.equals(RhythmicDuration.ZERO)) {
            throw new IllegalArgumentException("A chord must have a positive duration");
        }
        HashSet<Integer> strings = new HashSet<>();
        for (ChordNote note : notes) {
            if (!strings.add(note.fretPosition().stringNumber())) {
                throw new IllegalArgumentException("A chord cannot use the same guitar string twice");
            }
        }
    }

    public int fretSpan() {
        int minimum = notes.stream().mapToInt(note -> note.fretPosition().fret()).filter(fret -> fret > 0)
                .min().orElse(0);
        int maximum = notes.stream().mapToInt(note -> note.fretPosition().fret()).max().orElse(0);
        return minimum == 0 ? maximum : maximum - minimum;
    }
}
