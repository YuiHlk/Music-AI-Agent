package com.musicai.agent.domain;

/**
 * 以 MIDI 音符编号表示绝对音高。
 *
 * @param midiNumber MIDI 音符编号，范围为 0 至 127
 */
public record Pitch(int midiNumber) {

    private static final String[] NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    /**
     * 创建并校验 MIDI 音高。
     */
    public Pitch {
        if (midiNumber < 0 || midiNumber > 127) {
            throw new IllegalArgumentException("MIDI pitch must be between 0 and 127");
        }
    }

    /**
     * 获取 MusicXML 使用的自然音级字母。
     *
     * @return C 至 B 之一
     */
    public String step() {
        return NAMES[midiNumber % 12].substring(0, 1);
    }

    /**
     * 获取相对自然音级的半音变化量。
     *
     * @return 升号音返回 1，自然音返回 0
     */
    public int alter() {
        return NAMES[midiNumber % 12].endsWith("#") ? 1 : 0;
    }

    /**
     * 获取科学音高记谱法中的八度编号。
     *
     * @return 八度编号，其中 MIDI 60 对应 C4
     */
    public int octave() {
        return midiNumber / 12 - 1;
    }
}
