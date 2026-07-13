package com.musicai.agent.domain;

import java.util.List;

public record Track(String name, GuitarTuning tuning, List<Measure> measures) {
    public Track {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Track name must not be blank");
        }
        measures = List.copyOf(measures);
        if (measures.isEmpty()) {
            throw new IllegalArgumentException("Track must contain measures");
        }
    }

    public void validatePlayability(int maximumFretSpan) {
        for (Measure measure : measures) {
            for (MusicalEvent event : measure.events()) {
                if (event instanceof NoteEvent note) {
                    validatePosition(note.pitch(), note.fretPosition());
                } else if (event instanceof ChordEvent chord) {
                    if (chord.fretSpan() > maximumFretSpan) {
                        throw new IllegalStateException("Chord fret span exceeds " + maximumFretSpan
                                + " in measure " + measure.number());
                    }
                    chord.notes().forEach(note -> validatePosition(note.pitch(), note.fretPosition()));
                }
            }
        }
    }

    private void validatePosition(Pitch pitch, FretPosition position) {
        int openPitch = tuning.openStringsHighToLow().get(position.stringNumber() - 1).midiNumber();
        if (openPitch + position.fret() != pitch.midiNumber()) {
            throw new IllegalStateException("Pitch " + pitch.midiNumber() + " does not match string "
                    + position.stringNumber() + " fret " + position.fret());
        }
    }
}
