package com.musicai.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 音乐项目表的持久化实体。
 */
@TableName("music_project")
public class MusicProjectEntity {
    /** 项目主键。 */
    @TableId public String id;
    /** 项目标题。 */
    public String title;
    /** 项目生成状态。 */
    public String status;
    /** 当前选中的版本号。 */
    public int currentVersion;
    /** 创建时间。 */
    public Instant createdAt;
    /** 最近更新时间。 */
    public Instant updatedAt;
}
