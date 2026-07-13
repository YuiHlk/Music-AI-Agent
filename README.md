# Music AI Agent

面向吉他创作的模块化单体 MVP：自然语言需求经 LangChain4j/DeepSeek 解析后，由纯 Java 领域内核生成并校验可演奏的 8 小节 Riff，持久化版本并导出 MIDI 与 MusicXML。

## 组件

- Java 21、Spring Boot 3.5、LangChain4j、MyBatis-Plus
- H2 本地开发数据库；`mysql` Profile 使用 MySQL 8
- Vue 3、Vite、Element Plus 工作台
- 可选 FastAPI WAV 音频分析服务

## 目录结构

```text
demo/
├── music-backend/       # Java、Spring Boot、Maven Wrapper 与后端测试
├── music-frontend/      # Vue 3 创作工作台
├── music-ai-python/     # 可选 FastAPI 音频分析服务
├── docker/              # MySQL 初始化脚本
├── scripts/             # 本地安全启动脚本
├── .local/              # 本地数据库和导出物，不提交 Git
├── docker-compose.yml
├── AGENTS.md
└── README.md
```

完整实现文档位于 Obsidian Vault：`docs/Music-AI-Agent-Docs`，首页为 `00-首页.md`。

## 本地启动

后端无模型模式（使用确定性需求解析器）：

```powershell
cd music-backend
.\mvnw.cmd spring-boot:run
```

使用本地密钥文件启动 DeepSeek 模式（密钥只注入当前子进程）：

```powershell
.\scripts\run-with-deepseek.ps1
```

前端：

```powershell
cd music-frontend
pnpm install
pnpm dev
```

打开 `http://localhost:5173`。后端 API 为 `http://localhost:8080/api`。

## 验证

```powershell
cd music-backend
.\mvnw.cmd test
cd ..
cd music-frontend
pnpm build
cd ..\music-ai-python
python -m unittest discover -s tests -v
```

真实 DeepSeek 测试仅在提供 `DEEPSEEK_API_KEY` 时运行，普通测试不依赖外部模型和网络。

## Docker Compose

先在当前终端设置 `DEEPSEEK_API_KEY`，再运行：

```powershell
docker compose up --build
```

服务端口：前端 `5173`、Java API `8080`、音频分析 `8000`、MySQL `3306`。
