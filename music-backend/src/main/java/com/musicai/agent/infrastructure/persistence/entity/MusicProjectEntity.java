package com.musicai.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("music_project")
public class MusicProjectEntity {
    @TableId public String id;
    public String title;
    public String status;
    public int currentVersion;
    public Instant createdAt;
    public Instant updatedAt;
}
