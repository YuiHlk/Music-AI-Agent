package com.musicai.agent.agent;

import com.musicai.agent.application.CreationConstraints;
import com.musicai.agent.application.MusicProjectService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 向对话代理暴露受控的音乐项目应用工具。
 *
 * <p>工具方法是 AI 信任边界：代理只能提交受限参数，实际状态变更由应用服务执行，避免模型直接接触存储与文件系统。</p>
 */
@Component
public class MusicCreationTools {

    private final MusicProjectService projects;

    /**
     * 创建工具集合。
     *
     * @param projects 音乐项目应用服务
     */
    public MusicCreationTools(MusicProjectService projects) {
        this.projects = projects;
    }

    /**
     * 创建一个尚未包含乐谱版本的持久化项目。
     *
     * @param title 用户可见的项目标题
     * @return 新项目摘要
     */
    @Tool("Create a persistent music project before generating music. The title is user-visible.")
    public ProjectResult createMusicProject(
            @P("A concise project title; must not contain a database ID or file path") String title) {
        var project = projects.createProject(title);
        return new ProjectResult(project.id(), project.title(), project.currentVersion(), project.status());
    }

    /**
     * 在不写文件、不创建版本的情况下返回首阶段歌曲结构计划。
     *
     * @param measures 计划小节数
     * @param style 音乐风格
     * @return 简要歌曲结构
     */
    @Tool("Create a concise song plan from a bounded guitar riff request without writing files.")
    public SongPlanResult planSongStructure(
            @P("Number of measures between 1 and 128") int measures,
            @P("Musical style such as rock or metal") String style) {
        if (measures < 1 || measures > 128) {
            throw new IllegalArgumentException("Measure count must be between 1 and 128");
        }
        return new SongPlanResult(measures, style, "One guitar track; alternating two-measure riff motifs");
    }

    /**
     * 为已有项目创建异步生成任务。
     *
     * @param projectId 项目标识
     * @param measures 小节数
     * @param tempo BPM
     * @param keySignature 调性
     * @param style 风格
     * @param tuning 调弦
     * @param timeSignatureBeats 每小节拍数
     * @param timeSignatureBeatUnit 拍号分母
     * @param mood 情绪枚举名
     * @param rhythmicFeel 节奏感觉枚举名
     * @param complexity 复杂度
     * @return 可供轮询的任务摘要
     */
    @Tool("Start asynchronous guitar riff generation from structured constraints already inferred by the agent. "
            + "This does not call a second language model. Poll getGenerationTask with the returned task ID.")
    public TaskResult generateGuitarRiff(
            @P("Existing project ID returned by createMusicProject") String projectId,
            @P("Number of measures from 1 to 128") int measures,
            @P("Tempo in BPM from 20 to 300") int tempo,
            @P("Key signature such as E minor") String keySignature,
            @P("Musical style such as rock or metal") String style,
            @P("Guitar tuning: STANDARD or DROP_D") String tuning,
            @P("Beats per measure") int timeSignatureBeats,
            @P("Beat unit: 1, 2, 4, 8, 16 or 32") int timeSignatureBeatUnit,
            @P("Mood: DARK, BRIGHT, AGGRESSIVE, MELANCHOLIC, ENERGETIC or CALM") String mood,
            @P("Rhythmic feel: STRAIGHT, SYNCOPATED, DRIVING or HALF_TIME") String rhythmicFeel,
            @P("Complexity from 1 to 5") int complexity) {
        String seedMaterial = String.join("|", Integer.toString(measures), Integer.toString(tempo), keySignature,
                style, tuning, Integer.toString(timeSignatureBeats), Integer.toString(timeSignatureBeatUnit), mood,
                rhythmicFeel, Integer.toString(complexity));
        var constraints = CreationConstraints.fromStructured(measures, tempo, keySignature, style, tuning,
                timeSignatureBeats, timeSignatureBeatUnit, mood, rhythmicFeel, complexity,
                AiRequirementParser.stableSeed(seedMaterial));
        var task = projects.generate(projectId, constraints);
        return new TaskResult(task.id(), task.projectId(), task.status());
    }

    /** @return 当前异步生成或重写任务状态 */
    @Tool("Read an asynchronous generation or rewrite task. Wait for COMPLETED before validation or export lookup.")
    public TaskResult getGenerationTask(@P("Task ID returned by generation or rewrite") String taskId) {
        var task = projects.requireTask(taskId);
        return new TaskResult(task.id(), task.projectId(), task.status());
    }

    /**
     * 重写闭区间内的小节并保留其他小节，成功后产生新的不可变版本。
     *
     * @param projectId 项目标识
     * @param fromMeasure 起始小节号，从 1 开始
     * @param toMeasure 结束小节号，包含在重写范围内
     * @param request 自然语言改写要求
     * @return 可供轮询的任务摘要
     */
    @Tool("Rewrite only an inclusive range of measures and create a new immutable project version.")
    public TaskResult rewriteMeasures(
            @P("Existing project ID") String projectId,
            @P("First measure number, starting from 1") int fromMeasure,
            @P("Last measure number, inclusive") int toMeasure,
            @P("Natural-language replacement constraints") String request) {
        var task = projects.rewrite(projectId, fromMeasure, toMeasure, request);
        return new TaskResult(task.id(), task.projectId(), task.status());
    }

    /**
     * @param projectId 项目标识
     * @return 当前乐谱的确定性校验结果
     */
    @Tool("Validate the current score using deterministic duration and guitar playability rules.")
    public ValidationResult validateScore(@P("Existing project ID") String projectId) {
        var validation = projects.validate(projectId);
        return new ValidationResult(validation.valid(), validation.tracks(), validation.measures());
    }

    /**
     * 查询已经生成的导出物；该工具不会重复执行导出。
     *
     * @param projectId 项目标识
     * @return 导出物数量和格式列表
     */
    @Tool("List already exported MIDI and MusicXML artifacts for the current project version.")
    public ExportResult exportProject(@P("Existing project ID") String projectId) {
        var artifacts = projects.artifacts(projectId);
        return new ExportResult(artifacts.size(), artifacts.stream().map(artifact -> artifact.type()).toList());
    }

    /**
     * @param projectId 项目标识
     * @param title 项目标题
     * @param currentVersion 当前乐谱版本号
     * @param status 项目状态
     */
    public record ProjectResult(String projectId, String title, int currentVersion, String status) {
    }

    /**
     * @param measures 小节数
     * @param style 音乐风格
     * @param structure 结构说明
     */
    public record SongPlanResult(int measures, String style, String structure) {
    }

    /**
     * @param taskId 任务标识
     * @param projectId 所属项目标识
     * @param status 任务状态
     */
    public record TaskResult(String taskId, String projectId, String status) {
    }

    /**
     * @param valid 是否通过校验
     * @param tracks 轨道数
     * @param measures 小节数
     */
    public record ValidationResult(boolean valid, int tracks, int measures) {
    }

    /**
     * @param artifactCount 导出物数量
     * @param types 导出格式列表
     */
    public record ExportResult(int artifactCount, java.util.List<String> types) {
    }
}
