package com.musicai.agent.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 乐谱时间线上的封闭事件类型，统一描述音符、休止与和弦。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = NoteEvent.class, name = "note"),
        @JsonSubTypes.Type(value = RestEvent.class, name = "rest"),
        @JsonSubTypes.Type(value = ChordEvent.class, name = "chord")
})
public sealed interface MusicalEvent permits NoteEvent, RestEvent, ChordEvent {
    /**
     * 获取事件在乐谱中的持续时值。
     *
     * @return 事件时值
     */
    RhythmicDuration duration();
}
