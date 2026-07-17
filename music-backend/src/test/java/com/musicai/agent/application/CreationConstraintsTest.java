package com.musicai.agent.application;

import com.musicai.agent.domain.GuitarTuning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreationConstraintsTest {

    @Test
    void createsValidatedConstraintsFromProtocolFriendlyNames() {
        CreationConstraints constraints = CreationConstraints.fromStructured(8, 120, "E minor", "Metal",
                "drop-d", 4, 4, "aggressive", "half-time", 4, 42L);

        assertThat(constraints.style()).isEqualTo("metal");
        assertThat(constraints.tuning()).isEqualTo(GuitarTuning.DROP_D);
        assertThat(constraints.mood()).isEqualTo(MusicalMood.AGGRESSIVE);
        assertThat(constraints.rhythmicFeel()).isEqualTo(RhythmicFeel.HALF_TIME);
        assertThat(constraints.variationSeed()).isEqualTo(42L);
    }

    @Test
    void rejectsUnsupportedStructuredEnumValues() {
        assertThatThrownBy(() -> CreationConstraints.fromStructured(8, 120, "E minor", "rock",
                "SEVEN_STRING", 4, 4, "ENERGETIC", "STRAIGHT", 3, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported tuning");
    }
}
