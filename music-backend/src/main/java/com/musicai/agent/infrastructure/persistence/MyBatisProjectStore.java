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

/**
 * 基于 MyBatis-Plus 实现项目、任务、版本、消息及导出产物的持久化存储。
 */
@Repository
public class MyBatisProjectStore implements ProjectStore {

    private final MusicProjectMapper projectMapper;
    private final ProjectVersionMapper versionMapper;
    private final GenerationTaskMapper taskMapper;
    private final ConversationMessageMapper messageMapper;
    private final ExportedArtifactMapper artifactMapper;

    /**
     * 创建 MyBatis 项目存储。
     *
     * @param projectMapper 项目映射器
     * @param versionMapper 版本映射器
     * @param taskMapper 任务映射器
     * @param messageMapper 会话消息映射器
     * @param artifactMapper 导出产物映射器
     */
    public MyBatisProjectStore(MusicProjectMapper projectMapper, ProjectVersionMapper versionMapper,
                               GenerationTaskMapper taskMapper, ConversationMessageMapper messageMapper,
                               ExportedArtifactMapper artifactMapper) {
        this.projectMapper = projectMapper;
        this.versionMapper = versionMapper;
        this.taskMapper = taskMapper;
        this.messageMapper = messageMapper;
        this.artifactMapper = artifactMapper;
    }

    /**
     * 创建初始状态为待处理的项目。
     *
     * @param title 项目标题
     * @return 已持久化的项目
     */
    @Override
    @Transactional
    public StoredProject createProject(String title) {
        // 项目初始化字段必须作为一个事务整体写入，避免对外暴露不完整记录。
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

    /**
     * 按主键查找项目。
     *
     * @param projectId 项目主键
     * @return 项目存在时返回其持久化视图
     */
    @Override
    public Optional<StoredProject> findProject(String projectId) {
        return Optional.ofNullable(projectMapper.selectById(projectId)).map(MyBatisProjectStore::toStored);
    }

    /**
     * 按最近更新时间倒序查询项目。
     *
     * @param limit 返回数量上限，取值为 1 至 100
     * @return 项目列表
     */
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

    /**
     * 为项目创建生成任务并记录对应的用户消息。
     *
     * @param projectId 项目主键
     * @param prompt 用户生成提示词
     * @return 已持久化的任务
     */
    @Override
    @Transactional
    public StoredTask createTask(String projectId, String prompt) {
        // 任务与用户消息必须同成同败，避免出现无法追溯输入来源的生成任务。
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

    /**
     * 按主键查找生成任务。
     *
     * @param taskId 任务主键
     * @return 任务存在时返回其持久化视图
     */
    @Override
    public Optional<StoredTask> findTask(String taskId) {
        return Optional.ofNullable(taskMapper.selectById(taskId)).map(MyBatisProjectStore::toStored);
    }

    /**
     * 更新任务状态，并同步所属项目的状态与更新时间。
     *
     * @param taskId 任务主键
     * @param status 新状态
     * @param errorMessage 失败信息
     */
    @Override
    @Transactional
    public void updateTask(String taskId, String status, String errorMessage) {
        // 任务与项目状态用于同一业务进度展示，事务保证二者不会因部分写入而相互矛盾。
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

    /**
     * 将进程重启前遗留的所有非终态任务及其项目标记为失败。
     *
     * @param errorMessage 写入任务的中断原因
     * @return 被恢复为失败状态的任务数量
     */
    @Override
    @Transactional
    public int failInterruptedTasks(String errorMessage) {
        // 批量恢复涉及任务和项目两张表，事务避免恢复中断后留下部分不一致状态。
        // 进程重启后内存中的执行线程已经丢失，非终态任务不能继续伪装成运行中。
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

    /**
     * 保存新的乐谱版本并将其设为项目当前版本。
     *
     * @param projectId 项目主键
     * @param scoreJson 序列化后的乐谱数据
     * @return 新分配的项目内版本号
     */
    @Override
    @Transactional
    public synchronized int saveVersion(String projectId, String scoreJson) {
        // 版本记录与项目当前版本指针必须在同一事务提交，避免指向不存在的版本。
        // synchronized 只在单 JVM 内串行分配版本号；多实例竞争由数据库唯一约束拒绝，
        // 当前方法不会在冲突后重新读取版本号并重试。
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

    /**
     * 查询项目的指定版本。
     *
     * @param projectId 项目主键
     * @param versionNumber 项目内版本号
     * @return 版本存在时返回其持久化视图
     */
    @Override
    public Optional<StoredVersion> findVersion(String projectId, int versionNumber) {
        ProjectVersionEntity entity = versionMapper.selectOne(new QueryWrapper<ProjectVersionEntity>()
                .eq("project_id", projectId)
                .eq("version_number", versionNumber));
        return Optional.ofNullable(entity).map(MyBatisProjectStore::toStored);
    }

    /**
     * 将项目的当前版本切换为已存在的指定版本。
     *
     * @param projectId 项目主键
     * @param versionNumber 目标版本号
     */
    @Override
    @Transactional
    public void setCurrentVersion(String projectId, int versionNumber) {
        // 版本存在性校验和项目指针更新置于同一事务，避免并发变更间观察到无效指针。
        if (findVersion(projectId, versionNumber).isEmpty()) {
            throw new IllegalArgumentException("Project version not found: " + versionNumber);
        }
        MusicProjectEntity project = requireProject(projectId);
        project.currentVersion = versionNumber;
        project.updatedAt = Instant.now();
        projectMapper.updateById(project);
    }

    /**
     * 保存一条项目会话消息。
     *
     * @param projectId 项目主键
     * @param role 消息角色
     * @param content 消息正文
     */
    @Override
    @Transactional
    public void saveMessage(String projectId, String role, String content) {
        // 独立调用时也需要事务边界；被事务方法调用时则参与外层业务原子性。
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.id = UUID.randomUUID().toString();
        entity.projectId = projectId;
        entity.role = role;
        entity.content = content;
        entity.createdAt = Instant.now();
        messageMapper.insert(entity);
    }

    /**
     * 保存项目版本的导出产物元数据。
     *
     * @param projectId 项目主键
     * @param versionNumber 项目版本号
     * @param type 产物类型
     * @param path 产物文件路径
     * @return 已持久化的产物
     */
    @Override
    @Transactional
    public StoredArtifact saveArtifact(String projectId, int versionNumber, String type, Path path) {
        // 产物元数据写入使用事务，以便与调用方可能存在的外层持久化流程共同提交或回滚。
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

    /**
     * 按主键查找导出产物。
     *
     * @param artifactId 产物主键
     * @return 产物存在时返回其持久化视图
     */
    @Override
    public Optional<StoredArtifact> findArtifact(String artifactId) {
        return Optional.ofNullable(artifactMapper.selectById(artifactId)).map(MyBatisProjectStore::toStored);
    }

    /**
     * 按创建时间升序查询项目的全部导出产物。
     *
     * @param projectId 项目主键
     * @return 导出产物列表
     */
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
