package com.musicai.agent.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 表示在同一起始时刻发声的吉他和弦事件。
 *
 * @param notes 和弦中的按弦音，必须占用互不重复的琴弦
 * @param duration 和弦持续时值
 */
public record ChordEvent(List<ChordNote> notes, RhythmicDuration duration) implements MusicalEvent {
    /**
     * 校验和弦音数、时值及琴弦占用约束。
     */
    public ChordEvent {
        notes = List.copyOf(notes);
        Objects.requireNonNull(duration, "duration");
        if (notes.size() < 2 || notes.size() > 6) {
            throw new IllegalArgumentException("A guitar chord must contain between two and six notes");
        }
        if (duration.equals(RhythmicDuration.ZERO)) {
            throw new IllegalArgumentException("A chord must have a positive duration");
        }
        HashSet<Integer> strings = new HashSet<>();
        for (ChordNote note : notes) {
            if (!strings.add(note.fretPosition().stringNumber())) {
                throw new IllegalArgumentException("A chord cannot use the same guitar string twice");
            }
        }
    }

    /**
     * 计算非空弦最低品位到最高品位之间的跨度。
     *
     * @return 和弦品位跨度
     */
    public int fretSpan() {
        int minimum = notes.stream().mapToInt(note -> note.fretPosition().fret()).filter(fret -> fret > 0)
                .min().orElse(0);
        int maximum = notes.stream().mapToInt(note -> note.fretPosition().fret()).max().orElse(0);
        return minimum == 0 ? maximum : maximum - minimum;
    }
}
