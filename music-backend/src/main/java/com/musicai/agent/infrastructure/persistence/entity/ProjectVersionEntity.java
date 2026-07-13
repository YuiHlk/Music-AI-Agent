package com.musicai.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("project_version")
public class ProjectVersionEntity {
    @TableId public String id;
    public String projectId;
    public int versionNumber;
    public String scoreJson;
    public Instant createdAt;
}
