package com.musicai.agent.application.port;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProjectStore {

    StoredProject createProject(String title);

    Optional<StoredProject> findProject(String projectId);

    List<StoredProject> findProjects(int limit);

    StoredTask createTask(String projectId, String prompt);

    Optional<StoredTask> findTask(String taskId);

    void updateTask(String taskId, String status, String errorMessage);

    int failInterruptedTasks(String errorMessage);

    int saveVersion(String projectId, String scoreJson);

    Optional<StoredVersion> findVersion(String projectId, int versionNumber);

    void setCurrentVersion(String projectId, int versionNumber);

    void saveMessage(String projectId, String role, String content);

    StoredArtifact saveArtifact(String projectId, int versionNumber, String type, Path path);

    Optional<StoredArtifact> findArtifact(String artifactId);

    List<StoredArtifact> findArtifacts(String projectId);

    record StoredProject(String id, String title, String status, int currentVersion,
                         Instant createdAt, Instant updatedAt) {
    }

    record StoredTask(String id, String projectId, String status, String prompt, String errorMessage,
                      Instant createdAt, Instant updatedAt) {
    }

    record StoredVersion(String id, String projectId, int versionNumber, String scoreJson, Instant createdAt) {
    }

    record StoredArtifact(String id, String projectId, int versionNumber, String type, Path path,
                          Instant createdAt) {
    }
}
