package com.musicai.agent.domain;

/**
 * 以最简非负分数表示相对于全音符的精确时值。
 *
 * @param numerator 时值分子
 * @param denominator 时值分母
 */
public record RhythmicDuration(long numerator, long denominator) implements Comparable<RhythmicDuration> {

    /** 零时值，作为时值累加的单位元。 */
    public static final RhythmicDuration ZERO = new RhythmicDuration(0, 1);
    /** 四分音符时值。 */
    public static final RhythmicDuration QUARTER = new RhythmicDuration(1, 4);
    /** 八分音符时值。 */
    public static final RhythmicDuration EIGHTH = new RhythmicDuration(1, 8);

    /**
     * 创建时值并约分为最简形式。
     */
    public RhythmicDuration {
        if (denominator == 0) {
            throw new IllegalArgumentException("Duration denominator must not be zero");
        }
        if (numerator < 0 || denominator < 0) {
            throw new IllegalArgumentException("Duration must not be negative");
        }
        long gcd = gcd(numerator, denominator);
        numerator /= gcd;
        denominator /= gcd;
    }

    /**
     * 精确相加两个时值。
     *
     * @param other 待相加时值
     * @return 约分后的时值之和
     * @throws ArithmeticException 中间结果超出 {@code long} 范围时
     */
    public RhythmicDuration add(RhythmicDuration other) {
        return new RhythmicDuration(
                Math.addExact(Math.multiplyExact(numerator, other.denominator),
                        Math.multiplyExact(other.numerator, denominator)),
                Math.multiplyExact(denominator, other.denominator));
    }

    /**
     * 按给定 PPQ 将乐谱时值精确换算为整数刻度。
     *
     * @param pulsesPerQuarter 每四分音符的脉冲数
     * @return 对应的整数刻度数
     * @throws IllegalArgumentException 当前 PPQ 无法精确表达该时值时
     * @throws ArithmeticException 换算结果溢出时
     */
    public long toTicks(int pulsesPerQuarter) {
        long scaled = Math.multiplyExact(numerator, Math.multiplyExact(4L, pulsesPerQuarter));
        if (scaled % denominator != 0) {
            throw new IllegalArgumentException("Duration cannot be represented exactly at PPQ " + pulsesPerQuarter);
        }
        return scaled / denominator;
    }

    /**
     * 通过交叉相乘比较两个分数时值，避免浮点误差。
     *
     * @param other 待比较时值
     * @return 负数、零或正数，分别表示小于、等于或大于目标时值
     */
    @Override
    public int compareTo(RhythmicDuration other) {
        return Long.compare(Math.multiplyExact(numerator, other.denominator),
                Math.multiplyExact(other.numerator, denominator));
    }

    private static long gcd(long left, long right) {
        while (right != 0) {
            long remainder = left % right;
            left = right;
            right = remainder;
        }
        return left == 0 ? 1 : left;
    }
}
