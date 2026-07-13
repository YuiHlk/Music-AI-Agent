package com.musicai.agent.application;

import org.junit.jupiter.api.Test;

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
}
