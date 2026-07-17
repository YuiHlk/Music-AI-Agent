package com.musicai.agent.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

/**
 * 模型无关的聊天模型连接配置。
 *
 * @param provider 模型提供商或兼容协议名称
 * @param baseUrl 模型服务基础地址
 * @param modelName 模型名称
 * @param apiKey 模型服务密钥
 */
@ConfigurationProperties("music-ai.llm")
public record LlmProperties(String provider, String baseUrl, String modelName, String apiKey) {

    /** 规范化外部配置，避免模型工厂重复处理空白和大小写。 */
    public LlmProperties {
        provider = normalizeProvider(provider);
        baseUrl = normalize(baseUrl);
        modelName = normalize(modelName);
        apiKey = normalize(apiKey);
    }

    /** 在创建任何网络客户端前快速暴露缺失配置。 */
    public void requireValidConfiguration() {
        if (provider.isBlank()) {
            throw new IllegalStateException("LLM_PROVIDER must not be blank when the llm profile is active");
        }
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("LLM_BASE_URL must not be blank when the llm profile is active");
        }
        if (modelName.isBlank()) {
            throw new IllegalStateException("LLM_MODEL must not be blank when the llm profile is active");
        }
        if (apiKey.isBlank()) {
            throw new IllegalStateException("LLM_API_KEY must not be blank when the llm profile is active");
        }
    }

    private static String normalizeProvider(String value) {
        return normalize(value).toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
