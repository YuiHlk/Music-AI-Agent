package com.musicai.agent.domain;

/**
 * 表示六弦吉他上的琴弦与品位坐标。
 *
 * @param stringNumber 琴弦编号，1 为最高音弦，6 为最低音弦
 * @param fret 品位编号，0 表示空弦
 */
public record FretPosition(int stringNumber, int fret) {

    /**
     * 创建并校验指板坐标。
     */
    public FretPosition {
        if (stringNumber < 1 || stringNumber > 6) {
            throw new IllegalArgumentException("Guitar string must be between 1 and 6");
        }
        if (fret < 0 || fret > 24) {
            throw new IllegalArgumentException("Fret must be between 0 and 24");
        }
    }
}
