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

/** 提供项目、异步生成、版本、SSE、导出物和可选 Agent chat 的 REST API。 */
@RestController
@RequestMapping("/api")
public class ProjectController {

    private final MusicProjectService projects;
    private final ProjectEventBroker events;
    private final ObjectProvider<MusicCreatorAgent> musicCreatorAgent;
    private final GuitarProConnector guitarProConnector;

    /**
     * 创建 REST 控制器并注入所有应用级协作者。
     *
     * @param projects 项目应用服务
     * @param events SSE 事件代理
     * @param musicCreatorAgent 可选的模型驱动 Agent
     * @param guitarProConnector 服务端桌面连接器
     */
    public ProjectController(MusicProjectService projects, ProjectEventBroker events,
                             ObjectProvider<MusicCreatorAgent> musicCreatorAgent,
                             GuitarProConnector guitarProConnector) {
        this.projects = projects;
        this.events = events;
        this.musicCreatorAgent = musicCreatorAgent;
        this.guitarProConnector = guitarProConnector;
    }

    /**
     * @param request 项目创建请求
     * @return HTTP 201 与新项目
     */
    @PostMapping("/projects")
    ResponseEntity<ProjectStore.StoredProject> createProject(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(201).body(projects.createProject(request.title()));
    }

    /**
     * 根据项目 ID 查询项目元数据；完整乐谱保存在当前版本对应的版本快照中。
     *
     * @param projectId 项目标识
     * @return 项目基本信息及当前乐谱版本号
     */
    @GetMapping("/projects/{projectId}")
    ProjectStore.StoredProject getProject(@PathVariable String projectId) {
        return projects.requireProject(projectId);
    }

    /**
     * @param limit 最多返回的项目数
     * @return 按更新时间倒序排列的项目
     */
    @GetMapping("/projects")
    List<ProjectStore.StoredProject> listProjects(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return projects.projects(limit);
    }

    /**
     * @param projectId 项目标识
     * @return 当前版本的前端乐谱预览
     */
    @GetMapping("/projects/{projectId}/score")
    ScorePreviewResponse score(@PathVariable String projectId) {
        return ScorePreviewResponse.from(projects.currentScore(projectId));
    }

    /**
     * 创建持久化生成任务后立即返回，不等待模型解析、乐谱生成和文件导出完成。
     *
     * @param projectId 项目标识
     * @param request 生成要求
     * @return HTTP 202 与需要轮询的异步任务
     */
    @PostMapping("/projects/{projectId}/generate")
    ResponseEntity<ProjectStore.StoredTask> generate(@PathVariable String projectId,
                                                      @Valid @RequestBody GenerateRequest request) {
        return ResponseEntity.accepted().body(projects.generate(projectId, request.prompt()));
    }

    /**
     * 将对话交给可选的模型驱动 Agent；未启用 {@code llm} Profile 时返回冲突错误。
     * @param projectId 项目标识
     * @param request 用户消息
     * @return Agent 的文本回复
     */
    @PostMapping("/projects/{projectId}/chat")
    AgentChatResponse chat(@PathVariable String projectId, @Valid @RequestBody GenerateRequest request) {
        projects.requireProject(projectId);
        MusicCreatorAgent agent = musicCreatorAgent.getIfAvailable();
        if (agent == null) {
            throw new IllegalStateException("Agent chat requires the llm profile");
        }
        return new AgentChatResponse(agent.chat(projectId, request.prompt()));
    }

    /**
     * @param projectId 项目标识
     * @param request 从 1 开始、包含首尾小节的改写要求
     * @return HTTP 202 与异步任务
     */
    @PostMapping("/projects/{projectId}/rewrite")
    ResponseEntity<ProjectStore.StoredTask> rewrite(@PathVariable String projectId,
                                                     @Valid @RequestBody RewriteRequest request) {
        return ResponseEntity.accepted().body(projects.rewrite(projectId, request.fromMeasure(),
                request.toMeasure(), request.prompt()));
    }

    /**
     * @param projectId 项目标识
     * @return 当前乐谱的结构与可演奏性验证摘要
     */
    @PostMapping("/projects/{projectId}/validate")
    MusicProjectService.ValidationSummary validate(@PathVariable String projectId) {
        return projects.validate(projectId);
    }

    /**
     * 移动当前版本指针，不删除目标版本之后的历史记录。
     * @param projectId 项目标识
     * @param versionNumber 已存在版本号
     * @return 更新后的项目
     */
    @PostMapping("/projects/{projectId}/versions/{versionNumber}/rollback")
    ProjectStore.StoredProject rollback(@PathVariable String projectId, @PathVariable @Min(1) int versionNumber) {
        return projects.rollback(projectId, versionNumber);
    }

    /**
     * @param projectId 项目标识
     * @return 进程内、无历史事件重放保证的 SSE 长连接
     */
    @GetMapping(path = "/projects/{projectId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable String projectId) {
        projects.requireProject(projectId);
        return events.subscribe(projectId);
    }

    /**
     * @param projectId 项目标识
     * @return 属于该项目全部版本的导出物元数据
     */
    @GetMapping("/projects/{projectId}/artifacts")
    List<ProjectStore.StoredArtifact> artifacts(@PathVariable String projectId) {
        return projects.artifacts(projectId);
    }

    /**
     * @param taskId 异步任务标识
     * @return 任务当前状态、原始提示词和失败信息
     */
    @GetMapping("/tasks/{taskId}")
    ProjectStore.StoredTask task(@PathVariable String taskId) {
        return projects.requireTask(taskId);
    }

    /**
     * @param artifactId 导出物标识
     * @return 带 UTF-8 附件名的二进制下载
     */
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

    /**
     * 在服务器本机用 Windows 文件关联打开导出物，而非在客户端机器执行。
     * @param artifactId 导出物标识
     * @return HTTP 202 空响应
     */
    @PostMapping("/artifacts/{artifactId}/open-in-guitar-pro")
    ResponseEntity<Void> openInGuitarPro(@PathVariable String artifactId) {
        guitarProConnector.open(projects.requireArtifact(artifactId).path());
        return ResponseEntity.accepted().build();
    }

    /**
     * @param title 最长 200 字符的非空项目标题
     */
    public record CreateProjectRequest(@NotBlank @Size(max = 200) String title) {
    }

    /**
     * @param prompt 最长 4000 字符的自然语言要求
     */
    public record GenerateRequest(@NotBlank @Size(max = 4000) String prompt) {
    }

    /**
     * @param fromMeasure 从 1 开始的首个改写小节
     * @param toMeasure 从 1 开始的末个改写小节，包含在范围内
     * @param prompt 最长 4000 字符的改写要求
     */
    public record RewriteRequest(@Min(1) @Max(128) int fromMeasure,
                                 @Min(1) @Max(128) int toMeasure,
                                 @NotBlank @Size(max = 4000) String prompt) {
    }

    /**
     * @param message Agent 返回的自然语言消息
     */
    public record AgentChatResponse(String message) {
    }
}
