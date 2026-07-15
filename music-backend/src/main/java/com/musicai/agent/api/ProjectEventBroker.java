package com.musicai.agent.api;

import com.musicai.agent.application.port.ProjectEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程内项目 SSE 事件代理，不提供跨实例广播、断线重放或持久化投递保证。
 */
@Component
public class ProjectEventBroker implements ProjectEventPublisher {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 为项目创建 30 分钟 SSE 订阅并立即发送 CONNECTED 事件。
     * @param projectId 项目标识
     * @return 已注册的 emitter
     */
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

    /** {@inheritDoc} */
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
