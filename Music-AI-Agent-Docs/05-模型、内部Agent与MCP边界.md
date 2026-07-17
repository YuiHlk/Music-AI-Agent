---
aliases: [Agent与MCP边界, 模型调用边界]
tags: [project/music-ai-agent, topic/agent, topic/mcp]
status: active
---

# 模型、内部Agent与MCP边界

导航：[[00-首页]] · [[01-架构与数据流]] · [[02-开发运行与配置]]

## 先区分四个角色

| 角色 | 当前实现 | 职责 |
|---|---|---|
| 服务器聊天模型 | `ChatModel` | 理解自然语言、结构化输出和内部Agent推理 |
| 内部Agent | `MusicCreatorAgent` | 服务网页`/chat`，维护项目级记忆并选择Java工具 |
| MCP Server | `/mcp`、`MusicMcpToolCatalog` | 把应用用例标准化开放给项目外部Agent |
| 音乐业务核心 | `MusicProjectService`、Domain、Export | 生成、校验、版本、持久化和导出 |

模型负责“理解和选择”，MCP负责“对外暴露能力”，Application与Domain负责“可靠执行”。MCP不是模型，也不是第二套音乐业务。

## 四条真实调用路径

### 网页普通生成

```text
Vue → REST /generate → MusicProjectService
    → RequirementParser（llm Profile时使用服务器模型，否则使用规则）
    → GuitarRiffGenerator → Score.validate → 保存与导出
```

### 网页聊天Agent

```text
Vue → REST /chat → MusicCreatorAgent → 服务器ChatModel
    → 结构化工具参数 → MusicCreationTools → MusicProjectService → 业务核心
```

内部Agent与应用服务处于同一Spring进程，因此直接调用Java工具，不通过`/mcp`进行HTTP自调用。`generateGuitarRiff`直接接收模型已经推断的结构化字段，不再把同一句自然语言交给`RequirementParser`进行第二次模型解析。

### MCP自然语言生成

```text
外部Agent → /mcp → generate_guitar_riff(prompt)
    → MusicProjectService → 服务器RequirementParser → 业务核心
```

这条路径仍会使用服务器模型（启用`llm` Profile时），适合由平台承担模型调用的场景。

### MCP结构化生成（外部模型自带Key）

```text
外部Agent自己的模型 → 解析CreationConstraints
    → /mcp → generate_guitar_riff_from_constraints
    → MusicProjectService → 业务核心
```

这条路径跳过服务器`RequirementParser`，不会使用服务器`LLM_API_KEY`。用户Key保留在外部AI客户端中，后端只接收已结构化的音乐参数。

## 为什么同时保留REST、内部Agent和MCP

- REST为Vue及普通程序提供稳定、明确的产品接口。
- 内部Agent让网页用户直接获得对话、记忆和工具编排能力。
- MCP让Claude Desktop、AI IDE、自定义Agent等外部客户端复用音乐能力。
- 三种入口最终统一进入`MusicProjectService`，避免重复业务实现。

如果产品只服务自有网页，REST与内部Agent已经足够，MCP可以保持关闭；只有需要外部Agent生态、用户自带模型或跨工具编排时才启用MCP。

## 配置边界

```text
SPRING_PROFILES_ACTIVE=llm  决定是否创建服务器ChatModel与内部Agent
LLM_*                      配置服务器统一使用的模型
MUSIC_AI_MCP_ENABLED       决定是否开放/mcp
MUSIC_AI_MCP_TOKEN         保护MCP入口
```

`LLM_API_KEY`与`MUSIC_AI_MCP_TOKEN`用途不同：前者授权服务器调用模型，后者授权外部客户端调用音乐工具。

## 当前限制

- MCP目前使用部署级共享Token，还没有用户级Token和权限撤销。
- 项目尚未建立用户表、项目所有权和跨用户数据隔离，不能直接作为公开多租户服务。
- 网页端尚不保存用户自带模型Key；在完成加密凭据存储前不得增加明文Key字段。
- 内部Agent工具与MCP工具是两套协议适配器，应继续共享Application用例，而不是互相通过HTTP调用。
