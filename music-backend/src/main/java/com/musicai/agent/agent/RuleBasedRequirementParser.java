package com.musicai.agent.agent;

import com.musicai.agent.application.CreationConstraints;
import com.musicai.agent.domain.GuitarTuning;
import com.musicai.agent.domain.TimeSignature;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Profile("!deepseek")
public final class RuleBasedRequirementParser implements RequirementParser {

    private static final Pattern MEASURES = Pattern.compile("(\\d+)\\s*(?:小节|measures?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEMPO = Pattern.compile("(\\d+)\\s*(?:BPM|拍)", Pattern.CASE_INSENSITIVE);

    @Override
    public CreationConstraints parse(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("Creation request must not be blank");
        }
        int measures = firstInteger(MEASURES, userMessage, 8);
        int tempo = firstInteger(TEMPO, userMessage, 120);
        String normalized = userMessage.toLowerCase(Locale.ROOT);
        String key = normalized.contains("a minor") || userMessage.contains("A小调") ? "A minor" : "E minor";
        String style = normalized.contains("metal") || userMessage.contains("金属") ? "metal" : "rock";
        GuitarTuning tuning = normalized.contains("drop d") || userMessage.contains("降d")
                ? GuitarTuning.DROP_D : GuitarTuning.STANDARD;
        return new CreationConstraints(measures, tempo, key, style, tuning, TimeSignature.FOUR_FOUR);
    }

    private static int firstInteger(Pattern pattern, String input, int fallback) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }
}
