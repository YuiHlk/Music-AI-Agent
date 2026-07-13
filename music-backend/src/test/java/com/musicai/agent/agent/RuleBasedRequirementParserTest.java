package com.musicai.agent.agent;

import com.musicai.agent.domain.GuitarTuning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedRequirementParserTest {

    private final RuleBasedRequirementParser parser = new RuleBasedRequirementParser();

    @Test
    void parsesChineseVerticalSliceRequest() {
        var constraints = parser.parse("生成一段 8 小节、120 BPM、E 小调、标准调弦的摇滚吉他 Riff");

        assertThat(constraints.measures()).isEqualTo(8);
        assertThat(constraints.tempo()).isEqualTo(120);
        assertThat(constraints.keySignature()).isEqualTo("E minor");
        assertThat(constraints.style()).isEqualTo("rock");
        assertThat(constraints.tuning()).isEqualTo(GuitarTuning.STANDARD);
    }

    @Test
    void parsesDropDMetalRequest() {
        var constraints = parser.parse("Create 4 measures at 140 BPM, Drop D metal riff");

        assertThat(constraints.measures()).isEqualTo(4);
        assertThat(constraints.tempo()).isEqualTo(140);
        assertThat(constraints.style()).isEqualTo("metal");
        assertThat(constraints.tuning()).isEqualTo(GuitarTuning.DROP_D);
    }
}
