# Music AI Agent 项目开发规范

## 1. 文档用途

本文档是本项目面向 AI 编程助手和开发者的最高优先级项目说明。开始任何开发任务前，必须先阅读本文档，并基于当前代码状态实施最小、可验证的变更。

如果用户的最新明确要求与本文档冲突，以用户要求为准；完成任务后，应同步更新本文档中已经失效的架构或进度信息。

## 2. 项目愿景

本项目是一款面向吉他手、乐队和音乐创作者的 Music AI Agent。用户通过自然语言、MIDI、MusicXML 或音频素材表达创作意图，系统生成可试听、可修改、可演奏的多轨音乐，并接入 Guitar Pro 8 继续编辑。

核心价值不是简单生成 MIDI，而是：

1. 理解自然语言音乐需求。
2. 生成结构完整的多轨作品。
3. 支持轨道、小节和段落级局部修改。
4. 检查吉他谱的实际可演奏性。
5. 稳定导出 Guitar Pro 8 可导入的 MusicXML 和 MIDI。

## 3. 当前阶段目标

当前阶段只实现第一个纵向切片：

> 用户输入“生成一段 8 小节、120 BPM、E 小调、标准调弦的摇滚吉他 Riff”，LangChain4j 将需求解析为结构化参数，Java 创建并校验音乐事件，导出 MusicXML 和 MIDI，最终可由 Guitar Pro 8 打开。

在该链路完成以前，不实现微服务拆分、复杂 RAG、多 Agent、自训练基础模型、音频转谱、实时 DAW 插件或完整桌面 UI 自动化。

## 4. 技术基线

### 4.1 核心技术栈

- Java 21
- Spring Boot 3.x 稳定版本
- Maven
- LangChain4j：唯一的 Java AI 应用框架
- MyBatis-Plus
- MySQL 8 或 PostgreSQL（二选一；首版默认 MySQL 8，以便复用 QA 项目经验）
- Vue 3 + Vite + Element Plus
- Python 3.11 + FastAPI：仅用于 Java 生态不适合承担的音乐模型和音频算法
- Docker Compose
- JUnit 5 + Testcontainers

当前工程若仍为 Java 17 或 Spring Boot 4.x，应在功能开发前调整至上述基线。不要同时引入 Spring AI 和 LangChain4j。

### 4.2 Guitar Pro 8 集成原则

Guitar Pro 8 集成按以下优先级实施：

1. 生成 MusicXML 文件。
2. 生成 MIDI 文件。
3. 在 Windows 上调用系统方式使用 Guitar Pro 8 打开文件。
4. 后期再考虑 UI Automation 自动导入、保存和导出。

不得假设 Guitar Pro 8 存在公开第三方 API。UI 自动化不能成为核心数据链路。

## 5. 总体架构

系统采用模块化单体优先的架构。Java 负责业务、Agent 编排、领域规则、持久化和文件导出；Python 负责可选的模型推理和音频分析。

推荐逻辑模块：

```text
music-ai-agent/
├── music-domain/          # 音乐领域模型与纯 Java 规则
├── music-application/     # 用例、工作流、端口接口
├── music-agent/           # LangChain4j AI Service、Tools、Memory
├── music-infrastructure/  # MyBatis、存储、外部模型客户端
├── music-export/          # MusicXML、MIDI 导出
├── guitar-pro-connector/  # Guitar Pro 检测、打开与后期自动化
├── music-api/             # Spring Boot、REST、SSE
├── music-frontend/        # Vue 3 前端
├── music-ai-python/       # 后期音乐模型与音频算法服务
└── docker-compose.yml
```

学习阶段可以先使用单个 Spring Boot Maven 模块，但包结构必须保持这些边界。只有当第一个纵向切片完成后，才考虑拆成独立 Maven 模块。

## 6. 分层职责

### 6.1 API 层

负责：

- REST 请求和参数校验
- SSE 事件输出
- DTO 与应用命令转换
- HTTP 状态码和统一错误响应

禁止：

- 在 Controller 中编写音乐规则
- 在 Controller 中直接调用模型
- 在 Controller 中直接使用 Mapper

### 6.2 Application 层

负责：

- 创建项目、生成段落、重写小节、校验和导出等用例
- 创作工作流和任务状态机
- 事务边界
- 调用领域服务、Agent端口和基础设施端口

工作流状态建议：

```text
PENDING
→ PARSING_REQUIREMENTS
→ PLANNING
→ GENERATING
→ VALIDATING
→ EXPORTING
→ COMPLETED
↘ FAILED
```

### 6.3 Domain 层

该层是项目核心，必须保持纯 Java，不依赖 Spring、LangChain4j、MyBatis、HTTP 或数据库实体。

负责：

- 音乐项目、乐谱、轨道、小节、声部和音符建模
- 小节时值不变量
- 乐器音域规则
- 吉他弦品映射
- 指法合法性
- 局部修改边界

### 6.4 Agent 层

负责：

- 使用 LangChain4j 理解用户意图
- 输出结构化创作需求
- 通过 `@Tool` 调用应用服务
- 维护以项目 ID 为边界的 Chat Memory
- 向用户解释生成结果

Agent 不得直接生成最终 MusicXML 文本，不得直接写数据库，也不得绕过领域校验修改工程。

### 6.5 Infrastructure 层

负责：

- 数据库访问
- 文件和对象存储
- 大模型与 Python 服务客户端
- Guitar Pro 操作系统集成
- 领域仓储接口的实现

## 7. 核心领域模型

统一音乐中间表示是所有生成、修改、校验和导出的唯一事实来源。

最低限度应包含：

```text
MusicProject
├── ProjectSettings
│   ├── tempo
│   ├── keySignature
│   └── timeSignature
├── SongSection
└── Score
    └── Track
        ├── Instrument
        ├── Tuning
        └── Measure
            └── Voice
                └── MusicalEvent
                    ├── NoteEvent
                    ├── RestEvent
                    └── ChordEvent
```

吉他相关值对象至少包括：

- `GuitarTuning`
- `StringNumber`
- `FretNumber`
- `FretPosition`
- `Fingering`
- `GuitarTechnique`

时间值必须使用精确表示。禁止以 `double` 表示拍值；优先使用分数值对象，例如 `RhythmicDuration(numerator, denominator)`，或使用统一 tick 并明确 PPQ。

## 8. LangChain4j 设计规范

### 8.1 AI Service

首版只使用一个主 Agent，不建设多 Agent 网络。

建议接口：

```java
public interface MusicCreatorAgent {

    @SystemMessage(fromResource = "prompts/music-agent-system.txt")
    String chat(@MemoryId Long projectId,
                @UserMessage String message);
}
```

需要流式响应时，可以增加独立流式接口，但不得让 Web/SSE 类型进入 Agent 或 Domain 层。

### 8.2 工具设计

第一阶段工具：

- `createMusicProject`
- `planSongStructure`
- `generateGuitarRiff`
- `rewriteMeasures`
- `validateScore`
- `exportProject`

每个工具必须：

- 使用明确的请求 DTO，不使用通用 `Map<String, Object>`。
- 描述调用条件、参数语义和副作用。
- 返回简洁、结构化的结果摘要。
- 调用应用层用例，不直接操作 Mapper。
- 可以重复调用或明确处理幂等性。

### 8.3 结构化输出

模型只输出创作约束、歌曲计划或工具参数等结构化数据。任何模型输出在进入领域模型前必须经过：

1. JSON/对象反序列化校验。
2. Bean Validation。
3. 领域规则校验。
4. 必要时执行有限次数的修复或重新生成。

## 9. Java 与 Python 的边界

### Java 实现

- LangChain4j Agent
- 项目和版本管理
- 音乐领域模型
- 音阶、和弦和节奏基础规则
- 小节时值、音域和指法校验
- MIDI 与 MusicXML 导出
- Guitar Pro 文件打开
- 任务状态与日志

### Python 实现

- 专用符号音乐模型推理
- 音频转 MIDI
- BPM、和弦和多音高识别
- 音频特征提取与乐器分离

在第一个纵向切片中不强制建设 Python 服务。固定规则或受约束的 Java 生成器可以先跑通完整链路。

## 10. 持久化策略

首版建议：

- 关系表保存用户、项目、生成任务、版本和文件元数据。
- 完整乐谱中间表示以 JSON 字段保存版本快照。
- MIDI、MusicXML 和音频保存为文件或对象存储对象。
- 每次局部修改创建新版本，不能静默覆盖唯一副本。

至少需要以下概念：

- `music_project`
- `project_version`
- `generation_task`
- `conversation_message`
- `exported_artifact`

不要在MVP开始时把每个音符都拆成数据库行。

## 11. API 初稿

```text
POST   /api/projects
GET    /api/projects/{projectId}
POST   /api/projects/{projectId}/chat
GET    /api/projects/{projectId}/events
POST   /api/projects/{projectId}/generate
POST   /api/projects/{projectId}/rewrite
POST   /api/projects/{projectId}/validate
POST   /api/projects/{projectId}/exports
GET    /api/tasks/{taskId}
GET    /api/artifacts/{artifactId}/download
POST   /api/artifacts/{artifactId}/open-in-guitar-pro
```

接口细节以实际用例为准。禁止为了满足该草案而提前创建没有业务实现的空接口。

## 12. 生成与校验流程

每次音乐生成必须遵循：

```text
用户意图
→ LangChain4j 解析为 CreationConstraints
→ 创建 SongPlan
→ 生成 MusicalEvent
→ 构造或修改领域对象
→ 确定性规则校验
→ 必要时修复
→ 创建项目版本
→ 导出 MusicXML/MIDI
→ 返回摘要和文件
```

最低校验项：

- 每小节总时值与拍号一致。
- MIDI 音高处于合法范围。
- 吉他音符能映射到指定调弦和品数。
- 同一时刻同一根弦不存在冲突音符。
- 指法跨度不超过当前难度规则。
- 导出的轨道数和小节数与领域模型一致。
- MusicXML 是格式正确的 XML。

## 13. 异步任务与事件

可以在MVP使用 Spring `TaskExecutor`，但生成任务必须持久化，应用重启后不能将 `RUNNING` 永久留作未知状态。

不要在 Service 中直接持有 Servlet `OutputStream`。API层应通过 `SseEmitter` 或 WebFlux SSE 将应用事件转换为HTTP事件。

建议事件类型：

- `AGENT_MESSAGE`
- `PLAN_CREATED`
- `SECTION_GENERATING`
- `TRACK_COMPLETED`
- `VALIDATION_COMPLETED`
- `EXPORT_COMPLETED`
- `TASK_FAILED`

## 14. 测试策略

每个功能优先测试领域规则，再测试框架集成。

必须具备：

- 小节时值单元测试
- 不同拍号单元测试
- MIDI tick转换测试
- 标准调弦和降调弦的弦品映射测试
- 不可演奏指法识别测试
- MusicXML结构测试
- MIDI文件生成与重新读取测试
- 局部重写不会改变非目标小节的测试
- LangChain4j工具参数解析测试
- 模型输出非法时的失败或修复测试
- Guitar Pro导入人工验收清单

外部模型测试默认使用桩或假实现，不能让普通单元测试依赖真实API密钥和网络。

## 15. 安全与版权

- API密钥只能通过环境变量或未提交的本地配置提供。
- 不得在日志中记录完整密钥或用户上传的敏感内容。
- 上传文件必须限制类型、大小和保存路径。
- 文件名不能直接作为服务器保存路径。
- 训练或检索数据必须记录来源和授权状态。
- 产品不得承诺复制在世艺术家的独特风格。
- 用户生成内容和上传素材需要保留可审计的来源信息。

## 16. 编码规范

- 所有文件统一使用 UTF-8。
- 包名使用有意义的项目域名，不沿用 `com.test.qa`。
- 类名表达业务职责，避免 `CommonService`、`Utils` 等模糊命名。
- 优先构造器注入。
- DTO、领域对象和数据库实体不得混用。
- 禁止以字符串散落表示调式、拍号、任务状态和轨道类型；使用枚举或值对象。
- 禁止捕获异常后静默忽略。
- 禁止让长时间外部HTTP调用占用数据库事务。
- 禁止让大模型直接决定数据库ID、文件路径或授权行为。
- 新增依赖前检查许可证、维护状态和必要性。
- 仅为复杂业务决策写注释，不用注释复述代码。

## 17. AI 编程助手工作规则

AI在处理任务时必须：

1. 先检查当前代码、测试和Git变更，不假设文档中的模块已经存在。
2. 明确当前任务属于哪个用例和架构层。
3. 优先完成一个可运行的纵向功能，不批量创建空壳类。
4. 不因顺手而修改用户未要求的无关代码。
5. 变更领域规则时同步补充测试。
6. 修改公共接口、数据结构或架构边界时同步更新本文档。
7. 完成后运行与变更范围相称的测试，并报告未验证部分。
8. 发现需求不明确时，优先依据当前阶段目标做最小合理实现；只有会显著改变产品方向时才询问用户。

## 18. 开发路线图

### Milestone 0：工程基线

- [x] 将Java版本调整为21。
- [x] 使用兼容LangChain4j的Spring Boot 3.x稳定版本。
- [x] 设置项目名称、包名和UTF-8。
- [x] 引入基础测试与配置分层。
- [x] 确认应用可启动。

### Milestone 1：纯Java音乐内核

- [x] 建立最小领域模型。
- [x] 实现拍号与小节时值校验。
- [x] 实现标准吉他调弦和音高到弦品的映射。
- [x] 使用固定输入生成8小节Riff。
- [x] 导出并重新读取MIDI。
- [x] 导出格式正确的MusicXML。
- [x] 人工确认Guitar Pro 8可以导入（已使用 Guitar Pro 8.1.0 验证 MusicXML 与 MIDI）。

### Milestone 2：LangChain4j纵向切片

- [x] 接入LangChain4j模型配置。
- [x] 将自然语言解析为`CreationConstraints`。
- [x] 实现`MusicCreatorAgent`。
- [x] 实现创建、生成、校验和导出工具。
- [x] 完成自然语言到GP8文件的端到端测试。

### Milestone 3：项目与版本

- [x] 引入数据库。
- [x] 保存项目、任务、版本和导出物。
- [x] 实现小节级局部重写。
- [x] 实现版本回滚。
- [x] 实现SSE进度事件。

### Milestone 4：前端MVP

- [x] 创建Vue 3创作工作台。
- [ ] 支持对话、项目列表和任务进度（当前已支持单项目创作与任务进度）。
- [ ] 支持乐谱预览和MIDI试听。
- [x] 支持下载或打开MusicXML/MIDI。

### Milestone 5：音乐AI增强

- [x] 接入Python音乐模型服务（当前提供独立FastAPI WAV分析服务）。
- [ ] 多轨生成。
- [ ] 自定义调弦和难度控制。
- [ ] 指法优化。
- [x] 音频分析或转谱初稿（当前支持WAV元数据、响度与BPM初估）。

## 19. 完成定义

一个功能只有同时满足以下条件才算完成：

- 对应用户流程可以实际运行。
- 领域规则和输入错误得到明确处理。
- 关键逻辑具备自动化测试。
- 不依赖硬编码密钥或个人绝对路径。
- 生成文件经过程序验证。
- 涉及Guitar Pro兼容性的功能已经完成人工导入检查。
- 文档与实际实现一致。

## 20. 下一步

Milestone 0 已完成，第一个纵向切片已经可以生成、校验并导出 MIDI/MusicXML。当前优先完成以下验收缺口：

1. 使用 Testcontainers 验证 MySQL 8 初始化与持久化链路。
2. 增加乐谱预览、MIDI 试听和项目列表。
3. 在 Python 依赖可下载的环境中验证 FastAPI HTTP 上传接口。

当前本地开发与普通测试使用 H2，Docker Compose 和生产 `mysql` Profile 使用 MySQL 8；H2 不作为生产数据库。

当前仓库物理目录采用 `music-backend`、`music-frontend`、`music-ai-python` 三个顶级实现目录。Java 暂时仍是单 Maven 模块，`music-backend` 内部通过包结构维持 Domain、Application、Agent、Infrastructure、Export 和 API 边界；在首个纵向切片继续演进期间不拆分多 Maven 模块。本地数据库和导出文件统一写入根目录 `.local`，不得提交 Git。

2026-07-14 已将首版固定两小节音型生成器重构为确定性的约束驱动生成器。`CreationConstraints` 现在除小节数、速度、调性、风格、调弦和拍号外，还包含情绪、节奏感觉、复杂度和由原始提示词派生的变化种子。生成器必须根据调式音阶、风格、节奏网格和动机发展创建事件；禁止恢复为与提示词无关的固定 MIDI 音高数组。相同提示词和约束应可复现，不同提示词或关键音乐约束必须产生不同的音乐事件序列。

当前的生成能力仍是单轨吉他规则生成，不应描述为通用音乐基础模型。下一阶段应在保持领域校验的前提下引入结构化 `SongPlan` 与 `RiffPlan`，表达和声进行、段落功能、技法、旋律轮廓与动机变奏；模型仍不得直接生成最终 MusicXML。MusicXML 调号现已根据领域乐谱中的实际调性导出，不再硬编码 E 小调。
