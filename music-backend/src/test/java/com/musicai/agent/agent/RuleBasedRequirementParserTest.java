package com.musicai.agent.agent;

import com.musicai.agent.application.MusicalMood;
import com.musicai.agent.application.RhythmicFeel;
import com.musicai.agent.domain.GuitarTuning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedRequirementParserTest {

    private final RuleBasedRequirementParser parser = new RuleBasedRequirementParser();

    @Test
    void parsesChineseMusicalIntent() {
        var constraints = parser.parse("生成一段 8 小节、120 BPM、E 小调、标准调弦的忧郁切分摇滚 Riff，难度 4");

        assertThat(constraints.measures()).isEqualTo(8);
        assertThat(constraints.tempo()).isEqualTo(120);
        assertThat(constraints.keySignature()).isEqualTo("E minor");
        assertThat(constraints.style()).isEqualTo("rock");
        assertThat(constraints.tuning()).isEqualTo(GuitarTuning.STANDARD);
        assertThat(constraints.mood()).isEqualTo(MusicalMood.MELANCHOLIC);
        assertThat(constraints.rhythmicFeel()).isEqualTo(RhythmicFeel.SYNCOPATED);
        assertThat(constraints.complexity()).isEqualTo(4);
    }

    @Test
    void parsesDropDMetalRequest() {
        var constraints = parser.parse("Create 4 measures at 140 BPM, Drop D aggressive metal riff in 3/4");

        assertThat(constraints.measures()).isEqualTo(4);
        assertThat(constraints.tempo()).isEqualTo(140);
        assertThat(constraints.style()).isEqualTo("metal");
        assertThat(constraints.tuning()).isEqualTo(GuitarTuning.DROP_D);
        assertThat(constraints.timeSignature().beats()).isEqualTo(3);
        assertThat(constraints.mood()).isEqualTo(MusicalMood.AGGRESSIVE);
        assertThat(constraints.rhythmicFeel()).isEqualTo(RhythmicFeel.DRIVING);
    }

    @Test
    void derivesStableButPromptSpecificVariationSeed() {
        long first = parser.parse("dark E minor rock riff").variationSeed();
        long repeated = parser.parse("dark E minor rock riff").variationSeed();
        long different = parser.parse("bright E minor rock riff").variationSeed();

        assertThat(repeated).isEqualTo(first);
        assertThat(different).isNotEqualTo(first);
    }
}
