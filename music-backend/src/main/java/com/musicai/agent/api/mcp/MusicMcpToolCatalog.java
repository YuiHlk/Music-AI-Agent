package com.musicai.agent.api.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicai.agent.application.MusicProjectService;
import com.musicai.agent.application.CreationConstraints;
import com.musicai.agent.application.ResourceNotFoundException;
import com.musicai.agent.application.port.ProjectStore;
import com.musicai.agent.domain.Score;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 声明 Music AI Agent 对 MCP 客户端开放的工具目录。
 *
 * <p>本类只负责协议 Schema、参数校验和结果适配，实际业务统一委托给
 * {@link MusicProjectService}，因此 MCP 调用不会绕过应用层与领域校验。</p>
 */
@Component
public class MusicMcpToolCatalog {

    private final MusicProjectService projects;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * @param projects 项目应用服务
     * @param objectMapper MCP 参数和结果的对象映射器
     * @param validator 工具输入校验器
     */
    public MusicMcpToolCatalog(MusicProjectService projects, ObjectMapper objectMapper, Validator validator) {
        this.projects = projects;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /**
     * 构建服务器启动时注册的全部同步工具定义。
     *
     * <p>“同步工具”指 MCP 调用本身同步返回；音乐生成与局部重写仍会返回
     * 持久化任务，由客户端继续轮询任务状态。</p>
     *
     * @return MCP 工具定义列表
     */
    List<McpServerFeatures.SyncToolSpecification> specifications() {
        // Schema、行为提示和执行器在同一处声明，避免 MCP 客户端看到的能力与真实用例漂移。
        return List.of(
                tool("create_music_project",
                        "Create a persistent music project and return its generated project ID.",
                        objectSchema(Map.of("title", stringProperty("Project title", 1, 200)), List.of("title")),
                        objectSchema(projectOutputProperties(), List.of("id", "title", "status", "currentVersion")),
                        annotations(false, false, false),
                        CreateProjectInput.class,
                        input -> ProjectSummary.from(projects.createProject(input.title()))),
                tool("generate_guitar_riff",
                        "Start asynchronous natural-language guitar riff generation for an existing project. "
                                + "Poll get_generation_task with the returned task ID.",
                        objectSchema(Map.of(
                                "projectId", idProperty("Project ID"),
                                "prompt", stringProperty("Music creation request", 1, 4000)),
                                List.of("projectId", "prompt")),
                        taskOutputSchema(),
                        annotations(false, false, false),
                        GenerateInput.class,
                        input -> TaskSummary.from(projects.generate(input.projectId(), input.prompt()))),
                tool("generate_guitar_riff_from_constraints",
                        "Start asynchronous guitar riff generation from already structured constraints. "
                                + "This tool does not invoke the server-side language model; it is intended for "
                                + "clients using their own model and API key. Poll get_generation_task afterwards.",
                        structuredGenerationInputSchema(),
                        taskOutputSchema(),
                        annotations(false, false, false),
                        StructuredGenerateInput.class,
                        input -> TaskSummary.from(projects.generate(input.projectId(), input.toConstraints()))),
                tool("get_generation_task",
                        "Read the current state of an asynchronous generation or rewrite task.",
                        objectSchema(Map.of("taskId", idProperty("Generation task ID")), List.of("taskId")),
                        taskOutputSchema(),
                        annotations(true, false, true),
                        TaskInput.class,
                        input -> TaskSummary.from(projects.requireTask(input.taskId()))),
                tool("get_score_summary",
                        "Read a compact summary of the current validated score without exposing server file paths.",
                        objectSchema(Map.of("projectId", idProperty("Project ID")), List.of("projectId")),
                        objectSchema(Map.of(
                                "title", stringProperty("Score title", 1, 200),
                                "tempo", integerProperty("Tempo in BPM", 20, 300),
                                "keySignature", stringProperty("Key signature", 1, 100),
                                "timeSignature", stringProperty("Time signature", 3, 16),
                                "trackCount", integerProperty("Track count", 1, 128),
                                "measureCount", integerProperty("Measure count", 1, 1024),
                                "tracks", arrayProperty("Track summaries")),
                                List.of("title", "tempo", "keySignature", "timeSignature", "trackCount",
                                        "measureCount", "tracks")),
                        annotations(true, false, true),
                        ProjectInput.class,
                        input -> ScoreSummary.from(projects.currentScore(input.projectId()))),
                tool("validate_score",
                        "Run deterministic duration and guitar playability validation on the current score.",
                        objectSchema(Map.of("projectId", idProperty("Project ID")), List.of("projectId")),
                        objectSchema(Map.of(
                                "valid", Map.of("type", "boolean", "description", "Whether validation passed"),
                                "tracks", integerProperty("Validated track count", 1, 128),
                                "measures", integerProperty("Validated measure count", 1, 1024)),
                                List.of("valid", "tracks", "measures")),
                        annotations(true, false, true),
                        ProjectInput.class,
                        input -> projects.validate(input.projectId())),
                tool("list_artifacts",
                        "List exported MIDI and MusicXML metadata for a project. Server storage paths are omitted.",
                        objectSchema(Map.of("projectId", idProperty("Project ID")), List.of("projectId")),
                        objectSchema(Map.of("artifacts", arrayProperty("Exported artifact metadata")),
                                List.of("artifacts")),
                        annotations(true, false, true),
                        ProjectInput.class,
                        input -> new ArtifactList(projects.artifacts(input.projectId()).stream()
                                .map(ArtifactSummary::from).toList())),
                tool("rewrite_measures",
                        "Start an asynchronous rewrite of an inclusive measure range and create a new score version. "
                                + "Poll get_generation_task with the returned task ID.",
                        objectSchema(Map.of(
                                "projectId", idProperty("Project ID"),
                                "fromMeasure", integerProperty("First measure to replace", 1, 128),
                                "toMeasure", integerProperty("Last measure to replace", 1, 128),
                                "prompt", stringProperty("Rewrite request", 1, 4000)),
                                List.of("projectId", "fromMeasure", "toMeasure", "prompt")),
                        taskOutputSchema(),
                        annotations(false, false, false),
                        RewriteInput.class,
                        input -> TaskSummary.from(projects.rewrite(input.projectId(), input.fromMeasure(),
                                input.toMeasure(), input.prompt()))),
                tool("rollback_project",
                        "Move a project's current-version pointer to an existing historical version. "
                                + "This changes the active score but does not delete later versions.",
                        objectSchema(Map.of(
                                "projectId", idProperty("Project ID"),
                                "versionNumber", integerProperty("Version to make current", 1, 100000)),
                                List.of("projectId", "versionNumber")),
                        objectSchema(projectOutputProperties(), List.of("id", "title", "status", "currentVersion")),
                        annotations(false, true, true),
                        RollbackInput.class,
                        input -> ProjectSummary.from(projects.rollback(input.projectId(), input.versionNumber())))
        );
    }

    private <T> McpServerFeatures.SyncToolSpecification tool(String name, String description,
                                                              Map<String, Object> inputSchema,
                                                              Map<String, Object> outputSchema,
                                                              McpSchema.ToolAnnotations annotations,
                                                              Class<T> inputType,
                                                              Function<T, Object> action) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, inputSchema)
                .description(description)
                .outputSchema(outputSchema)
                .annotations(annotations)
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> invoke(request.arguments(), inputType, action))
                .build();
    }

    private <T> McpSchema.CallToolResult invoke(Map<String, Object> arguments, Class<T> inputType,
                                                Function<T, Object> action) {
        try {
            // MCP 参数属于不可信外部输入，先转成强类型 DTO 并执行 Bean Validation，再进入应用层。
            T input = objectMapper.convertValue(arguments, inputType);
            validate(input);
            return success(action.apply(input));
        } catch (ResourceNotFoundException | IllegalArgumentException | IllegalStateException exception) {
            return failure(exception);
        }
    }

    private <T> void validate(T input) {
        Set<ConstraintViolation<T>> violations = validator.validate(input);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .findFirst()
                    .orElse("Invalid tool input");
            throw new IllegalArgumentException(message);
        }
    }

    private McpSchema.CallToolResult success(Object output) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(writeJson(output))
                .structuredContent(output)
                .isError(false)
                .build();
    }

    private McpSchema.CallToolResult failure(RuntimeException exception) {
        ErrorOutput output = new ErrorOutput(exception instanceof ResourceNotFoundException
                ? "NOT_FOUND" : "INVALID_REQUEST", exception.getMessage());
        return McpSchema.CallToolResult.builder()
                .addTextContent(writeJson(output))
                .structuredContent(output)
                .isError(true)
                .build();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize MCP tool output", exception);
        }
    }

    private static McpSchema.ToolAnnotations annotations(boolean readOnly, boolean destructive,
                                                          boolean idempotent) {
        // openWorld=false 表示工具只操作本项目边界内的资源，不代表工具调用一定是只读的。
        return McpSchema.ToolAnnotations.builder()
                .readOnlyHint(readOnly)
                .destructiveHint(destructive)
                .idempotentHint(idempotent)
                .openWorldHint(false)
                .build();
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> stringProperty(String description, int minimumLength, int maximumLength) {
        return Map.of("type", "string", "description", description,
                "minLength", minimumLength, "maxLength", maximumLength);
    }

    private static Map<String, Object> idProperty(String description) {
        return stringProperty(description, 1, 64);
    }

    private static Map<String, Object> integerProperty(String description, int minimum, int maximum) {
        return Map.of("type", "integer", "description", description, "minimum", minimum, "maximum", maximum);
    }

    private static Map<String, Object> arrayProperty(String description) {
        return Map.of("type", "array", "description", description, "items", Map.of("type", "object"));
    }

    private static Map<String, Object> projectOutputProperties() {
        return Map.of(
                "id", idProperty("Project ID"),
                "title", stringProperty("Project title", 1, 200),
                "status", stringProperty("Project status", 1, 40),
                "currentVersion", integerProperty("Current score version; zero means not generated", 0, 100000));
    }

    private static Map<String, Object> structuredGenerationInputSchema() {
        return objectSchema(Map.ofEntries(
                        Map.entry("projectId", idProperty("Project ID")),
                        Map.entry("measures", integerProperty("Measure count", 1, 128)),
                        Map.entry("tempo", integerProperty("Tempo in BPM", 20, 300)),
                        Map.entry("keySignature", stringProperty("Key signature such as E minor", 1, 100)),
                        Map.entry("style", stringProperty("Normalized musical style", 1, 100)),
                        Map.entry("tuning", enumProperty("Guitar tuning", "STANDARD", "STANDARD_E", "DROP_D")),
                        Map.entry("timeSignatureBeats", integerProperty("Beats per measure", 1, 32)),
                        Map.entry("timeSignatureBeatUnit", enumIntegerProperty("Beat unit", 1, 2, 4, 8, 16, 32)),
                        Map.entry("mood", enumProperty("Musical mood",
                                "DARK", "BRIGHT", "AGGRESSIVE", "MELANCHOLIC", "ENERGETIC", "CALM")),
                        Map.entry("rhythmicFeel", enumProperty("Rhythmic feel",
                                "HALF_TIME", "STRAIGHT", "SYNCOPATED", "DRIVING")),
                        Map.entry("complexity", integerProperty("Complexity level", 1, 5)),
                        Map.entry("variationSeed", Map.of("type", "integer",
                                "description", "Optional deterministic variation seed"))),
                List.of("projectId", "measures", "tempo", "keySignature", "style", "tuning",
                        "timeSignatureBeats", "timeSignatureBeatUnit", "mood", "rhythmicFeel", "complexity"));
    }

    private static Map<String, Object> enumProperty(String description, String... values) {
        return Map.of("type", "string", "description", description, "enum", List.of(values));
    }

    private static Map<String, Object> enumIntegerProperty(String description, Integer... values) {
        return Map.of("type", "integer", "description", description, "enum", List.of(values));
    }

    private static Map<String, Object> taskOutputSchema() {
        return objectSchema(Map.of(
                "id", idProperty("Task ID"),
                "projectId", idProperty("Project ID"),
                "status", stringProperty("Task status", 1, 40),
                "errorMessage", Map.of("type", List.of("string", "null"), "description", "Failure detail"),
                "createdAt", stringProperty("Creation timestamp", 1, 100),
                "updatedAt", stringProperty("Last update timestamp", 1, 100)),
                List.of("id", "projectId", "status", "createdAt", "updatedAt"));
    }

    record CreateProjectInput(@NotBlank @Size(max = 200) String title) {
    }

    record ProjectInput(@NotBlank @Size(max = 64) String projectId) {
    }

    record TaskInput(@NotBlank @Size(max = 64) String taskId) {
    }

    record GenerateInput(@NotBlank @Size(max = 64) String projectId,
                         @NotBlank @Size(max = 4000) String prompt) {
    }

    record StructuredGenerateInput(
            @NotBlank @Size(max = 64) String projectId,
            @Min(1) @Max(128) int measures,
            @Min(20) @Max(300) int tempo,
            @NotBlank @Size(max = 100) String keySignature,
            @NotBlank @Size(max = 100) String style,
            @NotBlank @Size(max = 32) String tuning,
            @Min(1) @Max(32) int timeSignatureBeats,
            @Min(1) @Max(32) int timeSignatureBeatUnit,
            @NotBlank @Size(max = 32) String mood,
            @NotBlank @Size(max = 32) String rhythmicFeel,
            @Min(1) @Max(5) int complexity,
            long variationSeed) {

        CreationConstraints toConstraints() {
            return CreationConstraints.fromStructured(measures, tempo, keySignature, style, tuning,
                    timeSignatureBeats, timeSignatureBeatUnit, mood, rhythmicFeel, complexity, variationSeed);
        }
    }

    record RewriteInput(@NotBlank @Size(max = 64) String projectId,
                        @Min(1) @Max(128) int fromMeasure,
                        @Min(1) @Max(128) int toMeasure,
                        @NotBlank @Size(max = 4000) String prompt) {
    }

    record RollbackInput(@NotBlank @Size(max = 64) String projectId,
                         @Min(1) int versionNumber) {
    }

    record ProjectSummary(String id, String title, String status, int currentVersion) {
        static ProjectSummary from(ProjectStore.StoredProject project) {
            return new ProjectSummary(project.id(), project.title(), project.status(), project.currentVersion());
        }
    }

    record TaskSummary(String id, String projectId, String status, String errorMessage,
                       String createdAt, String updatedAt) {
        static TaskSummary from(ProjectStore.StoredTask task) {
            return new TaskSummary(task.id(), task.projectId(), task.status(), task.errorMessage(),
                    task.createdAt().toString(), task.updatedAt().toString());
        }
    }

    record TrackSummary(String name, String tuning, int measures) {
    }

    record ScoreSummary(String title, int tempo, String keySignature, String timeSignature,
                        int trackCount, int measureCount, List<TrackSummary> tracks) {
        static ScoreSummary from(Score score) {
            List<TrackSummary> tracks = score.tracks().stream()
                    .map(track -> new TrackSummary(track.name(), track.tuning().name(), track.measures().size()))
                    .toList();
            return new ScoreSummary(score.title(), score.tempo(), score.keySignature(),
                    score.timeSignature().beats() + "/" + score.timeSignature().beatUnit(),
                    tracks.size(), tracks.getFirst().measures(), tracks);
        }
    }

    record ArtifactSummary(String id, String projectId, int versionNumber, String type, String createdAt) {
        static ArtifactSummary from(ProjectStore.StoredArtifact artifact) {
            return new ArtifactSummary(artifact.id(), artifact.projectId(), artifact.versionNumber(), artifact.type(),
                    artifact.createdAt().toString());
        }
    }

    record ArtifactList(List<ArtifactSummary> artifacts) {
    }

    record ErrorOutput(String code, String message) {
    }
}
