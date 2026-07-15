package com.musicai.agent.infrastructure;

import com.musicai.agent.application.port.ProjectStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时恢复因上次进程中断而遗留的生成任务状态。
 */
@Component
public class InterruptedTaskRecovery implements ApplicationRunner {

    private final ProjectStore store;

    /**
     * 创建中断任务恢复器。
     *
     * @param store 项目持久化存储
     */
    public InterruptedTaskRecovery(ProjectStore store) {
        this.store = store;
    }

    /**
     * 将无法跨进程续跑的非终态任务标记为失败，避免重启后永久显示处理中。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        // 异步执行上下文只存在于旧进程内，启动时必须把遗留状态收敛到可解释的失败终态。
        store.failInterruptedTasks("Application restarted before task completion");
    }
}
