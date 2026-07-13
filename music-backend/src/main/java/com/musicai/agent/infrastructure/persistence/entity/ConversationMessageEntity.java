package com.musicai.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("conversation_message")
public class ConversationMessageEntity {
    @TableId public String id;
    public String projectId;
    public String role;
    public String content;
    public Instant createdAt;
}
