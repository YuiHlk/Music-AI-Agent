package com.musicai.agent.agent;

import com.musicai.agent.application.CreationConstraints;

/**
 * 将用户创作描述转换为生成器可消费约束的端口。
 *
 * <p>实现必须阻止未校验的外部或模型值直接进入音乐生成领域。</p>
 */
public interface RequirementParser {

    /**
     * 解析一条创作请求。
     *
     * @param userMessage 非空的自然语言请求
     * @return 完整且可供应用层使用的创作约束
     * @throws IllegalArgumentException 请求为空或包含不支持的约束时
     */
    CreationConstraints parse(String userMessage);
}
