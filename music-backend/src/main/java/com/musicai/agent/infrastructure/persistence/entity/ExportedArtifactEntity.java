package com.musicai.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("exported_artifact")
public class ExportedArtifactEntity {
    @TableId public String id;
    public String projectId;
    public int versionNumber;
    public String artifactType;
    public String storagePath;
    public Instant createdAt;
}
