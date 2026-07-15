package com.musicai.agent.domain;

import java.util.Objects;

/**
 * 表示单个吉他音符事件。
 *
 * @param pitch 实际发声音高
 * @param duration 音符持续时值
 * @param fretPosition 音符在吉他指板上的位置
 */
public record NoteEvent(Pitch pitch, RhythmicDuration duration, FretPosition fretPosition) implements MusicalEvent {
    /**
     * 创建并校验音符事件。
     */
    public NoteEvent {
        Objects.requireNonNull(pitch, "pitch");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(fretPosition, "fretPosition");
        if (duration.equals(RhythmicDuration.ZERO)) {
            throw new IllegalArgumentException("A note must have a positive duration");
        }
    }
}
