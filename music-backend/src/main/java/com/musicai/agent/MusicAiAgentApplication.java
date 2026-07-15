package com.musicai.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Music AI Agent 的 Spring Boot 启动入口，启用异步任务和 MyBatis mapper 扫描。
 */
@SpringBootApplication
@EnableAsync
@MapperScan("com.musicai.agent.infrastructure.persistence.mapper")
public class MusicAiAgentApplication {

    /**
     * 启动应用上下文。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MusicAiAgentApplication.class, args);
    }
}
