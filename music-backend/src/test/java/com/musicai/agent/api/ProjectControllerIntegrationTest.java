package com.musicai.agent.api;

import com.musicai.agent.application.GenerationStatus;
import com.musicai.agent.application.MusicProjectService;
import com.musicai.agent.application.port.ProjectStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MusicProjectService projects;

    @Test
    void listsProjectsAndReturnsAStableScorePreviewDto() throws Exception {
        ProjectStore.StoredProject project = projects.createProject("API preview");
        ProjectStore.StoredTask task = projects.generate(project.id(),
                "Generate an 8 measure, 120 BPM, E minor, standard tuning rock guitar riff");
        awaitCompletion(task.id());

        mvc.perform(get("/api/projects").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(project.id())));

        mvc.perform(get("/api/projects/{projectId}/score", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tempo", is(120)))
                .andExpect(jsonPath("$.timeSignature", is("4/4")))
                .andExpect(jsonPath("$.tracks[0].measures.length()", is(8)))
                .andExpect(jsonPath("$.tracks[0].measures[0].events[0].type", is("NOTE")));
    }

    @Test
    void returnsStructuredErrorsForMissingResourcesAndInvalidLimits() throws Exception {
        mvc.perform(get("/api/projects/{projectId}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")))
                .andExpect(jsonPath("$.path", is("/api/projects/missing")));

        mvc.perform(get("/api/projects").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
    }

    private void awaitCompletion(String taskId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            ProjectStore.StoredTask task = projects.requireTask(taskId);
            if (task.status().equals(GenerationStatus.COMPLETED.name())) {
                return;
            }
            if (task.status().equals(GenerationStatus.FAILED.name())) {
                throw new AssertionError("Generation failed: " + task.errorMessage());
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Generation task did not finish");
    }
}
