# 云墨 (YunMo) — AI 多智能体小说写作系统

基于 6 个专职 AI Agent 协作的长篇小说创作平台。通过 LangGraph 风格的确定性流水线编排，实现从上下文组装、情节预检、流式写作到多维质量审查的全自动创作闭环。

---

## 项目结构

```
yunmo/
├── yunmo-server/               # Java 后端 (Spring Boot 3.2)
│   ├── yunmo-common/           # 公共模块 — 枚举/DTO/配置属性
│   ├── yunmo-domain/           # 领域模型 — 17 JPA Entity + 14 Repository
│   ├── yunmo-llm/              # LLM 适配层 — 3 Provider + LangChain4j 桥接
│   ├── yunmo-agent/            # AI Agent 核心 — Pipeline引擎 + 6Agent + 工具
│   ├── yunmo-service/          # 业务服务 — 上下文/伏笔/记忆学习/导出
│   ├── yunmo-api/              # Web API — 13 Controller + SSE 流式端点
│   ├── yunmo-server-app/       # 启动模块 — Spring Boot 入口 + 配置
│   └── pom.xml                 # Maven 多模块父 POM
│
└── yunmo-frontend/             # Vue 3 前端 (Vite)
    └── src/
        ├── views/              # 4 个页面
        ├── components/         # 4 个组件
        ├── composables/        # Pinia Store + SSE + API 封装
        └── styles/             # Tailwind CSS + 古风主题
```

### 代码规模

| 项目 | 语言 | 文件数 | 行数(估) |
|:--|:--|:--|:--|
| yunmo-server | Java 17 | 95 源文件 | ~6,500 |
| yunmo-frontend | Vue 3 / TypeScript | 16 源文件 | ~1,500 |

---

## 技术栈

### 后端 (Java)

| 层级 | 技术 | 版本 | 说明 |
|:--|:--|:--|:--|
| JDK | Java 17 LTS | 17 | Record、Text Block、Sealed Class |
| 应用框架 | Spring Boot | 3.2.7 | WebFlux 响应式全栈 |
| AI 编排 | LangChain4j | 1.0.0-alpha1 | Agent/Tool/ChatMemory 抽象 |
| ORM | Spring Data JPA + Hibernate | 6.x | 对象关系映射 |
| 数据库 | H2 (嵌入式) | 2.x | 单文件零部署 |
| 缓存 | Redis | 7.x | 上下文快照 + Session 缓存 |
| 向量存储 | ChromaDB | 0.5+ | 语义检索（独立部署） |
| 数据库迁移 | Liquibase | 4.24 | 数据库版本管理 |
| API 文档 | SpringDoc OpenAPI | 2.5 | Swagger UI: `/swagger-ui.html` |

### 前端 (Vue 3)

| 层级 | 技术 | 版本 | 说明 |
|:--|:--|:--|:--|
| 框架 | Vue 3 Composition API | 3.5 | `<script setup>` 语法 |
| 构建 | Vite | 6.1 | 开发代理 `/api/*` → `localhost:8080` |
| 路由 | Vue Router | 4.5 | Hash 模式 |
| 状态管理 | Pinia | 2.3 | Vue 官方推荐状态管理 |
| UI 组件 | Ant Design Vue | 4.2 | 企业级 UI 组件库 |
| 样式 | Tailwind CSS | 4.0 | 框架无关 |
| 富文本 | TipTap (Vue 3) | 2.11 | ProseMirror 内核 |
| 语言 | TypeScript | 5.7 | 严格模式 |

### LLM 提供商

| 提供商 | 默认模型 | 用途 | Agent 分配 |
|:--|:--|:--|:--|
| DeepSeek | `deepseek-chat` | 主力生成 | Writer / Architect / Supervisor |
| Kimi (月之暗面) | `moonshot-v1-8k` | 审校 | Inspector |
| Qwen (通义千问) | `qwen-turbo` | 轻量检查 | Guardian / Custodian |

> 所有提供商均通过 OpenAI 兼容 HTTP API 接入，可在 `application.yml` 中替换为其他兼容服务。

---

## 核心功能

### 六智能体协作架构

```
Supervisor (主编)
    ├── Architect (情节架构师)  → 因果链 + 伏笔 + 时间线
    ├── Writer (主笔)           → 流式生成章节正文
    ├── Inspector (质检官)      → 10维质量分析
    ├── Guardian (类型守卫)     → 禁止术语机械扫描
    └── Custodian (角色守护者)  → 6层角色模型一致性检查
```

### 章节生成流水线

```
assemble_context          → 4层上下文组装 (~46K tokens)
    ↓
preflight (并行)          → Architect ∥ Guardian
    ↓
write_chapter (SSE流式)   → Writer 逐 token 实时推送
    ↓
review_chapter (串行)     → Guardian → Inspector
    ↓
decide_verdict            → fatal→regenerate / severe>2→rewrite / pass→END
    ↓
    自动循环至通过或达最大重试
```

### 4 层上下文组装

| 层 | 预算 | 内容 |
|:--|:--|:--|
| Layer 1 (圣经) | ~8K tokens | 类型铁律 + 角色总览 + 禁止术语 |
| Layer 2 (活跃) | ~20K tokens | 上一章全文 + 登场角色当前状态 |
| Layer 3 (历史) | ~15K tokens | 前 10 章摘要 |
| Layer 4 (计划) | ~3K tokens | 大纲节点 + 因果句 |

### 6 层角色认知模型

每个角色拥有结构化的认知档案，Custodian Agent 据此检查创作一致性：

```
Layer 1: 世界观    — 角色对世界的认知框架
Layer 2: 自我认同  — 角色如何定义自己
Layer 3: 价值观    — 角色的道德判断标准
Layer 4: 能力      — 角色的天赋和上限
Layer 5: 技能      — 角色后天习得的能力
Layer 6: 环境      — 角色的社会位置和人际关系
```

### 4 种小说类型配置

| 类型 | ID | 禁止术语示例 |
|:--|:--|:--|
| 东方仙侠 | `xianxia` | 魔力、魔核、法环、元素、基因 |
| 西方奇幻 | `western-fantasy` | 灵气、丹田、元婴、神识、道心 |
| 科幻末世 | `scifi-apocalypse` | 灵气、丹田、魔法、魔力 |
| 都市 | `urban` | 灵气、丹田、魔力、基因进化 |

### 其他功能

- **记忆学习** — AI 对比原稿与作者修改稿的 diff，提取可复用写作规则
- **伏笔管理** — 全生命周期跟踪（计划→埋设→提示→回收），三级紧急度提醒
- **实体冷却** — 角色出场频率控制（核心角色永不冷却，次要角色自动降温）
- **多格式导出** — Markdown / TXT / HTML
- **版本历史** — 每次手动修改保存版本快照

---

## 配置说明

### 环境要求

| 组件 | 最低版本 | 用途 |
|:--|:--|:--|
| JDK | 17 | 编译运行后端 |
| Maven | 3.9+ | 构建后端（IDE 内置） |
| Node.js | 18+ | 构建前端 |
| Redis | 7.x | 缓存（可选） |
| ChromaDB | 0.5+ | 向量检索（可选） |

### 1. 后端配置

**配置文件**: `yunmo-server/yunmo-server-app/src/main/resources/application.yml`

核心配置项（通过环境变量覆盖）：

```bash
# LLM API Key（必填，至少配一个）
export DEEPSEEK_API_KEY="sk-xxxxxxxx"
export KIMI_API_KEY="sk-xxxxxxxx"
export QWEN_API_KEY="sk-xxxxxxxx"

# LLM API 地址（可选，使用默认值可省略）
export DEEPSEEK_API_BASE="https://api.deepseek.com/v1"
export KIMI_API_BASE="https://api.moonshot.cn/v1"
export QWEN_API_BASE="https://dashscope.aliyuncs.com/compatible-mode/v1"

# JWT 密钥（生产环境必改）
export MOLU_SECRET_KEY="<随机字符串>"

# 外部服务（可选）
export CHROMA_URL="http://localhost:8000"
export REDIS_URL="redis://localhost:6379/0"
```

**application.yml 关键配置**:

```yaml
server:
  port: 8080                              # 后端端口

spring:
  datasource:
    url: jdbc:h2:file:./data/novelwriter  # H2 嵌入式数据库（单文件）
  jpa:
    hibernate:
      ddl-auto: update                    # 自动建表（首次启动）
  redis:
    url: ${yunmo.app.redis-url}           # Redis 连接

yunmo:
  app:
    max-retries: 3                        # 章节生成最大重试次数
    context-token-budget: 46000           # 上下文 token 预算
  llm:
    deepseek:
      model: deepseek-chat                # 可改为 deepseek-reasoner
    kimi:
      model: moonshot-v1-8k
    qwen:
      model: qwen-turbo
```

### 2. 前端配置

**Vite 代理配置**: `yunmo-frontend/vite.config.ts`

```typescript
server: {
  port: 3000,                             // 前端端口
  proxy: {
    '/api': {
      target: 'http://localhost:8080',    // 代理到 Java 后端
      changeOrigin: true,
    },
  },
}
```

> 如果后端端口不是 8080，修改此处的 `target` 即可。

### 3. 启动步骤

```bash
# ========== 后端 ==========
cd yunmo/yunmo-server

# 配置环境变量
export DEEPSEEK_API_KEY="sk-xxxxxxxx"

# 编译
mvn compile

# 启动（默认端口 8080）
mvn spring-boot:run

# ========== 前端 ==========
cd yunmo/yunmo-frontend

# 安装依赖（首次）
npm install

# 启动开发服务器（默认端口 3000）
npm run dev
```

浏览器访问 `http://localhost:3000` → 进入登录页 → 点击"进入书房" → 创建小说 → 初始化 → 开始写作。

---

## API 端点一览

### 小说
| 方法 | 路径 | 说明 |
|:--|:--|:--|
| GET | `/api/v1/novels` | 小说列表 |
| POST | `/api/v1/novels` | 创建小说 |
| GET | `/api/v1/novels/{id}` | 小说详情 |
| PATCH | `/api/v1/novels/{id}` | 更新小说 |
| DELETE | `/api/v1/novels/{id}` | 删除小说 |

### 章节 & 生成
| 方法 | 路径 | 说明 |
|:--|:--|:--|
| GET | `/api/v1/novels/{nid}/chapters` | 章节列表 |
| GET/POST | `/api/v1/novels/{nid}/chapters/{cn}/generate` | **SSE 流式生成** ⭐ |
| POST | `/api/v1/novels/{nid}/chapters/{cn}/generate-simple` | 非流式生成 |
| PATCH | `/api/v1/novels/{nid}/chapters/{cn}` | 保存章节(含版本历史) |

### 角色、世界观、组织
| 方法 | 路径 | 说明 |
|:--|:--|:--|
| CRUD | `/api/v1/novels/{nid}/characters` | 角色管理(6层认知模型) |
| CRUD | `/api/v1/novels/{nid}/world` | 世界观元素 |
| CRUD | `/api/v1/novels/{nid}/organizations` | 组织管理 |
| GET/POST | `/api/v1/novels/{nid}/careers` | 职业体系(内置模板) |

### 伏笔、分析、导出
| 方法 | 路径 | 说明 |
|:--|:--|:--|
| CRUD | `/api/v1/novels/{nid}/foreshadows` | 伏笔管理 |
| GET | `/api/v1/novels/{nid}/foreshadows/reminders` | 伏笔提醒 |
| GET | `/api/v1/novels/{nid}/analysis` | 质量报告 |
| GET | `/api/v1/novels/{nid}/export/{md,txt,html}` | 多格式导出 |

### 其他
| 方法 | 路径 | 说明 |
|:--|:--|:--|
| GET | `/api/v1/genre/list` | 类型列表 |
| GET | `/api/v1/health` | 健康检查 |
| GET | `/h2-console` | H2 数据库控制台 |

---

## 架构设计

### 并发模型 (JDK 17)

```
Controller 层:  WebFlux (Mono/Flux) — 必须响应式
     ↓
Service 层:     响应式 (Mono/Flux) — 全程非阻塞
     ↓
LLM 调用层:     WebClient — 天然非阻塞 (connect=10s, read=120s)
     ↓
Agent 调用层:   LangChain4j 异步 + CompletableFuture 并行
     ↓
DB 访问层:      JPA + @Async + boundedElastic — 自动切线程池
     ↓
ChromaDB:       WebClient HTTP API — 天然非阻塞
```

### PreFlight 并行 (Java 17)

```java
CompletableFuture.allOf(
    CompletableFuture.supplyAsync(() -> architectAgent.analyze(...), executor),
    CompletableFuture.supplyAsync(() -> guardianAgent.preCheck(...), executor)
).join();
```

### SSE 流式输出

后端通过 `Flux<ServerSentEvent>` 推送增量事件：
```
data: {"phase":"preflight","stage":"preflight",...}
data: {"phase":"writing","data":{"token":"天"}}
data: {"phase":"writing","data":{"token":"色"}}
data: {"phase":"writing","data":{"token":"渐"}}
...
data: {"phase":"done"}
```

前端 `useSSE` Composable 通过 Fetch API + ReadableStream 逐行接收。

---

## 许可证

MIT License
