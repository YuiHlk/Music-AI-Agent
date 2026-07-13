package com.musicai.agent.application;

public enum GenerationStatus {
    PENDING,
    PARSING_REQUIREMENTS,
    GENERATING,
    VALIDATING,
    EXPORTING,
    COMPLETED,
    FAILED
}
