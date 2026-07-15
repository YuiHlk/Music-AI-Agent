package com.musicai.agent.domain;

import java.util.Locale;
import java.util.Map;

/**
 * 表示可用于生成及导出的调号信息。
 *
 * @param tonic 主音名称
 * @param rootPitchClass 主音的十二平均律音级编号，C 为 0
 * @param mode 大调或小调调式
 * @param fifths MusicXML 调号所需的升降号数量，升号为正、降号为负
 */
public record KeySignature(String tonic, int rootPitchClass, Mode mode, int fifths) {

    private static final Map<String, Integer> PITCH_CLASSES = Map.ofEntries(
            Map.entry("C", 0), Map.entry("C#", 1), Map.entry("DB", 1),
            Map.entry("D", 2), Map.entry("D#", 3), Map.entry("EB", 3),
            Map.entry("E", 4), Map.entry("FB", 4), Map.entry("E#", 5),
            Map.entry("F", 5), Map.entry("F#", 6), Map.entry("GB", 6),
            Map.entry("G", 7), Map.entry("G#", 8), Map.entry("AB", 8),
            Map.entry("A", 9), Map.entry("A#", 10), Map.entry("BB", 10),
            Map.entry("B", 11), Map.entry("CB", 11));
    private static final Map<String, Integer> MAJOR_FIFTHS = Map.ofEntries(
            Map.entry("C", 0), Map.entry("G", 1), Map.entry("D", 2), Map.entry("A", 3),
            Map.entry("E", 4), Map.entry("B", 5), Map.entry("F#", 6), Map.entry("C#", 7),
            Map.entry("F", -1), Map.entry("BB", -2), Map.entry("EB", -3), Map.entry("AB", -4),
            Map.entry("DB", -5), Map.entry("GB", -6), Map.entry("CB", -7));
    private static final Map<String, Integer> MINOR_FIFTHS = Map.ofEntries(
            Map.entry("A", 0), Map.entry("E", 1), Map.entry("B", 2), Map.entry("F#", 3),
            Map.entry("C#", 4), Map.entry("G#", 5), Map.entry("D#", 6), Map.entry("A#", 7),
            Map.entry("D", -1), Map.entry("G", -2), Map.entry("C", -3), Map.entry("F", -4),
            Map.entry("BB", -5), Map.entry("EB", -6), Map.entry("AB", -7));

    /**
     * 创建并校验调号。
     */
    public KeySignature {
        if (!PITCH_CLASSES.containsKey(tonic)) {
            throw new IllegalArgumentException("Unsupported key tonic: " + tonic);
        }
    }

    /**
     * 解析包含主音及中英文大小调标识的调号文本。
     *
     * @param value 调号文本，例如 {@code E minor} 或 {@code C 大调}
     * @return 规范化后的调号
     * @throws IllegalArgumentException 文本为空或主音不受支持时
     */
    public static KeySignature parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Key signature must not be blank");
        }
        String normalized = value.strip().replace("♯", "#").replace("♭", "b")
                .toUpperCase(Locale.ROOT);
        String tonic = normalized.split("\\s+")[0];
        Mode mode = normalized.contains("MAJOR") || normalized.contains("大调") ? Mode.MAJOR : Mode.MINOR;
        Integer pitchClass = PITCH_CLASSES.get(tonic);
        if (pitchClass == null) {
            throw new IllegalArgumentException("Unsupported key signature: " + value);
        }
        int fifths = (mode == Mode.MAJOR ? MAJOR_FIFTHS : MINOR_FIFTHS).getOrDefault(tonic, 0);
        return new KeySignature(tonic, pitchClass, mode, fifths);
    }

    /**
     * 调号支持的调式类别。
     */
    public enum Mode {
        /** 大调，以大调音阶组织音高材料。 */
        MAJOR,
        /** 小调，以小调音阶组织音高材料。 */
        MINOR
    }
}
