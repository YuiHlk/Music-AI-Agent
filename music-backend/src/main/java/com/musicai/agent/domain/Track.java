package com.musicai.agent.domain;

import java.util.List;

/**
 * 表示采用固定吉他调弦的一条乐谱轨道。
 *
 * @param name 轨道名称
 * @param tuning 吉他调弦
 * @param measures 按编号排列的小节
 */
public record Track(String name, GuitarTuning tuning, List<Measure> measures) {
    /**
     * 创建轨道并固定小节列表快照。
     */
    public Track {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Track name must not be blank");
        }
        measures = List.copyOf(measures);
        if (measures.isEmpty()) {
            throw new IllegalArgumentException("Track must contain measures");
        }
    }

    /**
     * 校验音高与指板位置一致，并限制和弦横向跨度。
     *
     * @param maximumFretSpan 允许的最大和弦品位跨度
     * @throws IllegalStateException 存在错误指法或跨度过大的和弦时
     */
    public void validatePlayability(int maximumFretSpan) {
        for (Measure measure : measures) {
            for (MusicalEvent event : measure.events()) {
                if (event instanceof NoteEvent note) {
                    validatePosition(note.pitch(), note.fretPosition());
                } else if (event instanceof ChordEvent chord) {
                    if (chord.fretSpan() > maximumFretSpan) {
                        throw new IllegalStateException("Chord fret span exceeds " + maximumFretSpan
                                + " in measure " + measure.number());
                    }
                    chord.notes().forEach(note -> validatePosition(note.pitch(), note.fretPosition()));
                }
            }
        }
    }

    private void validatePosition(Pitch pitch, FretPosition position) {
        int openPitch = tuning.openStringsHighToLow().get(position.stringNumber() - 1).midiNumber();
        if (openPitch + position.fret() != pitch.midiNumber()) {
            throw new IllegalStateException("Pitch " + pitch.midiNumber() + " does not match string "
                    + position.stringNumber() + " fret " + position.fret());
        }
    }
}
