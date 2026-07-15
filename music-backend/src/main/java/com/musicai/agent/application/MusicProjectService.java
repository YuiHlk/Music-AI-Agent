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

/**
 * 编排音乐项目、异步生成任务、版本持久化及导出产物的应用服务。
 */
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

    /**
     * 创建音乐项目应用服务。
     *
     * @param store 项目、任务、版本与产物存储端口
     * @param requirementParser 自然语言需求解析器
     * @param events 项目事件发布端口
     * @param taskExecutor 音乐生成专用异步执行器
     * @param objectMapper 乐谱版本序列化器
     * @param exportDirectory 导出文件根目录
     */
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

    /**
     * 创建一个尚无乐谱版本的音乐项目。
     *
     * @param title 项目标题
     * @return 已持久化的项目
     */
    public ProjectStore.StoredProject createProject(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Project title must not be blank");
        }
        return store.createProject(title.trim());
    }

    /**
     * 创建生成任务并提交到异步执行器。
     *
     * @param projectId 目标项目标识
     * @param prompt 自然语言创作提示
     * @return 可立即查询状态的持久化任务
     */
    public ProjectStore.StoredTask generate(String projectId, String prompt) {
        requireProject(projectId);
        ProjectStore.StoredTask task = store.createTask(projectId, prompt);
        // 先持久化任务再异步执行，确保客户端立刻获得可查询的 taskId。
        taskExecutor.execute(() -> runGeneration(task));
        return task;
    }

    /**
     * 创建指定小节闭区间的异步重写任务。
     *
     * @param projectId 目标项目标识
     * @param fromMeasure 起始小节编号，包含该小节
     * @param toMeasure 结束小节编号，包含该小节
     * @param prompt 局部重写提示
     * @return 可立即查询状态的持久化任务
     */
    public ProjectStore.StoredTask rewrite(String projectId, int fromMeasure, int toMeasure, String prompt) {
        if (fromMeasure < 1 || toMeasure < fromMeasure) {
            throw new IllegalArgumentException("Invalid rewrite measure range");
        }
        requireProject(projectId);
        ProjectStore.StoredTask task = store.createTask(projectId, prompt);
        taskExecutor.execute(() -> runRewrite(task, fromMeasure, toMeasure));
        return task;
    }

    /**
     * 根据id获取项目，不存在时抛出应用层资源异常。
     *
     * @param projectId 项目标识
     * @return 已持久化项目
     * @throws ResourceNotFoundException 项目不存在时
     */
    public ProjectStore.StoredProject requireProject(String projectId) {
        return store.findProject(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
    }

    /**
     * 查询最近的项目列表。
     *
     * @param limit 最大返回数量
     * @return 项目列表
     */
    public List<ProjectStore.StoredProject> projects(int limit) {
        return store.findProjects(limit);
    }

    /**
     * 按标识取得生成任务。
     *
     * @param taskId 任务标识
     * @return 已持久化任务
     * @throws ResourceNotFoundException 任务不存在时
     */
    public ProjectStore.StoredTask requireTask(String taskId) {
        return store.findTask(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
    }

    /**
     * 读取项目当前版本的乐谱快照。
     *
     * @param projectId 项目标识
     * @return 反序列化后的当前乐谱
     * @throws IllegalStateException 项目尚无乐谱或当前版本缺失时
     */
    public Score currentScore(String projectId) {
        ProjectStore.StoredProject project = requireProject(projectId);
        if (project.currentVersion() == 0) {
            throw new IllegalStateException("Project has no generated score");
        }
        return readScore(store.findVersion(projectId, project.currentVersion())
                .orElseThrow(() -> new IllegalStateException("Current project version is missing")));
    }

    /**
     * 校验项目当前乐谱并返回结构摘要。
     *
     * @param projectId 项目标识
     * @return 校验成功后的轨道及小节统计
     */
    public ValidationSummary validate(String projectId) {
        Score score = currentScore(projectId);
        score.validate();
        return new ValidationSummary(true, score.tracks().size(), score.tracks().getFirst().measures().size());
    }

    /**
     * 将项目当前版本指针切换到指定历史版本。
     *
     * @param projectId 项目标识
     * @param versionNumber 目标版本号
     * @return 回滚后的项目状态
     */
    public ProjectStore.StoredProject rollback(String projectId, int versionNumber) {
        requireProject(projectId);
        store.setCurrentVersion(projectId, versionNumber);
        return requireProject(projectId);
    }

    /**
     * 查询项目已登记的全部导出产物。
     *
     * @param projectId 项目标识
     * @return 产物元数据列表
     */
    public List<ProjectStore.StoredArtifact> artifacts(String projectId) {
        requireProject(projectId);
        return store.findArtifacts(projectId);
    }

    /**
     * 按标识取得导出产物元数据。
     *
     * @param artifactId 产物标识
     * @return 已登记产物
     * @throws ResourceNotFoundException 产物不存在时
     */
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
            // 重写提示词只改变创作特征；小节数、拍号和调弦仍服从原工程，防止局部操作改变全局结构。
            CreationConstraints constraints = new CreationConstraints(original.tracks().getFirst().measures().size(),
                    parsed.tempo(), parsed.keySignature(), parsed.style(), original.tracks().getFirst().tuning(),
                    original.timeSignature(), parsed.mood(), parsed.rhythmicFeel(), parsed.complexity(),
                    parsed.variationSeed());
            Score replacement = generator.generate(constraints);
            transition(task, GenerationStatus.GENERATING, "SECTION_GENERATING");
            Score rewritten = replaceMeasures(original, replacement, fromMeasure, toMeasure);
            finishScore(task, rewritten);
        } catch (Exception exception) {
            fail(task, exception); //因为它在后台线程中运行，异常不能直接作为 HTTP 响应返回。
        }
    }

    static Score replaceMeasures(Score original, Score replacement, int fromMeasure, int toMeasure) {
        // 复制原列表后仅替换目标区间，这是“非目标小节保持不变”不变量的实现位置。
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
        // 即使项目 ID 的来源今后发生变化，也不允许路径穿越导出根目录。
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

    /**
     * 描述一次当前乐谱校验的成功结果。
     *
     * @param valid 是否通过全部领域校验
     * @param tracks 轨道数量
     * @param measures 每条轨道的小节数量
     */
    public record ValidationSummary(boolean valid, int tracks, int measures) {
    }
}
