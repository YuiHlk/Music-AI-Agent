package com.musicai.agent.infrastructure;

import com.musicai.agent.agent.DeepSeekRequirementParser;
import com.musicai.agent.agent.MusicCreationTools;
import com.musicai.agent.agent.MusicCreatorAgent;
import com.musicai.agent.agent.RequirementParser;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

/**
 * DeepSeek 模型及其音乐创作代理的 Spring 配置。
 */
@Configuration
@Profile("deepseek")
public class DeepSeekConfiguration {

    /**
     * 创建兼容 OpenAI 协议的 DeepSeek 聊天模型客户端。
     *
     * @param apiKey DeepSeek API 密钥
     * @param baseUrl DeepSeek API 基础地址
     * @param modelName 使用的模型名称
     * @return 聊天模型客户端
     */
    @Bean
    ChatModel deepSeekChatModel(
            @Value("${music-ai.deepseek.api-key}") String apiKey,
            @Value("${music-ai.deepseek.base-url}") String baseUrl,
            @Value("${music-ai.deepseek.model-name}") String modelName) {
        // 在 Bean 创建阶段快速失败，避免启用 deepseek 配置后直到首次请求才暴露密钥缺失。
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DEEPSEEK_API_KEY is required when the deepseek profile is active");
        }
        // DeepSeek 提供 OpenAI 兼容接口；显式限制超时和重试，避免模型故障长期占用生成线程。
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                // 请求和响应可能含用户创作内容，生产配置不写入模型通信日志。
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    /**
     * 创建使用 DeepSeek 提取结构化音乐需求的解析器。
     *
     * @param deepSeekChatModel DeepSeek 聊天模型
     * @return 音乐需求解析器
     */
    @Bean
    RequirementParser deepSeekRequirementParser(ChatModel deepSeekChatModel) {
        return new DeepSeekRequirementParser(deepSeekChatModel);
    }

    /**
     * 创建可调用音乐创作工具并保留有限会话上下文的代理。
     *
     * @param deepSeekChatModel DeepSeek 聊天模型
     * @param tools 代理可调用的音乐创作工具
     * @return 音乐创作代理
     */
    @Bean
    MusicCreatorAgent musicCreatorAgent(ChatModel deepSeekChatModel, MusicCreationTools tools) {
        // 为每个会话限制消息窗口，保留近期语境同时控制发送给模型的上下文规模。
        return AiServices.builder(MusicCreatorAgent.class)
                .chatModel(deepSeekChatModel)
                .tools(tools)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                .build();
    }
}
