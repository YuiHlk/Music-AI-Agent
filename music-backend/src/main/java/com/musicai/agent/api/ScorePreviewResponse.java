package com.musicai.agent.api;

import com.musicai.agent.domain.ChordEvent;
import com.musicai.agent.domain.Measure;
import com.musicai.agent.domain.NoteEvent;
import com.musicai.agent.domain.RestEvent;
import com.musicai.agent.domain.Score;

import java.util.List;

public record ScorePreviewResponse(String title, int tempo, String keySignature, String timeSignature,
                                   List<TrackPreview> tracks) {

    public static ScorePreviewResponse from(Score score) {
        return new ScorePreviewResponse(score.title(), score.tempo(), score.keySignature(),
                score.timeSignature().beats() + "/" + score.timeSignature().beatUnit(),
                score.tracks().stream().map(track -> new TrackPreview(track.name(), track.tuning().name(),
                        track.measures().stream().map(ScorePreviewResponse::toMeasure).toList())).toList());
    }

    private static MeasurePreview toMeasure(Measure measure) {
        return new MeasurePreview(measure.number(), measure.events().stream().map(event -> {
            if (event instanceof NoteEvent note) {
                return new EventPreview("NOTE", note.duration().numerator(), note.duration().denominator(),
                        List.of(new TabNote(note.pitch().midiNumber(), note.fretPosition().stringNumber(),
                                note.fretPosition().fret())));
            }
            if (event instanceof ChordEvent chord) {
                return new EventPreview("CHORD", chord.duration().numerator(), chord.duration().denominator(),
                        chord.notes().stream().map(note -> new TabNote(note.pitch().midiNumber(),
                                note.fretPosition().stringNumber(), note.fretPosition().fret())).toList());
            }
            RestEvent rest = (RestEvent) event;
            return new EventPreview("REST", rest.duration().numerator(), rest.duration().denominator(), List.of());
        }).toList());
    }

    public record TrackPreview(String name, String tuning, List<MeasurePreview> measures) {
    }

    public record MeasurePreview(int number, List<EventPreview> events) {
    }

    public record EventPreview(String type, long durationNumerator, long durationDenominator,
                               List<TabNote> notes) {
    }

    public record TabNote(int midiNumber, int stringNumber, int fret) {
    }
}
