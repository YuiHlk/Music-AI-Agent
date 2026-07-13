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

public final class MidiExporter {

    public static final int PPQ = 480;

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
