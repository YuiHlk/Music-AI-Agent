package com.musicai.agent.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuitarPlayabilityTest {

    @Test
    void rejectsTwoChordNotesOnTheSameString() {
        assertThatThrownBy(() -> new ChordEvent(List.of(
                new ChordNote(new Pitch(40), new FretPosition(6, 0)),
                new ChordNote(new Pitch(43), new FretPosition(6, 3))), RhythmicDuration.QUARTER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same guitar string");
    }

    @Test
    void rejectsPitchThatDoesNotMatchItsStringAndFret() {
        Measure measure = new Measure(1, List.of(
                new NoteEvent(new Pitch(43), RhythmicDuration.QUARTER, new FretPosition(6, 0)),
                new RestEvent(new RhythmicDuration(3, 4))));
        Score score = new Score("Invalid fingering", 120, "E minor", TimeSignature.FOUR_FOUR,
                List.of(new Track("Guitar", GuitarTuning.STANDARD, List.of(measure))));

        assertThatThrownBy(score::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsUnplayableChordSpan() {
        ChordEvent chord = new ChordEvent(List.of(
                new ChordNote(new Pitch(41), new FretPosition(6, 1)),
                new ChordNote(new Pitch(52), new FretPosition(4, 2)),
                new ChordNote(new Pitch(64), new FretPosition(2, 5)),
                new ChordNote(new Pitch(74), new FretPosition(1, 10))), new RhythmicDuration(1, 1));
        Score score = new Score("Wide chord", 120, "E minor", TimeSignature.FOUR_FOUR,
                List.of(new Track("Guitar", GuitarTuning.STANDARD, List.of(new Measure(1, List.of(chord))))));

        assertThatThrownBy(score::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fret span");
    }
}
