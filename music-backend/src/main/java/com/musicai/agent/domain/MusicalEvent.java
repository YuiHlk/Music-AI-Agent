package com.musicai.agent.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = NoteEvent.class, name = "note"),
        @JsonSubTypes.Type(value = RestEvent.class, name = "rest"),
        @JsonSubTypes.Type(value = ChordEvent.class, name = "chord")
})
public sealed interface MusicalEvent permits NoteEvent, RestEvent, ChordEvent {
    RhythmicDuration duration();
}
