package com.musicai.agent.application;

import com.musicai.agent.domain.GuitarTuning;
import com.musicai.agent.domain.TimeSignature;

/**
 * 汇总一次吉他 Riff 创作所需的结构、风格与变化约束。
 *
 * @param measures 生成小节数
 * @param tempo 每分钟四分音符拍数
 * @param keySignature 调号文本
 * @param style 规范化为小写的风格名称
 * @param tuning 吉他调弦
 * @param timeSignature 拍号
 * @param mood 音乐情绪
 * @param rhythmicFeel 节奏感觉
 * @param complexity 1 至 5 的创作复杂度
 * @param variationSeed 用于复现提示词变化的随机种子
 */
public record CreationConstraints(
        int measures,
        int tempo,
        String keySignature,
        String style,
        GuitarTuning tuning,
        TimeSignature timeSignature,
        MusicalMood mood,
        RhythmicFeel rhythmicFeel,
        int complexity,
        long variationSeed) {

    /**
     * 创建并校验完整创作约束。
     */
    public CreationConstraints {
        if (measures < 1 || measures > 128) {
            throw new IllegalArgumentException("Measure count must be between 1 and 128");
        }
        if (tempo < 20 || tempo > 300) {
            throw new IllegalArgumentException("Tempo must be between 20 and 300 BPM");
        }
        if (keySignature == null || keySignature.isBlank() || style == null || style.isBlank()) {
            throw new IllegalArgumentException("Key signature and style are required");
        }
        if (tuning == null || timeSignature == null || mood == null || rhythmicFeel == null) {
            throw new IllegalArgumentException("Tuning, time signature, mood and rhythmic feel are required");
        }
        if (complexity < 1 || complexity > 5) {
            throw new IllegalArgumentException("Complexity must be between 1 and 5");
        }
        style = style.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 使用默认情绪、节奏、复杂度和变化种子创建兼容约束。
     *
     * @param measures 生成小节数
     * @param tempo 每分钟四分音符拍数
     * @param keySignature 调号文本
     * @param style 风格名称
     * @param tuning 吉他调弦
     * @param timeSignature 拍号
     */
    public CreationConstraints(int measures, int tempo, String keySignature, String style,
                               GuitarTuning tuning, TimeSignature timeSignature) {
        this(measures, tempo, keySignature, style, tuning, timeSignature,
                MusicalMood.ENERGETIC, defaultFeel(style), 3, 0L);
    }

    /**
     * 创建标准调弦、E 小调、四四拍的默认摇滚 Riff 约束。
     *
     * @return 默认创作约束
     */
    public static CreationConstraints defaultRockRiff() {
        return new CreationConstraints(8, 120, "E minor", "rock", GuitarTuning.STANDARD,
                TimeSignature.FOUR_FOUR, MusicalMood.ENERGETIC, RhythmicFeel.STRAIGHT, 3, 0L);
    }

    /**
     * 将REST、内部Agent或MCP提供的结构化字符串值收敛为领域值对象。
     *
     * @return 完成枚举、调弦和拍号校验的创作约束
     */
    public static CreationConstraints fromStructured(int measures, int tempo, String keySignature, String style,
                                                     String tuning, int timeSignatureBeats,
                                                     int timeSignatureBeatUnit, String mood,
                                                     String rhythmicFeel, int complexity, long variationSeed) {
        GuitarTuning parsedTuning = switch (normalizeName(tuning)) {
            case "STANDARD", "STANDARD_E" -> GuitarTuning.STANDARD;
            case "DROP_D" -> GuitarTuning.DROP_D;
            default -> throw new IllegalArgumentException("Unsupported tuning: " + tuning);
        };
        MusicalMood parsedMood = parseEnum(MusicalMood.class, mood, "mood");
        RhythmicFeel parsedFeel = parseEnum(RhythmicFeel.class, rhythmicFeel, "rhythmicFeel");
        return new CreationConstraints(measures, tempo, keySignature, style, parsedTuning,
                new TimeSignature(timeSignatureBeats, timeSignatureBeatUnit), parsedMood, parsedFeel,
                complexity, variationSeed);
    }

    private static RhythmicFeel defaultFeel(String style) {
        return style != null && style.toLowerCase(java.util.Locale.ROOT).contains("metal")
                ? RhythmicFeel.DRIVING : RhythmicFeel.STRAIGHT;
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Structured constraint value must not be blank");
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, normalizeName(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported " + field + ": " + value, exception);
        }
    }
}
