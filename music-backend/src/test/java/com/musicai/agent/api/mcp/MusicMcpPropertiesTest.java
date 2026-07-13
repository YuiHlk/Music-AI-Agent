package com.musicai.agent.api.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MusicMcpPropertiesTest {

    @Test
    void requiresAStrongTokenWhenMcpIsEnabled() {
        assertThatThrownBy(() -> new MusicMcpProperties(true, "").requireValidConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MUSIC_AI_MCP_TOKEN is empty");
        assertThatThrownBy(() -> new MusicMcpProperties(true, "too-short").requireValidConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 16 characters");
    }

    @Test
    void permitsDisabledMcpWithoutAToken() {
        assertThatCode(() -> new MusicMcpProperties(false, "").requireValidConfiguration())
                .doesNotThrowAnyException();
    }
}
