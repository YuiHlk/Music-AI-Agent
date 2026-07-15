package com.musicai.agent.domain;

/**
 * 表示每小节拍数及作为一拍的音符单位。
 *
 * @param beats 每小节拍数
 * @param beatUnit 拍单位的分母，必须为 2 的幂
 */
public record TimeSignature(int beats, int beatUnit) {

    /** 常用的四四拍。 */
    public static final TimeSignature FOUR_FOUR = new TimeSignature(4, 4);

    /**
     * 创建并校验拍号。
     */
    public TimeSignature {
        if (beats <= 0 || beatUnit <= 0 || (beatUnit & (beatUnit - 1)) != 0) {
            throw new IllegalArgumentException("Invalid time signature: " + beats + "/" + beatUnit);
        }
    }

    /**
     * 计算一个完整小节相对于全音符的时值。
     *
     * @return 小节总时值
     */
    public RhythmicDuration measureDuration() {
        return new RhythmicDuration(beats, beatUnit);
    }
}
