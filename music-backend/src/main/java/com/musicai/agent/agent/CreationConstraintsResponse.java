package com.musicai.agent.agent;

/**
 * 大模型提取出的候选创作约束。
 *
 * @param measures 小节数
 * @param tempo 每分钟拍数
 * @param keySignature 调号文本
 * @param style 音乐风格
 * @param tuning 吉他调弦名称
 * @param timeSignatureBeats 每小节拍数
 * @param timeSignatureBeatUnit 拍号的拍值单位
 * @param mood 情绪名称
 * @param rhythmicFeel 律动类型名称
 * @param complexity 复杂度等级
 */
public record CreationConstraintsResponse(
        Integer measures,
        Integer tempo,
        String keySignature,
        String style,
        String tuning,
        Integer timeSignatureBeats,
        Integer timeSignatureBeatUnit,
        String mood,
        String rhythmicFeel,
        Integer complexity) {
}
