package com.musicai.agent.agent;

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
     * @param request 自然语言创作约束
     * @return 可供轮询的任务摘要
     */
    @Tool("Start asynchronous guitar riff generation for an existing project. Returns a task to poll.")
    public TaskResult generateGuitarRiff(
            @P("Existing project ID returned by createMusicProject") String projectId,
            @P("Natural-language creation constraints") String request) {
        var task = projects.generate(projectId, request);
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
