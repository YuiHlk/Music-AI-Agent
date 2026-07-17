---
aliases: [项目目录说明, 源码导览]
tags: [project/music-ai-agent, topic/source-code]
status: active
---

# 项目目录与逐文件说明

导航：[[00-首页]] · [[01-架构与数据流]] · [[AGENTS]]

## 文档范围

本文档记录 Music AI Agent 主项目的业务源码、配置和测试结构。当前知识库位于主项目的`Music-AI-Agent-Docs/`子目录，并可单独作为Obsidian Vault打开；下述业务路径均以主项目根目录为基准。`.git/`、`.idea/`、`.local/`、`target/`、`node_modules/`、`dist/`、Python虚拟环境及缓存只按目录说明，不枚举内部文件。

## 主项目根目录

| 文件/目录                                  | 作用                                         |
| -------------------------------------- | ------------------------------------------ |
| `.agents/`                             | AI 开发工具项目目录，当前为空。                          |
| `.git/`                                | Git 对象、分支、索引和提交历史。                         |
| `.idea/`                               | IDEA 本机项目、数据源和工作区配置。                       |
| `.local/`                              | 默认 H2 数据和 MIDI/MusicXML 导出物，不提交。           |
| `.run/MusicAiAgentApplication.run.xml` | 可共享的 IDEA Spring Boot 启动配置。                |
| `Music-AI-Agent-Docs/.obsidian/`       | 文档Vault的Obsidian配置。                         |
| `.env`                                 | 本机密钥和 Docker 变量，不提交、不进入笔记。                 |
| `.env.example`                         | 无真实密钥的环境变量模板。                              |
| `.gitattributes`                       | Git 文本和换行属性。                               |
| `.gitignore`                           | 排除密钥、运行数据、IDE 文件、依赖与构建产物。                  |
| `Music-AI-Agent-Docs/AGENTS.md`        | 开发规范、架构边界与路线图。                           |
| `README.md`                            | 项目介绍、启动和测试入口。                              |
| `docker-compose.yml`                   | 编排 MySQL、API、前端和音频分析服务。                    |
| `docker/mysql/init.sql`                | MySQL 数据卷首次启动时创建数据库和五张业务表。                 |
| `scripts/run-with-llm.ps1`             | 使用通用提供商、地址、模型名和Key启动`llm` Profile。       |
| `scripts/run-with-deepseek.ps1`        | 向后兼容的DeepSeek快捷启动入口。                         |

## 后端根目录 `music-backend/`

| 文件                                      | 作用                                            |
| --------------------------------------- | --------------------------------------------- |
| `Dockerfile`                            | Maven/JDK 21 构建 Jar，再用 JRE 21 运行。             |
| `pom.xml`                               | Spring Boot、LangChain4j、MCP、MyBatis、数据库和测试依赖。 |
| `mvnw` / `mvnw.cmd`                     | Linux/macOS 与 Windows Maven Wrapper。          |
| `.mvn/wrapper/maven-wrapper.jar`        | Maven Wrapper 引导程序。                           |
| `.mvn/wrapper/maven-wrapper.properties` | Wrapper Maven 版本与下载配置。                        |

### 应用入口

| 文件 | 作用 |
|---|---|
| `MusicAiAgentApplication.java` | Spring Boot 主类和组件扫描入口。 |

### Agent 层 `agent/`

| 文件 | 作用 |
|---|---|
| `CreationConstraintsAiService.java` | LangChain4j 结构化需求解析接口。 |
| `CreationConstraintsResponse.java` | 模型输出 DTO：小节、速度、调性、风格、调弦、拍号、情绪、节奏和复杂度。 |
| `RequirementParser.java` | Application 依赖的需求解析端口。 |
| `AiRequirementParser.java` | 将任意配置模型的输出校验并转换为约束，同时派生稳定变化种子。 |
| `RuleBasedRequirementParser.java` | 非`llm` Profile 的中英文离线解析器。 |
| `MusicCreatorAgent.java` | 带项目级 Chat Memory 的主对话 Agent。 |
| `MusicCreationTools.java` | 内部Agent工具：创建、结构化生成、任务查询、重写、校验和导出物查询。 |

### API 层 `api/`

| 文件                               | 作用                                |
| -------------------------------- | --------------------------------- |
| `ProjectController.java`         | 项目、生成、对话、重写、校验、回滚、SSE、任务和导出接口。    |
| `ScorePreviewResponse.java`      | 将领域 Score 转为前端预览 DTO。             |
| `ProjectEventBroker.java`        | `ProjectEventPublisher` 的 SSE 实现。 |
| `SessionController.java`         | 访问密钥登录与 HttpOnly 会话 Cookie。       |
| `AccessKeyFilter.java`           | 校验请求头密钥或会话 Cookie。                |
| `InvalidAccessKeyException.java` | 无效访问密钥异常。                         |
| `ApiExceptionHandler.java`       | 统一 HTTP 错误响应。                     |

### MCP `api/mcp/`

| 文件 | 作用 |
|---|---|
| `MusicMcpProperties.java` | MCP 开关和 Token 配置。 |
| `MusicMcpConfiguration.java` | MCP Server 与 HTTP 端点注册。 |
| `MusicMcpToolCatalog.java` | 创建、自然语言/结构化生成、查询、校验、重写和回滚工具 Schema/执行器。 |
| `McpBearerTokenFilter.java` | MCP Bearer Token 鉴权。 |

### Application 层 `application/`

| 文件 | 作用 |
|---|---|
| `MusicProjectService.java` | 创建项目、异步生成、重写、回滚、校验、版本保存和导出编排。 |
| `CreationConstraints.java` | 已验证的创作参数和值域。 |
| `GuitarRiffGenerator.java` | 基于调式、风格、节奏、情绪、复杂度和种子生成可演奏 Riff。 |
| `GenerationStatus.java` | 任务生命周期状态枚举。 |
| `MusicalMood.java` | 黑暗、明亮、激进、忧郁、活力和平静情绪枚举。 |
| `RhythmicFeel.java` | 直拍、切分、推进和 half-time 枚举。 |
| `ResourceNotFoundException.java` | 项目、任务或导出物不存在异常。 |
| `port/ProjectStore.java` | 项目、版本、任务、消息和导出物持久化端口。 |
| `port/ProjectEventPublisher.java` | 应用事件发布端口，隔离 SSE。 |

### Domain 层 `domain/`

| 文件                      | 作用                                  |
| ----------------------- | ----------------------------------- |
| `Score.java`            | 乐谱聚合根，统一验证轨道、小节时值和可演奏性。             |
| `Track.java`            | 轨道、调弦和小节集合；验证弦品匹配。                  |
| `Measure.java`          | 音乐事件集合及小节总时值不变量。                    |
| `MusicalEvent.java`     | Note/Rest/Chord 的密封接口和 JSON 多态入口。   |
| `NoteEvent.java`        | 音高、时值和弦品位置的单音事件。                    |
| `RestEvent.java`        | 精确时值休止事件。                           |
| `ChordEvent.java`       | 同时发声的和弦事件。                          |
| `ChordNote.java`        | 和弦内部音高和弦品。                          |
| `Pitch.java`            | MIDI 音高值对象及 MusicXML 音名转换。          |
| `RhythmicDuration.java` | 分数时值、加法、比较和 PPQ tick 转换。            |
| `TimeSignature.java`    | 拍号及完整小节时值。                          |
| `KeySignature.java`     | 大小调、主音、音级和五度圈解析。                    |
| `GuitarTuning.java`     | 标准/Drop D 调弦及音高到弦品映射。               |
| `FretPosition.java`     | 弦号与品位值对象。                           |
| `GuitarTechnique.java`  | palm mute、击勾弦、滑音、推弦、揉弦等技法枚举；尚未进入事件。 |

### Export 层 `export/`

| 文件 | 作用 |
|---|---|
| `MidiExporter.java` | 将 Score 转为 Java MIDI Sequence 并写入 `.mid`。 |
| `MusicXmlExporter.java` | 生成含调号、拍号、速度和吉他弦品的 MusicXML 4.0。 |

### Infrastructure 层 `infrastructure/`

| 文件 | 作用 |
|---|---|
| `LlmProperties.java` | 通用模型提供商、Base URL、模型名和Key配置。 |
| `ChatModelFactory.java` | 不同模型协议适配器的统一工厂接口。 |
| `OpenAiCompatibleChatModelFactory.java` | 创建DeepSeek、OpenAI及其他兼容端点的ChatModel。 |
| `LlmConfiguration.java` | 按`llm` Profile组装ChatModel、Parser和主Agent。 |
| `AsyncConfiguration.java` | 配置音乐任务线程池。 |
| `InterruptedTaskRecovery.java` | 启动时恢复异常中断的运行中任务。 |
| `GuitarProConnector.java` | Windows 下安全打开 Guitar Pro 文件。 |
| `persistence/MyBatisProjectStore.java` | `ProjectStore` 的 MyBatis-Plus 实现。 |
| `security/AccessKeyAuthenticator.java` | 常量时间密钥比较和 HMAC 会话签名。 |

#### 数据库实体

| 文件 | 表/作用 |
|---|---|
| `MusicProjectEntity.java` | `music_project` 项目与当前版本。 |
| `ProjectVersionEntity.java` | `project_version` Score JSON 快照。 |
| `GenerationTaskEntity.java` | `generation_task` 提示词、状态和错误。 |
| `ConversationMessageEntity.java` | `conversation_message` 对话记录。 |
| `ExportedArtifactEntity.java` | `exported_artifact` 文件类型、版本和路径。 |

#### MyBatis Mapper

| 文件 | 访问表 |
|---|---|
| `MusicProjectMapper.java` | `music_project` |
| `ProjectVersionMapper.java` | `project_version` |
| `GenerationTaskMapper.java` | `generation_task` |
| `ConversationMessageMapper.java` | `conversation_message` |
| `ExportedArtifactMapper.java` | `exported_artifact` |

### 后端资源 `src/main/resources/`

| 文件 | 作用 |
|---|---|
| `application.yaml` | 默认 H2、导出目录、通用LLM、安全和MCP配置。 |
| `application-mysql.yaml` | MySQL Profile 数据源覆盖。 |
| `schema.sql` | H2/测试权威建表脚本。 |
| `prompts/music-requirements-system.txt` | 结构化约束提取提示词。 |
| `prompts/music-agent-system.txt` | 主 Agent 工具使用和边界提示词。 |

### 后端测试

| 文件 | 验证内容 |
|---|---|
| `agent/RuleBasedRequirementParserTest.java` | 中英文约束、情绪、节奏和稳定种子。 |
| `agent/LlmRequirementParserLiveTest.java` | 可选真实配置模型端到端测试。 |
| `infrastructure/LlmPropertiesTest.java` | 通用模型配置归一化和缺失Key校验。 |
| `api/ProjectControllerIntegrationTest.java` | REST 项目和生成链路。 |
| `api/AccessKeyApiIntegrationTest.java` | 密钥、登录和 Cookie。 |
| `api/mcp/MusicMcpPropertiesTest.java` | MCP 配置校验。 |
| `api/mcp/MusicMcpIntegrationTest.java` | MCP 协议、鉴权和工具调用。 |
| `application/GuitarRiffGeneratorTest.java` | 差异、复现、调性、拍号与可演奏性。 |
| `application/CreationConstraintsTest.java` | REST、Agent与MCP共享的结构化约束转换。 |
| `application/MeasureRewriteTest.java` | 非目标小节保持不变。 |
| `application/MusicProjectServiceIntegrationTest.java` | 生成、版本和导出纵向链路。 |
| `domain/RhythmicDurationTest.java` | 分数时值与 tick。 |
| `domain/MeasureTest.java` | 小节总时值。 |
| `domain/GuitarTuningTest.java` | 标准/Drop D 弦品映射。 |
| `domain/GuitarPlayabilityTest.java` | 指法和弦品合法性。 |
| `export/ExportersTest.java` | MIDI 重读、MusicXML 结构和调号。 |
| `export/GuitarProAcceptanceArtifactTest.java` | GP8 人工验收样例。 |
| `infrastructure/persistence/MySqlProjectStoreTest.java` | Testcontainers MySQL 8。 |
| `infrastructure/security/AccessKeyAuthenticatorTest.java` | 密钥和 Token 签名。 |
| `MusicAiAgentApplicationTests.java` | Spring 上下文加载。 |
| `src/test/resources/application.yaml` | 隔离的测试 H2 和临时路径配置。 |

## 前端 `music-frontend/`

| 文件 | 作用 |
|---|---|
| `package.json` | 脚本及 Vue、Element Plus、Vite 依赖。 |
| `pnpm-lock.yaml` | 精确依赖版本与完整性校验。 |
| `pnpm-workspace.yaml` | pnpm 工作区与依赖策略。 |
| `vite.config.js` | Vite/Vue 插件和开发代理。 |
| `index.html` | Vue 挂载 HTML。 |
| `Dockerfile` | pnpm 构建后复制到 Nginx。 |
| `.dockerignore` | 排除 node_modules、dist、IDE 和日志。 |
| `nginx.conf` | SPA fallback、静态服务与 `/api/` 反向代理/SSE。 |
| `src/main.js` | Vue、Element Plus 和全局样式入口。 |
| `src/App.vue` | 登录、项目、生成、进度、预览、导出和 MIDI 播放工作台。 |
| `src/midiPlayer.js` | MIDI 解析及 Web Audio 播放。 |
| `src/style.css` | 布局、面板、谱面、指板和响应式样式。 |

## Python 服务 `music-ai-python/`

| 文件 | 作用 |
|---|---|
| `Dockerfile` | Python 3.11/Uvicorn 服务镜像。 |
| `requirements.txt` | FastAPI、Uvicorn、上传和测试依赖。 |
| `app/__init__.py` | Python 包标记。 |
| `app/main.py` | `/health` 与 WAV 上传分析 API。 |
| `app/analysis.py` | PCM 解码、元数据、峰值/RMS 和 BPM 初估。 |
| `tests/test_audio_analysis.py` | WAV 核心与 HTTP 接口测试。 |

## 常见修改入口

| 需求 | 位置 |
|---|---|
| 自然语言理解 | `agent/`、`resources/prompts/` |
| 音乐生成 | `application/GuitarRiffGenerator.java` |
| 时值、指法、调性规则 | `domain/` |
| REST/SSE | `api/` |
| MCP 工具 | `api/mcp/MusicMcpToolCatalog.java` |
| 数据保存 | `ProjectStore`、`infrastructure/persistence/` |
| 表结构 | `schema.sql`、`docker/mysql/init.sql`、后续迁移 |
| MIDI/MusicXML | `export/` |
| 页面 | `music-frontend/src/` |
| WAV 分析 | `music-ai-python/app/analysis.py` |

新增、删除、移动或改变文件职责后，应同步更新本页及相关 Obsidian 笔记。
