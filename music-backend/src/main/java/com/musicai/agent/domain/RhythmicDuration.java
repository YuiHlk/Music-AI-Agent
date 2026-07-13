package com.musicai.agent.domain;

public record RhythmicDuration(long numerator, long denominator) implements Comparable<RhythmicDuration> {

    public static final RhythmicDuration ZERO = new RhythmicDuration(0, 1);
    public static final RhythmicDuration QUARTER = new RhythmicDuration(1, 4);
    public static final RhythmicDuration EIGHTH = new RhythmicDuration(1, 8);

    public RhythmicDuration {
        if (denominator == 0) {
            throw new IllegalArgumentException("Duration denominator must not be zero");
        }
        if (numerator < 0 || denominator < 0) {
            throw new IllegalArgumentException("Duration must not be negative");
        }
        long gcd = gcd(numerator, denominator);
        numerator /= gcd;
        denominator /= gcd;
    }

    public RhythmicDuration add(RhythmicDuration other) {
        return new RhythmicDuration(
                Math.addExact(Math.multiplyExact(numerator, other.denominator),
                        Math.multiplyExact(other.numerator, denominator)),
                Math.multiplyExact(denominator, other.denominator));
    }

    public long toTicks(int pulsesPerQuarter) {
        long scaled = Math.multiplyExact(numerator, Math.multiplyExact(4L, pulsesPerQuarter));
        if (scaled % denominator != 0) {
            throw new IllegalArgumentException("Duration cannot be represented exactly at PPQ " + pulsesPerQuarter);
        }
        return scaled / denominator;
    }

    @Override
    public int compareTo(RhythmicDuration other) {
        return Long.compare(Math.multiplyExact(numerator, other.denominator),
                Math.multiplyExact(other.numerator, denominator));
    }

    private static long gcd(long left, long right) {
        while (right != 0) {
            long remainder = left % right;
            left = right;
            right = remainder;
        }
        return left == 0 ? 1 : left;
    }
}
