package com.musicai.agent.infrastructure;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * 创建使用 OpenAI 兼容 Chat Completions 协议的模型客户端。
 *
 * <p>DeepSeek、OpenAI 及提供兼容端点的其他服务共享这一适配器；未来接入不兼容协议时，
 * 只需增加新的 {@link ChatModelFactory} 实现。</p>
 */
@Component
@Profile("llm")
public final class OpenAiCompatibleChatModelFactory implements ChatModelFactory {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
            "deepseek", "openai", "openai-compatible");

    @Override
    public boolean supports(String provider) {
        return SUPPORTED_PROVIDERS.contains(provider);
    }

    @Override
    public ChatModel create(LlmProperties properties) {
        return OpenAiChatModel.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .modelName(properties.modelName())
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                // 请求和响应可能包含用户创作内容，默认不写入模型通信日志。
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
