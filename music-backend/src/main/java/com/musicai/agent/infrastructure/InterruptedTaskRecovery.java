package com.musicai.agent.infrastructure;

import com.musicai.agent.application.port.ProjectStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class InterruptedTaskRecovery implements ApplicationRunner {

    private final ProjectStore store;

    public InterruptedTaskRecovery(ProjectStore store) {
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) {
        store.failInterruptedTasks("Application restarted before task completion");
    }
}
