package com.musicai.agent.domain;

import java.util.Objects;

/**
 * 表示仅推进乐谱时间轴而不发声的休止事件。
 *
 * @param duration 休止持续时值
 */
public record RestEvent(RhythmicDuration duration) implements MusicalEvent {
    /**
     * 创建并校验休止事件。
     */
    public RestEvent {
        Objects.requireNonNull(duration, "duration");
        if (duration.equals(RhythmicDuration.ZERO)) {
            throw new IllegalArgumentException("A rest must have a positive duration");
        }
    }
}
