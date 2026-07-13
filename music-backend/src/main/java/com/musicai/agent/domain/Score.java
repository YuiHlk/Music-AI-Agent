package com.musicai.agent.domain;

import java.util.List;

public record Score(String title, int tempo, String keySignature, TimeSignature timeSignature, List<Track> tracks) {
    public Score {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        if (tempo < 20 || tempo > 300) {
            throw new IllegalArgumentException("Tempo must be between 20 and 300 BPM");
        }
        if (keySignature == null || keySignature.isBlank()) {
            throw new IllegalArgumentException("Key signature must not be blank");
        }
        tracks = List.copyOf(tracks);
        if (tracks.isEmpty()) {
            throw new IllegalArgumentException("Score must contain tracks");
        }
    }

    public void validate() {
        int expectedMeasures = tracks.getFirst().measures().size();
        for (Track track : tracks) {
            if (track.measures().size() != expectedMeasures) {
                throw new IllegalStateException("All tracks must contain the same number of measures");
            }
            track.measures().forEach(measure -> measure.validateDuration(timeSignature));
            track.validatePlayability(5);
        }
    }
}
