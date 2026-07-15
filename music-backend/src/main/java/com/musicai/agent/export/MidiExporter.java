package com.musicai.agent.export;

import com.musicai.agent.domain.MusicalEvent;
import com.musicai.agent.domain.NoteEvent;
import com.musicai.agent.domain.ChordEvent;
import com.musicai.agent.domain.Score;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 将领域乐谱转换为标准 MIDI 文件或内存序列。
 */
public final class MidiExporter {

    // 480 PPQ 可精确表达当前支持的二分至六十四分音符，同时保持常见 DAW/GP8 兼容性。
    /** 每四分音符的 MIDI 脉冲数，用于精确量化领域时值。 */
    public static final int PPQ = 480;

    /**
     * 校验乐谱并写出类型 1 的标准 MIDI 文件。
     *
     * @param score 待导出的乐谱
     * @param target 目标文件路径
     * @return 原目标路径
     * @throws IOException 无法创建目录、编码或写入 MIDI 时
     */
    public Path export(Score score, Path target) throws IOException {
        score.validate();
        try {
            Sequence sequence = toSequence(score);
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MidiSystem.write(sequence, 1, target.toFile());
            return target;
        } catch (InvalidMidiDataException exception) {
            throw new IOException("Could not create MIDI sequence", exception);
        }
    }

    /**
     * 将乐谱转换为包含速度、拍号和音符事件的 MIDI PPQ 序列。
     *
     * @param score 待转换的乐谱
     * @return MIDI 内存序列
     * @throws InvalidMidiDataException 元事件或通道事件不符合 MIDI 协议时
     */
    public Sequence toSequence(Score score) throws InvalidMidiDataException {
        Sequence sequence = new Sequence(Sequence.PPQ, PPQ);
        javax.sound.midi.Track midiTrack = sequence.createTrack();
        addTempo(midiTrack, score.tempo());
        addTimeSignature(midiTrack, score.timeSignature().beats(), score.timeSignature().beatUnit());

        long tick = 0;
        for (var measure : score.tracks().getFirst().measures()) {
            for (MusicalEvent event : measure.events()) {
                long duration = event.duration().toTicks(PPQ);
                if (event instanceof NoteEvent note) {
                    midiTrack.add(shortEvent(ShortMessage.NOTE_ON, note.pitch().midiNumber(), 96, tick));
                    midiTrack.add(shortEvent(ShortMessage.NOTE_OFF, note.pitch().midiNumber(), 0, tick + duration));
                } else if (event instanceof ChordEvent chord) {
                    for (var note : chord.notes()) {
                        midiTrack.add(shortEvent(ShortMessage.NOTE_ON, note.pitch().midiNumber(), 88, tick));
                        midiTrack.add(shortEvent(ShortMessage.NOTE_OFF, note.pitch().midiNumber(), 0,
                                tick + duration));
                    }
                }
                // 休止符不写 MIDI 事件，但仍推进时间轴；和弦内所有音共享同一时间窗口。
                tick += duration;
            }
        }
        return sequence;
    }

    private static void addTempo(javax.sound.midi.Track track, int bpm) throws InvalidMidiDataException {
        int microsPerQuarter = 60_000_000 / bpm;
        byte[] data = {(byte) (microsPerQuarter >> 16), (byte) (microsPerQuarter >> 8),
                (byte) microsPerQuarter};
        MetaMessage message = new MetaMessage();
        message.setMessage(0x51, data, data.length);
        track.add(new MidiEvent(message, 0));
    }

    private static void addTimeSignature(javax.sound.midi.Track track, int beats, int beatUnit)
            throws InvalidMidiDataException {
        int denominatorPower = Integer.numberOfTrailingZeros(beatUnit);
        MetaMessage message = new MetaMessage();
        byte[] data = {(byte) beats, (byte) denominatorPower, 24, 8};
        message.setMessage(0x58, data, data.length);
        track.add(new MidiEvent(message, 0));
    }

    private static MidiEvent shortEvent(int command, int pitch, int velocity, long tick)
            throws InvalidMidiDataException {
        ShortMessage message = new ShortMessage();
        message.setMessage(command, 0, pitch, velocity);
        return new MidiEvent(message, tick);
    }
}
