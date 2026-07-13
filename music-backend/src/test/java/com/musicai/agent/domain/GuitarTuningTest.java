package com.musicai.agent.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuitarTuningTest {

    @Test
    void mapsLowEInStandardTuning() {
        assertThat(GuitarTuning.STANDARD.requirePlayablePosition(new Pitch(40), 22))
                .isEqualTo(new FretPosition(6, 0));
    }

    @Test
    void mapsLowDOnlyInDropDTuning() {
        assertThat(GuitarTuning.DROP_D.requirePlayablePosition(new Pitch(38), 22))
                .isEqualTo(new FretPosition(6, 0));
        assertThatThrownBy(() -> GuitarTuning.STANDARD.requirePlayablePosition(new Pitch(38), 22))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
