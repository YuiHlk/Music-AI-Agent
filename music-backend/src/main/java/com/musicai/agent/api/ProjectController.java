package com.musicai.agent.api;

import com.musicai.agent.agent.MusicCreatorAgent;
import com.musicai.agent.application.MusicProjectService;
import com.musicai.agent.application.port.ProjectStore;
import com.musicai.agent.infrastructure.GuitarProConnector;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.FileSystemResource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ProjectController {

    private final MusicProjectService projects;
    private final ProjectEventBroker events;
    private final ObjectProvider<MusicCreatorAgent> musicCreatorAgent;
    private final GuitarProConnector guitarProConnector;

    public ProjectController(MusicProjectService projects, ProjectEventBroker events,
                             ObjectProvider<MusicCreatorAgent> musicCreatorAgent,
                             GuitarProConnector guitarProConnector) {
        this.projects = projects;
        this.events = events;
        this.musicCreatorAgent = musicCreatorAgent;
        this.guitarProConnector = guitarProConnector;
    }

    @PostMapping("/projects")
    ResponseEntity<ProjectStore.StoredProject> createProject(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(201).body(projects.createProject(request.title()));
    }

    @GetMapping("/projects/{projectId}")
    ProjectStore.StoredProject getProject(@PathVariable String projectId) {
        return projects.requireProject(projectId);
    }

    @GetMapping("/projects")
    List<ProjectStore.StoredProject> listProjects(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return projects.projects(limit);
    }

    @GetMapping("/projects/{projectId}/score")
    ScorePreviewResponse score(@PathVariable String projectId) {
        return ScorePreviewResponse.from(projects.currentScore(projectId));
    }

    @PostMapping("/projects/{projectId}/generate")
    ResponseEntity<ProjectStore.StoredTask> generate(@PathVariable String projectId,
                                                      @Valid @RequestBody GenerateRequest request) {
        return ResponseEntity.accepted().body(projects.generate(projectId, request.prompt()));
    }

    @PostMapping("/projects/{projectId}/chat")
    AgentChatResponse chat(@PathVariable String projectId, @Valid @RequestBody GenerateRequest request) {
        projects.requireProject(projectId);
        MusicCreatorAgent agent = musicCreatorAgent.getIfAvailable();
        if (agent == null) {
            throw new IllegalStateException("Agent chat requires the deepseek profile");
        }
        return new AgentChatResponse(agent.chat(projectId, request.prompt()));
    }

    @PostMapping("/projects/{projectId}/rewrite")
    ResponseEntity<ProjectStore.StoredTask> rewrite(@PathVariable String projectId,
                                                     @Valid @RequestBody RewriteRequest request) {
        return ResponseEntity.accepted().body(projects.rewrite(projectId, request.fromMeasure(),
                request.toMeasure(), request.prompt()));
    }

    @PostMapping("/projects/{projectId}/validate")
    MusicProjectService.ValidationSummary validate(@PathVariable String projectId) {
        return projects.validate(projectId);
    }

    @PostMapping("/projects/{projectId}/versions/{versionNumber}/rollback")
    ProjectStore.StoredProject rollback(@PathVariable String projectId, @PathVariable @Min(1) int versionNumber) {
        return projects.rollback(projectId, versionNumber);
    }

    @GetMapping(path = "/projects/{projectId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable String projectId) {
        projects.requireProject(projectId);
        return events.subscribe(projectId);
    }

    @GetMapping("/projects/{projectId}/artifacts")
    List<ProjectStore.StoredArtifact> artifacts(@PathVariable String projectId) {
        return projects.artifacts(projectId);
    }

    @GetMapping("/tasks/{taskId}")
    ProjectStore.StoredTask task(@PathVariable String taskId) {
        return projects.requireTask(taskId);
    }

    @GetMapping("/artifacts/{artifactId}/download")
    ResponseEntity<Resource> download(@PathVariable String artifactId) {
        ProjectStore.StoredArtifact artifact = projects.requireArtifact(artifactId);
        if (!Files.isRegularFile(artifact.path())) {
            throw new IllegalStateException("Artifact file is missing");
        }
        Resource resource = new FileSystemResource(artifact.path());
        String filename = artifact.path().getFileName().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/artifacts/{artifactId}/open-in-guitar-pro")
    ResponseEntity<Void> openInGuitarPro(@PathVariable String artifactId) {
        guitarProConnector.open(projects.requireArtifact(artifactId).path());
        return ResponseEntity.accepted().build();
    }

    public record CreateProjectRequest(@NotBlank @Size(max = 200) String title) {
    }

    public record GenerateRequest(@NotBlank @Size(max = 4000) String prompt) {
    }

    public record RewriteRequest(@Min(1) @Max(128) int fromMeasure,
                                 @Min(1) @Max(128) int toMeasure,
                                 @NotBlank @Size(max = 4000) String prompt) {
    }

    public record AgentChatResponse(String message) {
    }
}
