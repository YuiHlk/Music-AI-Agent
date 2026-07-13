package com.musicai.agent.agent;

public record CreationConstraintsResponse(
        int measures,
        int tempo,
        String keySignature,
        String style,
        String tuning,
        int timeSignatureBeats,
        int timeSignatureBeatUnit) {
}
