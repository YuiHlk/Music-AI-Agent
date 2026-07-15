package com.musicai.agent.application.port;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 持久化项目、异步任务、不可变乐谱版本、会话消息和导出物的应用端口。
 */
public interface ProjectStore {

    /**
     * 保存一个空项目。
     *
     * @param title 项目标题
     * @return 新建项目
     */
    StoredProject createProject(String title);

    /**
     * 按主键查询项目。
     *
     * @param projectId 项目标识
     * @return 项目，不存在时为空
     */
    Optional<StoredProject> findProject(String projectId);

    /**
     * 查询最近更新的项目。
     *
     * @param limit 返回数量上限
     * @return 按更新时间倒序的项目
     */
    List<StoredProject> findProjects(int limit);

    /**
     * 创建待执行任务；实现可在同一事务中持久化对应用户消息。
     * @param projectId 项目标识
     * @param prompt 原始创作要求
     * @return 新建任务
     */
    StoredTask createTask(String projectId, String prompt);

    /**
     * 按主键查询异步任务。
     *
     * @param taskId 任务标识
     * @return 任务，不存在时为空
     */
    Optional<StoredTask> findTask(String taskId);

    /**
     * 更新任务状态，并保持所属项目的对外状态同步。
     * @param taskId 任务标识
     * @param status 新状态
     * @param errorMessage 可空的失败原因
     */
    void updateTask(String taskId, String status, String errorMessage);

    /**
     * 将进程重启后无法继续执行的非终态任务标记为失败。
     * @param errorMessage 统一失败原因
     * @return 被更新的任务数
     */
    int failInterruptedTasks(String errorMessage);

    /**
     * 保存不可变乐谱 JSON，并把项目当前版本指针移动到新版本。
     * @param projectId 项目标识
     * @param scoreJson 乐谱持久化 JSON
     * @return 分配的递增版本号
     */
    int saveVersion(String projectId, String scoreJson);

    /**
     * 查询指定项目的不可变版本快照。
     *
     * @param projectId 项目标识
     * @param versionNumber 版本号
     * @return 指定版本，不存在时为空
     */
    Optional<StoredVersion> findVersion(String projectId, int versionNumber);

    /**
     * 移动当前版本指针而不删除任何历史版本。
     * @param projectId 项目标识
     * @param versionNumber 已存在的目标版本号
     */
    void setCurrentVersion(String projectId, int versionNumber);

    /**
     * 记录一条项目对话消息。
     *
     * @param projectId 项目标识
     * @param role 消息角色
     * @param content 消息内容
     */
    void saveMessage(String projectId, String role, String content);

    /**
     * 记录服务器本地导出文件。
     * @param projectId 项目标识
     * @param versionNumber 所属乐谱版本
     * @param type 导出格式
     * @param path 服务器本地文件路径
     * @return 已保存导出物
     */
    StoredArtifact saveArtifact(String projectId, int versionNumber, String type, Path path);

    /**
     * 按主键查询导出物。
     *
     * @param artifactId 导出物标识
     * @return 导出物，不存在时为空
     */
    Optional<StoredArtifact> findArtifact(String artifactId);

    /**
     * 查询项目所有历史版本关联的导出物。
     *
     * @param projectId 项目标识
     * @return 项目全部版本的导出物
     */
    List<StoredArtifact> findArtifacts(String projectId);

    /**
     * 项目持久化视图。
     * @param id 项目标识
     * @param title 标题
     * @param status 当前任务状态
     * @param currentVersion 当前版本号，尚无版本时为零
     * @param createdAt 创建时间
     * @param updatedAt 最后更新时间
     */
    record StoredProject(String id, String title, String status, int currentVersion,
                         Instant createdAt, Instant updatedAt) {
    }

    /**
     * 异步生成任务持久化视图。
     * @param id 任务标识
     * @param projectId 所属项目标识
     * @param status 当前状态
     * @param prompt 原始要求
     * @param errorMessage 可空的失败原因
     * @param createdAt 创建时间
     * @param updatedAt 最后更新时间
     */
    record StoredTask(String id, String projectId, String status, String prompt, String errorMessage,
                      Instant createdAt, Instant updatedAt) {
    }

    /**
     * 不可变乐谱版本持久化视图。
     * @param id 版本记录标识
     * @param projectId 所属项目标识
     * @param versionNumber 项目内递增版本号
     * @param scoreJson 乐谱 JSON
     * @param createdAt 创建时间
     */
    record StoredVersion(String id, String projectId, int versionNumber, String scoreJson, Instant createdAt) {
    }

    /**
     * 导出文件持久化视图。
     * @param id 导出物标识
     * @param projectId 所属项目标识
     * @param versionNumber 所属版本号
     * @param type 导出格式
     * @param path 服务器本地路径
     * @param createdAt 创建时间
     */
    record StoredArtifact(String id, String projectId, int versionNumber, String type, Path path,
                          Instant createdAt) {
    }
}
