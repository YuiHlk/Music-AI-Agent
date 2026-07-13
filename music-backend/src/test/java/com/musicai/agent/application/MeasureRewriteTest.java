package com.musicai.agent.application;

import com.musicai.agent.domain.GuitarTuning;
import com.musicai.agent.domain.TimeSignature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeasureRewriteTest {

    @Test
    void rewriteDoesNotChangeMeasuresOutsideTargetRange() {
        GuitarRiffGenerator generator = new GuitarRiffGenerator();
        var original = generator.generate(CreationConstraints.defaultRockRiff());
        var replacement = generator.generate(new CreationConstraints(8, 140, "E minor", "metal",
                GuitarTuning.STANDARD, TimeSignature.FOUR_FOUR));

        var rewritten = MusicProjectService.replaceMeasures(original, replacement, 3, 4);

        assertThat(rewritten.tracks().getFirst().measures().get(0))
                .isEqualTo(original.tracks().getFirst().measures().get(0));
        assertThat(rewritten.tracks().getFirst().measures().get(2))
                .isEqualTo(replacement.tracks().getFirst().measures().get(2));
        assertThat(rewritten.tracks().getFirst().measures().get(4))
                .isEqualTo(original.tracks().getFirst().measures().get(4));
    }
}
