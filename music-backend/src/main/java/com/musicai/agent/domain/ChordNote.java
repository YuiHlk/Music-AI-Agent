package com.musicai.agent.domain;

import java.util.Objects;

/**
 * 表示和弦中的一个音及其吉他指板位置。
 *
 * @param pitch 实际发声音高
 * @param fretPosition 对应的琴弦与品位
 */
public record ChordNote(Pitch pitch, FretPosition fretPosition) {
    /**
     * 创建并校验和弦音。
     */
    public ChordNote {
        Objects.requireNonNull(pitch, "pitch");
        Objects.requireNonNull(fretPosition, "fretPosition");
    }
}
