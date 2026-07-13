package com.musicai.agent.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.musicai.agent.application.GenerationStatus;
import com.musicai.agent.application.port.ProjectStore;
import com.musicai.agent.infrastructure.persistence.entity.ConversationMessageEntity;
import com.musicai.agent.infrastructure.persistence.entity.ExportedArtifactEntity;
import com.musicai.agent.infrastructure.persistence.entity.GenerationTaskEntity;
import com.musicai.agent.infrastructure.persistence.entity.MusicProjectEntity;
import com.musicai.agent.infrastructure.persistence.entity.ProjectVersionEntity;
import com.musicai.agent.infrastructure.persistence.mapper.ConversationMessageMapper;
import com.musicai.agent.infrastructure.persistence.mapper.ExportedArtifactMapper;
import com.musicai.agent.infrastructure.persistence.mapper.GenerationTaskMapper;
import com.musicai.agent.infrastructure.persistence.mapper.MusicProjectMapper;
import com.musicai.agent.infrastructure.persistence.mapper.ProjectVersionMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisProjectStore implements ProjectStore {

    private final MusicProjectMapper projectMapper;
    private final ProjectVersionMapper versionMapper;
    private final GenerationTaskMapper taskMapper;
    private final ConversationMessageMapper messageMapper;
    private final ExportedArtifactMapper artifactMapper;

    public MyBatisProjectStore(MusicProjectMapper projectMapper, ProjectVersionMapper versionMapper,
                               GenerationTaskMapper taskMapper, ConversationMessageMapper messageMapper,
                               ExportedArtifactMapper artifactMapper) {
        this.projectMapper = projectMapper;
        this.versionMapper = versionMapper;
        this.taskMapper = taskMapper;
        this.messageMapper = messageMapper;
        this.artifactMapper = artifactMapper;
    }

    @Override
    @Transactional
    public StoredProject createProject(String title) {
        Instant now = Instant.now();
        MusicProjectEntity entity = new MusicProjectEntity();
        entity.id = UUID.randomUUID().toString();
        entity.title = title;
        entity.status = GenerationStatus.PENDING.name();
        entity.currentVersion = 0;
        entity.createdAt = now;
        entity.updatedAt = now;
        projectMapper.insert(entity);
        return toStored(entity);
    }

    @Override
    public Optional<StoredProject> findProject(String projectId) {
        return Optional.ofNullable(projectMapper.selectById(projectId)).map(MyBatisProjectStore::toStored);
    }

    @Override
    public List<StoredProject> findProjects(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Project list limit must be between 1 and 100");
        }
        return projectMapper.selectList(new QueryWrapper<MusicProjectEntity>()
                        .orderByDesc("updated_at")
                        .last("limit " + limit))
                .stream().map(MyBatisProjectStore::toStored).toList();
    }

    @Override
    @Transactional
    public StoredTask createTask(String projectId, String prompt) {
        requireProject(projectId);
        Instant now = Instant.now();
        GenerationTaskEntity entity = new GenerationTaskEntity();
        entity.id = UUID.randomUUID().toString();
        entity.projectId = projectId;
        entity.status = GenerationStatus.PENDING.name();
        entity.prompt = prompt;
        entity.createdAt = now;
        entity.updatedAt = now;
        taskMapper.insert(entity);
        saveMessage(projectId, "USER", prompt);
        return toStored(entity);
    }

    @Override
    public Optional<StoredTask> findTask(String taskId) {
        return Optional.ofNullable(taskMapper.selectById(taskId)).map(MyBatisProjectStore::toStored);
    }

    @Override
    @Transactional
    public void updateTask(String taskId, String status, String errorMessage) {
        GenerationTaskEntity entity = taskMapper.selectById(taskId);
        if (entity == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        entity.status = status;
        entity.errorMessage = errorMessage;
        entity.updatedAt = Instant.now();
        taskMapper.updateById(entity);

        MusicProjectEntity project = requireProject(entity.projectId);
        project.status = status;
        project.updatedAt = entity.updatedAt;
        projectMapper.updateById(project);
    }

    @Override
    @Transactional
    public int failInterruptedTasks(String errorMessage) {
        List<String> terminal = List.of(GenerationStatus.COMPLETED.name(), GenerationStatus.FAILED.name());
        List<GenerationTaskEntity> interrupted = taskMapper.selectList(new QueryWrapper<GenerationTaskEntity>()
                .notIn("status", terminal));
        for (GenerationTaskEntity task : interrupted) {
            task.status = GenerationStatus.FAILED.name();
            task.errorMessage = errorMessage;
            task.updatedAt = Instant.now();
            taskMapper.updateById(task);
            MusicProjectEntity project = projectMapper.selectById(task.projectId);
            if (project != null) {
                project.status = GenerationStatus.FAILED.name();
                project.updatedAt = task.updatedAt;
                projectMapper.updateById(project);
            }
        }
        return interrupted.size();
    }

    @Override
    @Transactional
    public synchronized int saveVersion(String projectId, String scoreJson) {
        MusicProjectEntity project = requireProject(projectId);
        int versionNumber = project.currentVersion + 1;
        ProjectVersionEntity entity = new ProjectVersionEntity();
        entity.id = UUID.randomUUID().toString();
        entity.projectId = projectId;
        entity.versionNumber = versionNumber;
        entity.scoreJson = scoreJson;
        entity.createdAt = Instant.now();
        versionMapper.insert(entity);

        project.currentVersion = versionNumber;
        project.updatedAt = entity.createdAt;
        projectMapper.updateById(project);
        return versionNumber;
    }

    @Override
    public Optional<StoredVersion> findVersion(String projectId, int versionNumber) {
        ProjectVersionEntity entity = versionMapper.selectOne(new QueryWrapper<ProjectVersionEntity>()
                .eq("project_id", projectId)
                .eq("version_number", versionNumber));
        return Optional.ofNullable(entity).map(MyBatisProjectStore::toStored);
    }

    @Override
    @Transactional
    public void setCurrentVersion(String projectId, int versionNumber) {
        if (findVersion(projectId, versionNumber).isEmpty()) {
            throw new IllegalArgumentException("Project version not found: " + versionNumber);
        }
        MusicProjectEntity project = requireProject(projectId);
        project.currentVersion = versionNumber;
        project.updatedAt = Instant.now();
        projectMapper.updateById(project);
    }

    @Override
    @Transactional
    public void saveMessage(String projectId, String role, String content) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.id = UUID.randomUUID().toString();
        entity.projectId = projectId;
        entity.role = role;
        entity.content = content;
        entity.createdAt = Instant.now();
        messageMapper.insert(entity);
    }

    @Override
    @Transactional
    public StoredArtifact saveArtifact(String projectId, int versionNumber, String type, Path path) {
        ExportedArtifactEntity entity = new ExportedArtifactEntity();
        entity.id = UUID.randomUUID().toString();
        entity.projectId = projectId;
        entity.versionNumber = versionNumber;
        entity.artifactType = type;
        entity.storagePath = path.toAbsolutePath().normalize().toString();
        entity.createdAt = Instant.now();
        artifactMapper.insert(entity);
        return toStored(entity);
    }

    @Override
    public Optional<StoredArtifact> findArtifact(String artifactId) {
        return Optional.ofNullable(artifactMapper.selectById(artifactId)).map(MyBatisProjectStore::toStored);
    }

    @Override
    public List<StoredArtifact> findArtifacts(String projectId) {
        return artifactMapper.selectList(new QueryWrapper<ExportedArtifactEntity>()
                        .eq("project_id", projectId)
                        .orderByAsc("created_at"))
                .stream().map(MyBatisProjectStore::toStored).toList();
    }

    private MusicProjectEntity requireProject(String projectId) {
        MusicProjectEntity project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        return project;
    }

    private static StoredProject toStored(MusicProjectEntity entity) {
        return new StoredProject(entity.id, entity.title, entity.status, entity.currentVersion,
                entity.createdAt, entity.updatedAt);
    }

    private static StoredTask toStored(GenerationTaskEntity entity) {
        return new StoredTask(entity.id, entity.projectId, entity.status, entity.prompt, entity.errorMessage,
                entity.createdAt, entity.updatedAt);
    }

    private static StoredVersion toStored(ProjectVersionEntity entity) {
        return new StoredVersion(entity.id, entity.projectId, entity.versionNumber, entity.scoreJson,
                entity.createdAt);
    }

    private static StoredArtifact toStored(ExportedArtifactEntity entity) {
        return new StoredArtifact(entity.id, entity.projectId, entity.versionNumber, entity.artifactType,
                Path.of(entity.storagePath), entity.createdAt);
    }
}
