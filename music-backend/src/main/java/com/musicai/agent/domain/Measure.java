package com.musicai.agent.domain;

import java.util.List;

/**
 * 表示带顺序编号的一小节音乐事件。
 *
 * @param number 从 1 开始的小节编号
 * @param events 按时间顺序排列的小节事件
 */
public record Measure(int number, List<MusicalEvent> events) {
    /**
     * 创建小节并固定事件列表快照。
     */
    public Measure {
        if (number <= 0) {
            throw new IllegalArgumentException("Measure number must be positive");
        }
        events = List.copyOf(events);
        if (events.isEmpty()) {
            throw new IllegalArgumentException("A measure must contain events");
        }
    }

    /**
     * 汇总小节内全部事件的精确时值。
     *
     * @return 小节总时值
     */
    public RhythmicDuration totalDuration() {
        return events.stream().map(MusicalEvent::duration).reduce(RhythmicDuration.ZERO, RhythmicDuration::add);
    }

    /**
     * 校验小节总时值是否恰好符合拍号。
     *
     * @param timeSignature 目标拍号
     * @throws IllegalStateException 小节时值与拍号不一致时
     */
    public void validateDuration(TimeSignature timeSignature) {
        // 使用分数值对象做精确比较，避免浮点拍值累加导致合法小节被误判。
        if (!totalDuration().equals(timeSignature.measureDuration())) {
            throw new IllegalStateException("Measure " + number + " has duration " + totalDuration()
                    + ", expected " + timeSignature.measureDuration());
        }
    }
}
