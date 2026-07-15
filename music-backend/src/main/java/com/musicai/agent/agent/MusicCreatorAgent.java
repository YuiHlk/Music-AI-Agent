package com.musicai.agent.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 面向单个音乐项目的工具调用型对话代理。
 *
 * <p>项目 ID 同时隔离对话记忆；代理输出仅用于编排，持久化与生成操作仍须通过受控工具进入应用层。</p>
 */
public interface MusicCreatorAgent {

    /**
     * 在指定项目的记忆上下文中处理用户请求。
     *
     * @param projectId 当前项目标识，也是对话记忆标识
     * @param message 用户请求
     * @return 代理对用户的文本响应
     */
    @SystemMessage(fromResource = "prompts/music-agent-system.txt")
    @UserMessage("Current project ID: {{projectId}}\nUser request: {{message}}")
    String chat(@MemoryId @V("projectId") String projectId, @V("message") String message);
}
