package com.musicai.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 导出产物表的持久化实体。
 */
@TableName("exported_artifact")
public class ExportedArtifactEntity {
    /** 产物主键。 */
    @TableId public String id;
    /** 所属项目主键。 */
    public String projectId;
    /** 对应的项目版本号。 */
    public int versionNumber;
    /** 产物类型。 */
    public String artifactType;
    /** 产物存储路径。 */
    public String storagePath;
    /** 创建时间。 */
    public Instant createdAt;
}
