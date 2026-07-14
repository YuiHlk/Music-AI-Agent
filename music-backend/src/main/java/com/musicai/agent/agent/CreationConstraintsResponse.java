package com.musicai.agent.agent;

public record CreationConstraintsResponse(
        Integer measures,
        Integer tempo,
        String keySignature,
        String style,
        String tuning,
        Integer timeSignatureBeats,
        Integer timeSignatureBeatUnit,
        String mood,
        String rhythmicFeel,
        Integer complexity) {
}
