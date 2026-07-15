package com.musicai.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 项目乐谱版本表的持久化实体。
 */
@TableName("project_version")
public class ProjectVersionEntity {
    /** 版本记录主键。 */
    @TableId public String id;
    /** 所属项目主键。 */
    public String projectId;
    /** 项目内递增的版本号。 */
    public int versionNumber;
    /** 序列化后的乐谱数据。 */
    public String scoreJson;
    /** 创建时间。 */
    public Instant createdAt;
}
