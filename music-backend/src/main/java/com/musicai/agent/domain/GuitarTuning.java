package com.musicai.agent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record GuitarTuning(String name, List<Pitch> openStringsHighToLow) {

    public static final GuitarTuning STANDARD = new GuitarTuning("Standard E", List.of(
            new Pitch(64), new Pitch(59), new Pitch(55), new Pitch(50), new Pitch(45), new Pitch(40)));
    public static final GuitarTuning DROP_D = new GuitarTuning("Drop D", List.of(
            new Pitch(64), new Pitch(59), new Pitch(55), new Pitch(50), new Pitch(45), new Pitch(38)));

    public GuitarTuning {
        openStringsHighToLow = List.copyOf(openStringsHighToLow);
        if (openStringsHighToLow.size() != 6) {
            throw new IllegalArgumentException("A guitar tuning must contain exactly six strings");
        }
    }

    public List<FretPosition> positionsFor(Pitch pitch, int maximumFret) {
        if (maximumFret < 0 || maximumFret > 24) {
            throw new IllegalArgumentException("Maximum fret must be between 0 and 24");
        }
        List<FretPosition> positions = new ArrayList<>();
        for (int index = 0; index < openStringsHighToLow.size(); index++) {
            int fret = pitch.midiNumber() - openStringsHighToLow.get(index).midiNumber();
            if (fret >= 0 && fret <= maximumFret) {
                positions.add(new FretPosition(index + 1, fret));
            }
        }
        return positions.stream()
                .sorted(Comparator.comparingInt(FretPosition::fret).thenComparingInt(FretPosition::stringNumber))
                .toList();
    }

    public FretPosition requirePlayablePosition(Pitch pitch, int maximumFret) {
        return positionsFor(pitch, maximumFret).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pitch " + pitch.midiNumber() + " is not playable"));
    }
}
