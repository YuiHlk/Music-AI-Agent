package com.musicai.agent.api.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 服务开关与共享 Bearer token 配置。
 *
 * @param enabled 是否启用 MCP 入口
 * @param token 客户端必须提供的共享 token；禁用时允许为空
 */
@ConfigurationProperties("music-ai.mcp")
public record MusicMcpProperties(boolean enabled, String token) {

    /** 将缺失 token 归一化为空字符串，便于统一校验。 */
    public MusicMcpProperties {
        token = token == null ? "" : token;
    }

    /**
     * 在创建网络入口前验证启用状态所需的最低配置。
     * 16 字符仅是误配置保护，不代表对 token 熵的完整验证。
     */
    void requireValidConfiguration() {
        if (enabled && token.isBlank()) {
            throw new IllegalStateException("MCP is enabled but MUSIC_AI_MCP_TOKEN is empty");
        }
        if (enabled && token.length() < 16) {
            throw new IllegalStateException("MUSIC_AI_MCP_TOKEN must contain at least 16 characters");
        }
    }
}
