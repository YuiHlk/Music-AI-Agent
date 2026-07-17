package com.musicai.agent.agent;

import com.musicai.agent.application.GenerationStatus;
import com.musicai.agent.application.MusicProjectService;
import com.musicai.agent.application.port.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("llm")
@EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
class LlmRequirementParserLiveTest {

    @Autowired
    RequirementParser parser;

    @Autowired
    MusicProjectService projects;

    @Test
    void parsesTheMvpRequestWithConfiguredModel() {
        var constraints = parser.parse("生成一段 8 小节、120 BPM、E 小调、标准调弦的摇滚吉他 Riff");

        assertThat(constraints.measures()).isEqualTo(8);
        assertThat(constraints.tempo()).isEqualTo(120);
        assertThat(constraints.keySignature()).containsIgnoringCase("E");
        assertThat(constraints.style()).containsIgnoringCase("rock");
    }

    @Test
    void runsNaturalLanguageToExportedFilesWithConfiguredModel() throws Exception {
        var project = projects.createProject("LLM end-to-end riff");
        var task = projects.generate(project.id(),
                "生成一段 8 小节、120 BPM、E 小调、标准调弦的摇滚吉他 Riff");

        ProjectStore.StoredTask completed = awaitTerminal(task.id());

        assertThat(completed.status()).isEqualTo(GenerationStatus.COMPLETED.name());
        assertThat(projects.currentScore(project.id()).tracks().getFirst().measures()).hasSize(8);
        assertThat(projects.artifacts(project.id())).extracting(ProjectStore.StoredArtifact::type)
                .containsExactlyInAnyOrder("MIDI", "MUSICXML");
    }

    private ProjectStore.StoredTask awaitTerminal(String taskId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            ProjectStore.StoredTask task = projects.requireTask(taskId);
            if (task.status().equals(GenerationStatus.COMPLETED.name())
                    || task.status().equals(GenerationStatus.FAILED.name())) {
                return task;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Configured LLM generation task did not finish");
    }
}
