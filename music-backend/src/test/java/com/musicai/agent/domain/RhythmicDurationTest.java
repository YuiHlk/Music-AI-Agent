package com.musicai.agent.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RhythmicDurationTest {

    @Test
    void addsAndNormalizesFractionsExactly() {
        assertThat(new RhythmicDuration(1, 8).add(new RhythmicDuration(3, 8)))
                .isEqualTo(new RhythmicDuration(1, 2));
    }

    @Test
    void convertsDurationToMidiTicks() {
        assertThat(RhythmicDuration.EIGHTH.toTicks(480)).isEqualTo(240);
        assertThat(new RhythmicDuration(1, 3).toTicks(480)).isEqualTo(640);
    }

    @Test
    void rejectsDurationThatCannotBeRepresentedAtPpq() {
        assertThatThrownBy(() -> new RhythmicDuration(1, 7).toTicks(480))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
