package com.musicai.agent.application.port;

/**
 * 发布项目级客户端事件的应用端口；事件允许丢失，不承诺持久化或投递确认。
 */
public interface ProjectEventPublisher {
    /**
     * 发布一个客户端可见的项目事件。
     *
     * @param projectId 项目标识
     * @param eventType 客户端协议中的事件名称
     * @param data 可序列化的事件数据
     */
    void publish(String projectId, String eventType, Object data);
}
