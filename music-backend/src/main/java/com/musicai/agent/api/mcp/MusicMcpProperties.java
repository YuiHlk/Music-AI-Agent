package com.musicai.agent.api.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("music-ai.mcp")
public record MusicMcpProperties(boolean enabled, String token) {

    public MusicMcpProperties {
        token = token == null ? "" : token;
    }

    void requireValidConfiguration() {
        if (enabled && token.isBlank()) {
            throw new IllegalStateException("MCP is enabled but MUSIC_AI_MCP_TOKEN is empty");
        }
        if (enabled && token.length() < 16) {
            throw new IllegalStateException("MUSIC_AI_MCP_TOKEN must contain at least 16 characters");
        }
    }
}
