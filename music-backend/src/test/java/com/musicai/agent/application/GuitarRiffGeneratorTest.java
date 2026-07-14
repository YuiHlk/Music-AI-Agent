package com.musicai.agent.application;

import com.musicai.agent.agent.RuleBasedRequirementParser;
import com.musicai.agent.domain.GuitarTuning;
import com.musicai.agent.domain.NoteEvent;
import com.musicai.agent.domain.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class GuitarRiffGeneratorTest {

    @Test
    void generatesPlayableEightMeasureRockRiff() {
        var score = new GuitarRiffGenerator().generate(CreationConstraints.defaultRockRiff());

        assertThat(score.tempo()).isEqualTo(120);
        assertThat(score.keySignature()).isEqualTo("E minor");
        assertThat(score.tracks()).hasSize(1);
        assertThat(score.tracks().getFirst().measures()).hasSize(8);
        assertThatCode(score::validate).doesNotThrowAnyException();
    }

    @Test
    void differentPromptsProduceDifferentMusicalEvents() {
        RuleBasedRequirementParser parser = new RuleBasedRequirementParser();
        GuitarRiffGenerator generator = new GuitarRiffGenerator();

        var darkMetal = generator.generate(parser.parse(
                "Generate 8 measures of a dark aggressive E minor driving metal riff, complexity 5"));
        var brightRock = generator.generate(parser.parse(
                "Generate 8 measures of a bright E minor syncopated rock riff, complexity 2"));

        assertThat(eventSignature(darkMetal)).isNotEqualTo(eventSignature(brightRock));
    }

    @Test
    void sameConstraintsRemainReproducible() {
        RuleBasedRequirementParser parser = new RuleBasedRequirementParser();
        GuitarRiffGenerator generator = new GuitarRiffGenerator();
        var constraints = parser.parse("melancholic A minor blues riff with syncopation");

        assertThat(generator.generate(constraints)).isEqualTo(generator.generate(constraints));
    }

    @Test
    void keySignatureChangesGeneratedPitchMaterial() {
        GuitarRiffGenerator generator = new GuitarRiffGenerator();
        var eMinor = generator.generate(new CreationConstraints(2, 120, "E minor", "rock",
                GuitarTuning.STANDARD, TimeSignature.FOUR_FOUR,
                MusicalMood.ENERGETIC, RhythmicFeel.STRAIGHT, 3, 42));
        var aMinor = generator.generate(new CreationConstraints(2, 120, "A minor", "rock",
                GuitarTuning.STANDARD, TimeSignature.FOUR_FOUR,
                MusicalMood.ENERGETIC, RhythmicFeel.STRAIGHT, 3, 42));

        assertThat(pitches(eMinor)).isNotEqualTo(pitches(aMinor));
        assertThat(pitches(eMinor)).allMatch(pitch -> List.of(4, 7, 9, 11, 2).contains(pitch % 12));
        assertThat(pitches(aMinor)).allMatch(pitch -> List.of(9, 0, 2, 4, 7).contains(pitch % 12));
    }

    @Test
    void supportsThreeFourWithoutBreakingMeasureDuration() {
        var constraints = new CreationConstraints(4, 100, "D minor", "blues", GuitarTuning.DROP_D,
                new TimeSignature(3, 4), MusicalMood.DARK, RhythmicFeel.SYNCOPATED, 4, 99);
        var score = new GuitarRiffGenerator().generate(constraints);

        assertThat(score.tracks().getFirst().measures()).allSatisfy(measure ->
                assertThat(measure.totalDuration()).isEqualTo(new TimeSignature(3, 4).measureDuration()));
        assertThatCode(score::validate).doesNotThrowAnyException();
    }

    private static List<Integer> pitches(com.musicai.agent.domain.Score score) {
        return score.tracks().getFirst().measures().stream()
                .flatMap(measure -> measure.events().stream())
                .filter(NoteEvent.class::isInstance)
                .map(NoteEvent.class::cast)
                .map(note -> note.pitch().midiNumber())
                .toList();
    }

    private static List<String> eventSignature(com.musicai.agent.domain.Score score) {
        return score.tracks().getFirst().measures().stream()
                .flatMap(measure -> measure.events().stream())
                .map(event -> event instanceof NoteEvent note
                        ? "N" + note.pitch().midiNumber() + ":" + note.duration()
                        : "R:" + event.duration())
                .toList();
    }
}
