package com.musicai.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("generation_task")
public class GenerationTaskEntity {
    @TableId public String id;
    public String projectId;
    public String status;
    public String prompt;
    public String errorMessage;
    public Instant createdAt;
    public Instant updatedAt;
}
