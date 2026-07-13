package com.musicai.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.musicai.agent.infrastructure.persistence.mapper")
public class MusicAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicAiAgentApplication.class, args);
    }
}
