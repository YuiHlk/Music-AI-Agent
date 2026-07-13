package com.musicai.agent.agent;

import com.musicai.agent.application.MusicProjectService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class MusicCreationTools {

    private final MusicProjectService projects;

    public MusicCreationTools(MusicProjectService projects) {
        this.projects = projects;
    }

    @Tool("Create a persistent music project before generating music. The title is user-visible.")
    public ProjectResult createMusicProject(
            @P("A concise project title; must not contain a database ID or file path") String title) {
        var project = projects.createProject(title);
        return new ProjectResult(project.id(), project.title(), project.currentVersion(), project.status());
    }

    @Tool("Create a concise song plan from a bounded guitar riff request without writing files.")
    public SongPlanResult planSongStructure(
            @P("Number of measures between 1 and 128") int measures,
            @P("Musical style such as rock or metal") String style) {
        if (measures < 1 || measures > 128) {
            throw new IllegalArgumentException("Measure count must be between 1 and 128");
        }
        return new SongPlanResult(measures, style, "One guitar track; alternating two-measure riff motifs");
    }

    @Tool("Start asynchronous guitar riff generation for an existing project. Returns a task to poll.")
    public TaskResult generateGuitarRiff(
            @P("Existing project ID returned by createMusicProject") String projectId,
            @P("Natural-language creation constraints") String request) {
        var task = projects.generate(projectId, request);
        return new TaskResult(task.id(), task.projectId(), task.status());
    }

    @Tool("Rewrite only an inclusive range of measures and create a new immutable project version.")
    public TaskResult rewriteMeasures(
            @P("Existing project ID") String projectId,
            @P("First measure number, starting from 1") int fromMeasure,
            @P("Last measure number, inclusive") int toMeasure,
            @P("Natural-language replacement constraints") String request) {
        var task = projects.rewrite(projectId, fromMeasure, toMeasure, request);
        return new TaskResult(task.id(), task.projectId(), task.status());
    }

    @Tool("Validate the current score using deterministic duration and guitar playability rules.")
    public ValidationResult validateScore(@P("Existing project ID") String projectId) {
        var validation = projects.validate(projectId);
        return new ValidationResult(validation.valid(), validation.tracks(), validation.measures());
    }

    @Tool("List already exported MIDI and MusicXML artifacts for the current project version.")
    public ExportResult exportProject(@P("Existing project ID") String projectId) {
        var artifacts = projects.artifacts(projectId);
        return new ExportResult(artifacts.size(), artifacts.stream().map(artifact -> artifact.type()).toList());
    }

    public record ProjectResult(String projectId, String title, int currentVersion, String status) {
    }

    public record SongPlanResult(int measures, String style, String structure) {
    }

    public record TaskResult(String taskId, String projectId, String status) {
    }

    public record ValidationResult(boolean valid, int tracks, int measures) {
    }

    public record ExportResult(int artifactCount, java.util.List<String> types) {
    }
}
