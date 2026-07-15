package com.musicai.agent.application;

/**
 * 表示音乐生成任务在应用工作流中的生命周期状态。
 */
public enum GenerationStatus {
    /** 任务已创建，尚未开始执行。 */
    PENDING,
    /** 正在将自然语言提示解析为结构化创作约束。 */
    PARSING_REQUIREMENTS,
    /** 正在依据约束生成或重写乐谱。 */
    GENERATING,
    /** 正在校验乐谱结构、时值与可演奏性。 */
    VALIDATING,
    /** 正在生成并登记 MIDI 与 MusicXML 产物。 */
    EXPORTING,
    /** 任务及全部产物已成功完成。 */
    COMPLETED,
    /** 任务执行失败，错误信息已持久化并发布。 */
    FAILED
}
