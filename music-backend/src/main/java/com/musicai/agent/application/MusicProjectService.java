package com.musicai.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicai.agent.agent.RequirementParser;
import com.musicai.agent.application.port.ProjectEventPublisher;
import com.musicai.agent.application.port.ProjectStore;
import com.musicai.agent.domain.Measure;
import com.musicai.agent.domain.Score;
import com.musicai.agent.domain.Track;
import com.musicai.agent.export.MidiExporter;
import com.musicai.agent.export.MusicXmlExporter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class MusicProjectService {

    private final ProjectStore store;
    private final RequirementParser requirementParser;
    private final ProjectEventPublisher events;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;
    private final Path exportDirectory;
    private final GuitarRiffGenerator generator = new GuitarRiffGenerator();
    private final MidiExporter midiExporter = new MidiExporter();
    private final MusicXmlExporter musicXmlExporter = new MusicXmlExporter();

    public MusicProjectService(ProjectStore store, RequirementParser requirementParser,
                               ProjectEventPublisher events,
                               @Qualifier("musicTaskExecutor") TaskExecutor taskExecutor,
                               ObjectMapper objectMapper,
                               @Value("${music-ai.export-directory}") String exportDirectory) {
        this.store = store;
        this.requirementParser = requirementParser;
        this.events = events;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
        this.exportDirectory = Path.of(exportDirectory).toAbsolutePath().normalize();
    }

    public ProjectStore.StoredProject createProject(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Project title must not be blank");
        }
        return store.createProject(title.trim());
    }

    public ProjectStore.StoredTask generate(String projectId, String prompt) {
        requireProject(projectId);
        ProjectStore.StoredTask task = store.createTask(projectId, prompt);
        taskExecutor.execute(() -> runGeneration(task));
        return task;
    }

    public ProjectStore.StoredTask rewrite(String projectId, int fromMeasure, int toMeasure, String prompt) {
        if (fromMeasure < 1 || toMeasure < fromMeasure) {
            throw new IllegalArgumentException("Invalid rewrite measure range");
        }
        requireProject(projectId);
        ProjectStore.StoredTask task = store.createTask(projectId, prompt);
        taskExecutor.execute(() -> runRewrite(task, fromMeasure, toMeasure));
        return task;
    }

    public ProjectStore.StoredProject requireProject(String projectId) {
        return store.findProject(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
    }

    public List<ProjectStore.StoredProject> projects(int limit) {
        return store.findProjects(limit);
    }

    public ProjectStore.StoredTask requireTask(String taskId) {
        return store.findTask(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
    }

    public Score currentScore(String projectId) {
        ProjectStore.StoredProject project = requireProject(projectId);
        if (project.currentVersion() == 0) {
            throw new IllegalStateException("Project has no generated score");
        }
        return readScore(store.findVersion(projectId, project.currentVersion())
                .orElseThrow(() -> new IllegalStateException("Current project version is missing")));
    }

    public ValidationSummary validate(String projectId) {
        Score score = currentScore(projectId);
        score.validate();
        return new ValidationSummary(true, score.tracks().size(), score.tracks().getFirst().measures().size());
    }

    public ProjectStore.StoredProject rollback(String projectId, int versionNumber) {
        requireProject(projectId);
        store.setCurrentVersion(projectId, versionNumber);
        return requireProject(projectId);
    }

    public List<ProjectStore.StoredArtifact> artifacts(String projectId) {
        requireProject(projectId);
        return store.findArtifacts(projectId);
    }

    public ProjectStore.StoredArtifact requireArtifact(String artifactId) {
        return store.findArtifact(artifactId)
                .orElseThrow(() -> new ResourceNotFoundException("Artifact not found: " + artifactId));
    }

    private void runGeneration(ProjectStore.StoredTask task) {
        try {
            transition(task, GenerationStatus.PARSING_REQUIREMENTS, "AGENT_MESSAGE");
            CreationConstraints constraints = requirementParser.parse(task.prompt());
            events.publish(task.projectId(), "PLAN_CREATED", constraints);
            transition(task, GenerationStatus.GENERATING, "SECTION_GENERATING");
            Score score = generator.generate(constraints);
            finishScore(task, score);
        } catch (Exception exception) {
            fail(task, exception);
        }
    }

    private void runRewrite(ProjectStore.StoredTask task, int fromMeasure, int toMeasure) {
        try {
            transition(task, GenerationStatus.PARSING_REQUIREMENTS, "AGENT_MESSAGE");
            Score original = currentScore(task.projectId());
            if (toMeasure > original.tracks().getFirst().measures().size()) {
                throw new IllegalArgumentException("Rewrite range exceeds score measure count");
            }
            CreationConstraints parsed = requirementParser.parse(task.prompt());
            CreationConstraints constraints = new CreationConstraints(original.tracks().getFirst().measures().size(),
                    parsed.tempo(), parsed.keySignature(), parsed.style(), original.tracks().getFirst().tuning(),
                    original.timeSignature(), parsed.mood(), parsed.rhythmicFeel(), parsed.complexity(),
                    parsed.variationSeed());
            Score replacement = generator.generate(constraints);
            transition(task, GenerationStatus.GENERATING, "SECTION_GENERATING");
            Score rewritten = replaceMeasures(original, replacement, fromMeasure, toMeasure);
            finishScore(task, rewritten);
        } catch (Exception exception) {
            fail(task, exception);
        }
    }

    static Score replaceMeasures(Score original, Score replacement, int fromMeasure, int toMeasure) {
        List<Measure> measures = new ArrayList<>(original.tracks().getFirst().measures());
        for (int number = fromMeasure; number <= toMeasure; number++) {
            measures.set(number - 1, replacement.tracks().getFirst().measures().get(number - 1));
        }
        Track originalTrack = original.tracks().getFirst();
        Score score = new Score(original.title(), replacement.tempo(), replacement.keySignature(),
                original.timeSignature(), List.of(new Track(originalTrack.name(), originalTrack.tuning(),
                measures)));
        score.validate();
        return score;
    }

    private void finishScore(ProjectStore.StoredTask task, Score score) throws IOException {
        transition(task, GenerationStatus.VALIDATING, "VALIDATION_COMPLETED");
        score.validate();
        int version = store.saveVersion(task.projectId(), writeScore(score));
        transition(task, GenerationStatus.EXPORTING, "EXPORTING");
        Path projectDirectory = exportDirectory.resolve(task.projectId()).resolve("v" + version).normalize();
        if (!projectDirectory.startsWith(exportDirectory)) {
            throw new IllegalStateException("Invalid export directory");
        }
        Path midi = midiExporter.export(score, projectDirectory.resolve("score.mid"));
        Path musicXml = musicXmlExporter.export(score, projectDirectory.resolve("score.musicxml"));
        var midiArtifact = store.saveArtifact(task.projectId(), version, "MIDI", midi);
        var xmlArtifact = store.saveArtifact(task.projectId(), version, "MUSICXML", musicXml);
        store.saveMessage(task.projectId(), "ASSISTANT", "Generated version " + version);
        store.updateTask(task.id(), GenerationStatus.COMPLETED.name(), null);
        events.publish(task.projectId(), "EXPORT_COMPLETED", List.of(midiArtifact, xmlArtifact));
    }

    private void transition(ProjectStore.StoredTask task, GenerationStatus status, String eventType) {
        store.updateTask(task.id(), status.name(), null);
        events.publish(task.projectId(), eventType, status.name());
    }

    private void fail(ProjectStore.StoredTask task, Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        store.updateTask(task.id(), GenerationStatus.FAILED.name(), message.substring(0, Math.min(1000, message.length())));
        events.publish(task.projectId(), "TASK_FAILED", message);
    }

    private String writeScore(Score score) {
        try {
            return objectMapper.writeValueAsString(score);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize score", exception);
        }
    }

    private Score readScore(ProjectStore.StoredVersion version) {
        try {
            return objectMapper.readValue(version.scoreJson(), Score.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not deserialize score version " + version.versionNumber(), exception);
        }
    }

    public record ValidationSummary(boolean valid, int tracks, int measures) {
    }
}
