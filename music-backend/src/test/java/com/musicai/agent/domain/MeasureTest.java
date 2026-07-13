package com.musicai.agent.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeasureTest {

    @Test
    void acceptsCompleteFourFourMeasure() {
        Measure measure = new Measure(1, List.of(
                new RestEvent(RhythmicDuration.QUARTER),
                new RestEvent(RhythmicDuration.QUARTER),
                new RestEvent(RhythmicDuration.QUARTER),
                new RestEvent(RhythmicDuration.QUARTER)));

        assertThatCode(() -> measure.validateDuration(TimeSignature.FOUR_FOUR)).doesNotThrowAnyException();
    }

    @Test
    void validatesDifferentTimeSignatures() {
        Measure measure = new Measure(1, List.of(new RestEvent(new RhythmicDuration(3, 4))));

        assertThatCode(() -> measure.validateDuration(new TimeSignature(3, 4))).doesNotThrowAnyException();
        assertThatThrownBy(() -> measure.validateDuration(TimeSignature.FOUR_FOUR))
                .isInstanceOf(IllegalStateException.class);
    }
}
