package com.musicai.agent.domain;

public record Pitch(int midiNumber) {

    private static final String[] NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    public Pitch {
        if (midiNumber < 0 || midiNumber > 127) {
            throw new IllegalArgumentException("MIDI pitch must be between 0 and 127");
        }
    }

    public String step() {
        return NAMES[midiNumber % 12].substring(0, 1);
    }

    public int alter() {
        return NAMES[midiNumber % 12].endsWith("#") ? 1 : 0;
    }

    public int octave() {
        return midiNumber / 12 - 1;
    }
}
