package com.musicai.agent.domain;

public record TimeSignature(int beats, int beatUnit) {

    public static final TimeSignature FOUR_FOUR = new TimeSignature(4, 4);

    public TimeSignature {
        if (beats <= 0 || beatUnit <= 0 || (beatUnit & (beatUnit - 1)) != 0) {
            throw new IllegalArgumentException("Invalid time signature: " + beats + "/" + beatUnit);
        }
    }

    public RhythmicDuration measureDuration() {
        return new RhythmicDuration(beats, beatUnit);
    }
}
