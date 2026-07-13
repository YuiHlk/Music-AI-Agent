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

@Configuration
@Profile("deepseek")
public class DeepSeekConfiguration {

    @Bean
    ChatModel deepSeekChatModel(
            @Value("${music-ai.deepseek.api-key}") String apiKey,
            @Value("${music-ai.deepseek.base-url}") String baseUrl,
            @Value("${music-ai.deepseek.model-name}") String modelName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DEEPSEEK_API_KEY is required when the deepseek profile is active");
        }
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    RequirementParser deepSeekRequirementParser(ChatModel deepSeekChatModel) {
        return new DeepSeekRequirementParser(deepSeekChatModel);
    }

    @Bean
    MusicCreatorAgent musicCreatorAgent(ChatModel deepSeekChatModel, MusicCreationTools tools) {
        return AiServices.builder(MusicCreatorAgent.class)
                .chatModel(deepSeekChatModel)
                .tools(tools)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                .build();
    }
}
