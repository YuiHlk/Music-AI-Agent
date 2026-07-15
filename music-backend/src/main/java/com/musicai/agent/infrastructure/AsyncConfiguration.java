package com.musicai.agent.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 音乐生成异步任务的线程池配置。
 */
@Configuration
public class AsyncConfiguration {

    /**
     * 创建隔离于 Spring 默认执行器的音乐任务执行器。
     *
     * @return 用于执行音乐生成任务的线程池
     */
    @Bean("musicTaskExecutor")
    TaskExecutor musicTaskExecutor() {
        // 音乐生成属于耗时任务；限制并发数与排队容量，避免请求突增耗尽线程和内存。
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("music-task-");
        // 关闭时短暂等待已接收任务，减少正常重启把生成流程截断并留给恢复器清理的情况。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
