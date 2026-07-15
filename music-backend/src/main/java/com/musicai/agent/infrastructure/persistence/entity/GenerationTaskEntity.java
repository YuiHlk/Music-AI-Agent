package com.musicai.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 音乐生成任务表的持久化实体。
 */
@TableName("generation_task")
public class GenerationTaskEntity {
    /** 任务主键。 */
    @TableId public String id;
    /** 所属项目主键。 */
    public String projectId;
    /** 任务状态。 */
    public String status;
    /** 用户生成提示词。 */
    public String prompt;
    /** 任务失败信息。 */
    public String errorMessage;
    /** 创建时间。 */
    public Instant createdAt;
    /** 最近更新时间。 */
    public Instant updatedAt;
}
