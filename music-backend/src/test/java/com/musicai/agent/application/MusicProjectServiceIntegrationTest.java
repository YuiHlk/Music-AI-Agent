package com.musicai.agent.application;

import com.musicai.agent.application.port.ProjectStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MusicProjectServiceIntegrationTest {

    @Autowired
    MusicProjectService projects;

    @Test
    void generatesPersistsAndExportsVerticalSlice() throws Exception {
        var project = projects.createProject("Integration riff");
        var task = projects.generate(project.id(),
                "鐢熸垚涓€娈?8 灏忚妭銆?20 BPM銆丒 灏忚皟銆佹爣鍑嗚皟寮︾殑鎽囨粴鍚変粬 Riff");

        ProjectStore.StoredTask completed = awaitTerminal(task.id());

        assertThat(completed.status()).isEqualTo(GenerationStatus.COMPLETED.name());
        assertThat(projects.requireProject(project.id()).currentVersion()).isEqualTo(1);
        assertThat(projects.currentScore(project.id()).tracks().getFirst().measures()).hasSize(8);
        assertThat(projects.artifacts(project.id())).hasSize(2)
                .allSatisfy(artifact -> assertThat(Files.isRegularFile(artifact.path())).isTrue());
    }

    private ProjectStore.StoredTask awaitTerminal(String taskId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            ProjectStore.StoredTask task = projects.requireTask(taskId);
            if (task.status().equals(GenerationStatus.COMPLETED.name())
                    || task.status().equals(GenerationStatus.FAILED.name())) {
                return task;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Generation task did not finish");
    }
}
