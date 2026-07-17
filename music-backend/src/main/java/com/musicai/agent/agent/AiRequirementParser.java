package com.musicai.agent.agent;

import com.musicai.agent.application.CreationConstraints;
import com.musicai.agent.application.MusicalMood;
import com.musicai.agent.application.RhythmicFeel;
import com.musicai.agent.domain.GuitarTuning;
import com.musicai.agent.domain.TimeSignature;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

import java.util.Locale;

/**
 * 使用可配置聊天模型提取创作要求，并将模型结果收敛为受控的应用层约束。
 *
 * <p>该类型是 AI 信任边界：模型只提供候选值，枚举映射、缺省值和确定性种子均由本地代码决定。</p>
 */
public final class AiRequirementParser implements RequirementParser {

    private final CreationConstraintsAiService aiService;

    /** @param chatModel 用于约束提取的任意 LangChain4j 聊天模型 */
    public AiRequirementParser(ChatModel chatModel) {
        this.aiService = AiServices.create(CreationConstraintsAiService.class, chatModel);
    }

    @Override
    public CreationConstraints parse(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("Creation request must not be blank");
        }
        CreationConstraintsResponse response = aiService.parse(userMessage);
        if (response == null) {
            throw new IllegalStateException("Model returned no creation constraints");
        }
        String tuningName = valueOrDefault(response.tuning(), "STANDARD");
        GuitarTuning tuning = switch (tuningName.toUpperCase(Locale.ROOT).replace('-', '_')) {
            case "STANDARD", "STANDARD_E" -> GuitarTuning.STANDARD;
            case "DROP_D" -> GuitarTuning.DROP_D;
            default -> throw new IllegalArgumentException("Unsupported tuning returned by model: " + response.tuning());
        };
        MusicalMood mood = enumOrDefault(response.mood(), MusicalMood.class, MusicalMood.ENERGETIC);
        RhythmicFeel feel = enumOrDefault(response.rhythmicFeel(), RhythmicFeel.class, defaultFeel(response.style()));
        int complexity = response.complexity() == null ? 3 : response.complexity();
        return new CreationConstraints(integerOrDefault(response.measures(), 8),
                integerOrDefault(response.tempo(), 120), valueOrDefault(response.keySignature(), "E minor"),
                valueOrDefault(response.style(), "rock"), tuning,
                new TimeSignature(integerOrDefault(response.timeSignatureBeats(), 4),
                        integerOrDefault(response.timeSignatureBeatUnit(), 4)),
                mood, feel, complexity, stableSeed(userMessage));
    }

    private static int integerOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static RhythmicFeel defaultFeel(String style) {
        return style != null && style.toLowerCase(Locale.ROOT).contains("metal")
                ? RhythmicFeel.DRIVING : RhythmicFeel.STRAIGHT;
    }

    private static <E extends Enum<E>> E enumOrDefault(String value, Class<E> enumType, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    static long stableSeed(String prompt) {
        long hash = 0xcbf29ce484222325L;
        for (byte value : prompt.strip().toLowerCase(Locale.ROOT)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            hash ^= value & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
