package com.musicai.agent.domain;

public record FretPosition(int stringNumber, int fret) {

    public FretPosition {
        if (stringNumber < 1 || stringNumber > 6) {
            throw new IllegalArgumentException("Guitar string must be between 1 and 6");
        }
        if (fret < 0 || fret > 24) {
            throw new IllegalArgumentException("Fret must be between 0 and 24");
        }
    }
}
