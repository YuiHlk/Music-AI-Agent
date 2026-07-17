package com.musicai.agent.infrastructure;

import com.musicai.agent.agent.AiRequirementParser;
import com.musicai.agent.agent.MusicCreationTools;
import com.musicai.agent.agent.MusicCreatorAgent;
import com.musicai.agent.agent.RequirementParser;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/** 在启用 {@code llm} Profile 时组装模型客户端、需求解析器和主 Agent。 */
@Configuration(proxyBeanMethods = false)
@Profile("llm")
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfiguration {

    /** 根据提供商名称选择协议适配器并创建共享模型客户端。 */
    @Bean
    ChatModel chatModel(LlmProperties properties, List<ChatModelFactory> factories) {
        properties.requireValidConfiguration();
        ChatModelFactory factory = factories.stream()
                .filter(candidate -> candidate.supports(properties.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unsupported LLM provider: " + properties.provider()));
        return factory.create(properties);
    }

    /** 使用当前选定模型提取结构化音乐约束。 */
    @Bean
    RequirementParser aiRequirementParser(ChatModel chatModel) {
        return new AiRequirementParser(chatModel);
    }

    /** 创建可调用音乐工具并按项目保存有限上下文的主 Agent。 */
    @Bean
    MusicCreatorAgent musicCreatorAgent(ChatModel chatModel, MusicCreationTools tools) {
        return AiServices.builder(MusicCreatorAgent.class)
                .chatModel(chatModel)
                .tools(tools)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                .build();
    }
}
