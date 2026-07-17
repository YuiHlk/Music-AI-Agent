package com.musicai.agent.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmPropertiesTest {

    @Test
    void normalizesProviderAndConnectionValues() {
        LlmProperties properties = new LlmProperties(
                " OPENAI_COMPATIBLE ", " https://models.example/v1 ", " custom-model ", " secret ");

        assertThat(properties.provider()).isEqualTo("openai-compatible");
        assertThat(properties.baseUrl()).isEqualTo("https://models.example/v1");
        assertThat(properties.modelName()).isEqualTo("custom-model");
        assertThat(properties.apiKey()).isEqualTo("secret");
    }

    @Test
    void rejectsMissingApiKeyWhenModelProfileIsActive() {
        LlmProperties properties = new LlmProperties(
                "deepseek", "https://api.deepseek.com", "deepseek-chat", " ");

        assertThatThrownBy(properties::requireValidConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_API_KEY");
    }
}
