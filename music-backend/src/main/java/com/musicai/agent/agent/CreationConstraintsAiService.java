package com.musicai.agent.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 将自然语言创作请求交给模型提取为结构化约束的内部 AI 服务。
 *
 * <p>模型输出是不可信边界数据，调用方必须在进入领域逻辑前完成默认值填充、类型转换与范围校验。</p>
 */
interface CreationConstraintsAiService {

    /**
     * 从用户消息中提取候选创作约束。
     *
     * @param message 用户的自然语言创作请求
     * @return 模型返回的候选约束，尚未经过领域校验
     */
    @SystemMessage(fromResource = "prompts/music-requirements-system.txt")
    CreationConstraintsResponse parse(@UserMessage String message);
}
