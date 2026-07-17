package com.musicai.agent.infrastructure;

import dev.langchain4j.model.chat.ChatModel;

/** 创建特定提供商的 LangChain4j 聊天模型适配器。 */
public interface ChatModelFactory {

    /** @return 当前工厂是否支持指定提供商名称 */
    boolean supports(String provider);

    /** @return 根据已验证配置创建的聊天模型 */
    ChatModel create(LlmProperties properties);
}
