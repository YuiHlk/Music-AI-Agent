package com.musicai.agent.api;

import com.musicai.agent.application.port.ProjectEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ProjectEventBroker implements ProjectEventPublisher {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String projectId) {
        SseEmitter emitter = new SseEmitter(30L * 60 * 1000);
        emitters.computeIfAbsent(projectId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(projectId, emitter));
        emitter.onTimeout(() -> remove(projectId, emitter));
        emitter.onError(error -> remove(projectId, emitter));
        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data(projectId));
        } catch (IOException exception) {
            remove(projectId, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @Override
    public void publish(String projectId, String eventType, Object data) {
        for (SseEmitter emitter : List.copyOf(emitters.getOrDefault(projectId, new CopyOnWriteArrayList<>()))) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(data));
            } catch (IOException exception) {
                remove(projectId, emitter);
                emitter.completeWithError(exception);
            }
        }
    }

    private void remove(String projectId, SseEmitter emitter) {
        List<SseEmitter> projectEmitters = emitters.get(projectId);
        if (projectEmitters != null) {
            projectEmitters.remove(emitter);
            if (projectEmitters.isEmpty()) {
                emitters.remove(projectId);
            }
        }
    }
}
