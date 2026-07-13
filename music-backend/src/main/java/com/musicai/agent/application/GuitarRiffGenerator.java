package com.musicai.agent.application;

import com.musicai.agent.domain.Measure;
import com.musicai.agent.domain.NoteEvent;
import com.musicai.agent.domain.MusicalEvent;
import com.musicai.agent.domain.Pitch;
import com.musicai.agent.domain.RhythmicDuration;
import com.musicai.agent.domain.Score;
import com.musicai.agent.domain.Track;

import java.util.ArrayList;
import java.util.List;

public final class GuitarRiffGenerator {

    private static final int[][] ROCK_PATTERNS = {
            {40, 40, 43, 45, 40, 47, 45, 43},
            {40, 40, 43, 45, 47, 45, 43, 40}
    };

    public Score generate(CreationConstraints constraints) {
        List<Measure> measures = new ArrayList<>();
        for (int measureNumber = 1; measureNumber <= constraints.measures(); measureNumber++) {
            int[] pattern = ROCK_PATTERNS[(measureNumber - 1) % ROCK_PATTERNS.length];
            List<MusicalEvent> notes = new ArrayList<>();
            for (int midiPitch : pattern) {
                Pitch pitch = new Pitch(midiPitch);
                notes.add(new NoteEvent(pitch, RhythmicDuration.EIGHTH,
                        constraints.tuning().requirePlayablePosition(pitch, 22)));
            }
            measures.add(new Measure(measureNumber, notes));
        }
        Score score = new Score("Generated " + constraints.style() + " guitar riff", constraints.tempo(),
                constraints.keySignature(), constraints.timeSignature(),
                List.of(new Track("Electric Guitar", constraints.tuning(), measures)));
        score.validate();
        return score;
    }
}
