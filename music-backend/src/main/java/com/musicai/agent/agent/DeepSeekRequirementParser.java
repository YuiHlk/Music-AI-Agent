package com.musicai.agent.agent;

import com.musicai.agent.application.CreationConstraints;
import com.musicai.agent.domain.GuitarTuning;
import com.musicai.agent.domain.TimeSignature;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

import java.util.Locale;

public final class DeepSeekRequirementParser implements RequirementParser {

    private final CreationConstraintsAiService aiService;

    public DeepSeekRequirementParser(ChatModel chatModel) {
        this.aiService = AiServices.create(CreationConstraintsAiService.class, chatModel);
    }

    @Override
    public CreationConstraints parse(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("Creation request must not be blank");
        }
        CreationConstraintsResponse response = aiService.parse(userMessage);
        if (response == null) {
            throw new IllegalStateException("DeepSeek returned no creation constraints");
        }
        GuitarTuning tuning = switch (response.tuning().toUpperCase(Locale.ROOT).replace('-', '_')) {
            case "STANDARD", "STANDARD_E" -> GuitarTuning.STANDARD;
            case "DROP_D" -> GuitarTuning.DROP_D;
            default -> throw new IllegalArgumentException("Unsupported tuning returned by model: " + response.tuning());
        };
        return new CreationConstraints(response.measures(), response.tempo(), response.keySignature(),
                response.style(), tuning,
                new TimeSignature(response.timeSignatureBeats(), response.timeSignatureBeatUnit()));
    }
}
