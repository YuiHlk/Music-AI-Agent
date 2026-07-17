package com.musicai.agent.agent;

import com.musicai.agent.application.CreationConstraints;
import com.musicai.agent.application.MusicalMood;
import com.musicai.agent.application.RhythmicFeel;
import com.musicai.agent.domain.GuitarTuning;
import com.musicai.agent.domain.TimeSignature;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在未启用外部聊天模型时以确定性规则解析中英文创作要求。
 *
 * <p>规则实现既提供离线降级能力，也避免应用层依赖外部模型可用性。</p>
 */
@Component
@Profile("!llm")
public final class RuleBasedRequirementParser implements RequirementParser {

    private static final Pattern MEASURES = Pattern.compile("(\\d+)\\s*(?:小节|measures?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEMPO = Pattern.compile("(\\d+)\\s*(?:BPM|拍)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_SIGNATURE = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static final Pattern COMPLEXITY = Pattern.compile("(?:难度|complexity)\\s*(?:为|[:=])?\\s*([1-5])",
            Pattern.CASE_INSENSITIVE);

    @Override
    public CreationConstraints parse(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("Creation request must not be blank");
        }
        String normalized = userMessage.toLowerCase(Locale.ROOT);
        int measures = firstInteger(MEASURES, userMessage, 8);
        int tempo = firstInteger(TEMPO, userMessage, 120);
        String key = detectKey(userMessage, normalized);
        String style = detectStyle(userMessage, normalized);
        GuitarTuning tuning = normalized.contains("drop d") || userMessage.contains("降D")
                ? GuitarTuning.DROP_D : GuitarTuning.STANDARD;
        TimeSignature timeSignature = detectTimeSignature(userMessage);
        MusicalMood mood = detectMood(userMessage, normalized);
        RhythmicFeel feel = detectFeel(userMessage, normalized, style);
        int complexity = firstInteger(COMPLEXITY, userMessage,
                normalized.contains("advanced") || userMessage.contains("复杂") ? 5 : 3);
        return new CreationConstraints(measures, tempo, key, style, tuning, timeSignature,
                mood, feel, complexity, AiRequirementParser.stableSeed(userMessage));
    }

    private static String detectKey(String original, String normalized) {
        String[] englishKeys = {"c", "c#", "d", "d#", "e", "f", "f#", "g", "g#", "a", "a#", "b"};
        for (String key : englishKeys) {
            if (normalized.matches(".*(?:^|\\s)" + Pattern.quote(key) + "\\s+(?:minor|major).*")) {
                return key.toUpperCase(Locale.ROOT) + (normalized.contains(key + " major") ? " major" : " minor");
            }
        }
        Matcher chinese = Pattern.compile("([A-Ga-g](?:#|b)?)\\s*(大调|小调)").matcher(original);
        if (chinese.find()) {
            return chinese.group(1).toUpperCase(Locale.ROOT) + ("大调".equals(chinese.group(2)) ? " major" : " minor");
        }
        return "E minor";
    }

    private static String detectStyle(String original, String normalized) {
        if (normalized.contains("metal") || original.contains("金属")) return "metal";
        if (normalized.contains("blues") || original.contains("布鲁斯")) return "blues";
        if (normalized.contains("funk") || original.contains("放克")) return "funk";
        if (normalized.contains("ambient") || original.contains("氛围")) return "ambient";
        return "rock";
    }

    private static MusicalMood detectMood(String original, String normalized) {
        if (normalized.contains("dark") || original.contains("黑暗")) return MusicalMood.DARK;
        if (normalized.contains("bright") || original.contains("明亮")) return MusicalMood.BRIGHT;
        if (normalized.contains("aggressive") || original.contains("凶猛") || original.contains("激进")) {
            return MusicalMood.AGGRESSIVE;
        }
        if (normalized.contains("melancholic") || normalized.contains("sad") || original.contains("忧郁")) {
            return MusicalMood.MELANCHOLIC;
        }
        if (normalized.contains("calm") || original.contains("平静")) return MusicalMood.CALM;
        return MusicalMood.ENERGETIC;
    }

    private static RhythmicFeel detectFeel(String original, String normalized, String style) {
        if (normalized.contains("syncopated") || original.contains("切分")) return RhythmicFeel.SYNCOPATED;
        if (normalized.contains("half-time") || original.contains("半拍")) return RhythmicFeel.HALF_TIME;
        if (normalized.contains("driving") || original.contains("推进") || "metal".equals(style)) {
            return RhythmicFeel.DRIVING;
        }
        return RhythmicFeel.STRAIGHT;
    }

    private static TimeSignature detectTimeSignature(String input) {
        Matcher matcher = TIME_SIGNATURE.matcher(input);
        return matcher.find() ? new TimeSignature(Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2))) : TimeSignature.FOUR_FOUR;
    }

    private static int firstInteger(Pattern pattern, String input, int fallback) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }
}
