package com.musicai.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * 会话消息表的持久化实体。
 */
@TableName("conversation_message")
public class ConversationMessageEntity {
    /** 消息主键。 */
    @TableId public String id;
    /** 所属项目主键。 */
    public String projectId;
    /** 消息角色。 */
    public String role;
    /** 消息正文。 */
    public String content;
    /** 创建时间。 */
    public Instant createdAt;
}
