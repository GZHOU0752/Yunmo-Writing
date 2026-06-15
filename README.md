# 云墨 (YunMo) — 古典美学 AI 多智能体小说写作系统

基于 6 个专职 AI Agent 协作的长篇小说创作平台。通过确定性流水线编排，实现上下文组装 → 情节预检 → 流式写作 → 33维质量审查 → 去AI化检测的全自动创作闭环。

---

## 项目结构

```
yunmo/
├── yunmo-server/               # Java 后端 (Spring Boot 3.2 WebFlux)
│   ├── yunmo-common/           # 公共模块 — 枚举(33审计维度)/DTO/配置属性
│   ├── yunmo-domain/           # 领域模型 — 17 JPA Entity + 14 Repository
│   ├── yunmo-llm/              # LLM 适配层 — 3 Provider + EmbeddingService
│   ├── yunmo-agent/            # AI Agent 核心 — Pipeline引擎 + 6Agent + 审计
│   ├── yunmo-service/          # 业务服务 — RAG向量库/大纲/导出/统计
│   ├── yunmo-api/              # Web API — 19 Controller + SSE 流式端点
│   ├── yunmo-server-app/       # 启动模块 — Spring Boot 入口 + 配置
│   └── pom.xml                 # Maven 多模块父 POM

└── yunmo-frontend/             # Vue 3 前端 (Vite + Tailwind CSS 4)
    └── src/
        ├── views/              # 6 个页面
        ├── components/         # 12 个组件
        ├── composables/        # Pinia Store + SSE + API 封装
        └── styles/             # 古风设计系统(宣纸/松烟墨/朱砂印)
```

### 代码规模

| 项目 | 语言 | 源文件 |
|:--|:--|:--|
| yunmo-server | Java 17 | 119 |
| yunmo-frontend | Vue 3 / JavaScript | 25 |

---

## 技术栈

### 后端

| 层级 | 技术 | 说明 |
|:--|:--|:--|
| JDK | Java 17 | Record、Text Block、Sealed Class |
| 应用框架 | Spring Boot 3.2 | WebFlux 响应式全栈 |
| ORM | Spring Data JPA + Hibernate 6 | H2 嵌入式数据库(单文件) |
| 向量嵌入 | 阿里云 DashScope | text-embedding-v3 |
| API 文档 | SpringDoc OpenAPI | Swagger UI: `/swagger-ui.html` |

### 前端

| 层级 | 技术 | 说明 |
|:--|:--|:--|
| 框架 | Vue 3 Composition API | `<script setup>` 语法 |
| 构建 | Vite 6 | 开发代理 `/api/*` → `localhost:8080` |
| 状态管理 | Pinia 2 | Composition API 风格 |
| UI 组件 | Ant Design Vue 4 | 企业级组件库 |
| 样式 | Tailwind CSS 4 | 古风 CSS 变量设计系统 |
| 富文本 | TipTap 2 | ProseMirror 内核 |

### LLM 提供商

| 提供商 | 默认模型 | Agent 分配 |
|:--|:--|:--|
| DeepSeek | `deepseek-chat` | Writer / Architect / Supervisor |
| Kimi (月之暗面) | `moonshot-v1-8k` | Inspector |
| Qwen (通义千问) | `qwen-plus` | Guardian / Custodian / Embedding |

> 所有提供商均通过 OpenAI 兼容 HTTP API 接入，可在 `application.yml` 中替换。

---

## 核心功能

### 六智能体协作架构

```
Supervisor (主编)
    ├── Architect (情节架构师)  → 因果链 + 伏笔 + 大纲规划
    ├── Writer (主笔)           → SSE 流式生成章节正文
    ├── Inspector (质检官)      → 33维质量分析(情节8+人物7+文笔12+合规6)
    ├── Guardian (类型守卫)     → 机械扫描禁止术语(每类型45-75条)
    └── Custodian (角色守护者)  → 6层角色认知模型一致性检查
```

### 章节生成流水线

```
assemble_context          → 4层上下文组装 + RAG 向量检索
    ↓
preflight (并行)          → Architect ∥ Guardian
    ↓
write_chapter (SSE流式)   → Writer 逐 token 实时推送
    ↓
review_chapter            → Guardian → De-AI检测 → Inspector(33维)
    ↓
decide_verdict            → fatal→重写 / severe>2→修改 / pass→完成
```

### 4 层上下文组装

| 层 | 预算 | 内容 |
|:--|:--|:--|
| Layer 1 (圣经) | ~8K tokens | 类型铁律 + 角色总览 + 禁止术语 + 写作蓝图 |
| Layer 2 (活跃) | ~20K tokens | 上一章全文 + 登场角色当前状态 + 角色描述 |
| Layer 3 (历史) | ~15K tokens | 前 10 章摘要 |
| Layer 4 (计划) | ~3K tokens | 大纲节点 + 因果句 + RAG 参考素材 |

### 33 维审计体系

| 类别 | 维度数 | 示例 |
|:--|:--|:--|
| 情节 (PLOT) | 8 | 因果链断裂、节奏失控、伏笔遗忘、逻辑矛盾 |
| 人物 (CHARACTER) | 7 | 人设崩塌、对话同质化、动机缺失、关系矛盾 |
| 文笔 (PROSE) | 12 | 重复用词、句式单调、修饰泛滥、视角漂移 |
| 合规 (COMPLIANCE) | 6 | AI腔检测、术语穿越、时代错位、水文判定 |

### 6 种小说类型配置

| 类型 | ID | 禁止术语(示例) |
|:--|:--|:--|
| 东方玄幻 | `xuanhuan` | 魔力、魔核、圣光、法环、元素亲和度 |
| 西方奇幻 | `qihuan` | 灵气、丹田、元婴、神识、道心 |
| 东方仙侠 | `xianxia` | 魔力、魔核、法环、科技、基因进化 |
| 都市 | `dushi` | 灵气复苏、丹田、元婴、位面、系统面板 |
| 悬疑灵异 | `xuanyi` | 魔力、魔法阵、圣光、龙族、精灵 |
| 轻小说 | `qingxiaoshuo` | 老气横秋用词、章回体、文言句式 |

### 其他功能

- **RAG 向量库** — 每本书独立向量库，上传 .txt 参考素材(≤100MB)，按段落分块 + DashScope 嵌入 + 余弦相似度检索
- **大纲层级** — 总纲/卷/章/节 四级大纲树，支持 AI 自动补全(SSE 流式)
- **全文搜索替换** — 跨章节关键词搜索，合并上下文窗口 + 高亮，批量替换 + 版本快照
- **每日码字统计** — 今日进度条 + 七日墨染日历 + 本周/本月汇总
- **角色关系图谱** — ECharts 力导向图，展示角色间关系(师徒/恋人/仇敌等)
- **EPUB 导出** — 纯 Java EPUB2 生成(ZIP + XML)，无需第三方依赖
- **版本历史** — 每次修改保存版本快照，支持一键恢复
- **一键复制** — 当前章节提取纯文本 → 剪贴板
- **古风设计系统** — 16 个 CSS 变量(宣纸/松烟墨/朱砂印/檀木/竹青/泥金)

---

## 快速开始

### 环境要求

| 组件 | 版本 |
|:--|:--|
| JDK | 17+ |
| Maven | 3.9+ |
| Node.js | 18+ |

### 1. 配置 API Key

```bash
export DEEPSEEK_API_KEY="sk-xxxxxxxx"
export KIMI_API_KEY="sk-xxxxxxxx"
export QWEN_API_KEY="sk-xxxxxxxx"
```

### 2. 启动后端

```bash
cd yunmo-server
mvn compile
mvn spring-boot:run    # → http://localhost:8080
```

### 3. 启动前端

```bash
cd yunmo-frontend
npm install
npm run dev            # → http://localhost:3000
```

---

## API 端点(部分)

| 方法 | 路径 | 说明 |
|:--|:--|:--|
| GET/POST | `/api/v1/novels` | 小说列表/创建 |
| GET | `/api/v1/novels/{id}` | 小说详情 |
| GET/POST | `/api/v1/novels/{nid}/chapters` | 章节列表/创建 |
| POST | `/api/v1/novels/{nid}/chapters/{cn}/generate` | **SSE 流式生成** |
| PATCH | `/api/v1/novels/{nid}/chapters/{cn}` | 保存章节 |
| POST | `/api/v1/novels/{nid}/search` | 全文搜索 |
| POST | `/api/v1/novels/{nid}/replace` | 全文替换 |
| GET | `/api/v1/novels/{nid}/export/{epub,txt,md}` | 多格式导出 |
| GET | `/api/v1/novels/{nid}/outline` | 大纲树 |
| GET | `/api/v1/novels/{nid}/stats` | 码字统计 |
| GET | `/api/v1/novels/{nid}/relations` | 角色关系图 |
| CRUD | `/api/v1/novels/{nid}/references` | RAG 参考素材 |

---

## 许可证

MIT License
