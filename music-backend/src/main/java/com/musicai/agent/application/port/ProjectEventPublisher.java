package com.musicai.agent.application.port;

public interface ProjectEventPublisher {
    void publish(String projectId, String eventType, Object data);
}
