# CodePilot — AI 原生代码审查平台

**demo视频链接**：https://pan.quark.cn/s/e281b1ff4250

基于**多智能体协作流水线**的 GitHub PR 自动化审查系统。融合传统静态规则引擎与大语言模型语义分析，提供流式实时反馈、多模型交叉验证、可解释 AI 输出和误报控制闭环，帮助团队系统性地提升代码审查效率与质量。


## 目录

- [核心亮点](#核心亮点)
- [系统架构](#系统架构)
- [审查流水线](#审查流水线)
- [功能详情](#功能详情)
  - [多智能体协作流水线](#1-多智能体协作流水线)
  - [多模型 AI 评审与对比](#2-多模型-ai-评审与对比)
  - [风险评分引擎](#3-风险评分引擎)
  - [AI 置信度评估](#4-ai-置信度评估)
  - [可解释风险聚焦面板](#5-可解释风险聚焦面板)
  - [误报控制系统](#6-误报控制系统)
  - [代码质量趋势追踪](#7-代码质量趋势追踪)
  - [Markdown 报告导出](#8-markdown-报告导出)
- [页面概览](#页面概览)
- [快速开始](#快速开始)
- [API 文档](#api-文档)
- [项目结构](#项目结构)
- [数据库模型](#数据库模型)
- [AI 提供商配置](#ai-提供商配置)

## 核心亮点

| 能力 | 说明 |
|------|------|
| **6 Agent 流水线** | 仓库分析 → Diff 分析 → 上下文构建 → 风险检测 → AI 评审 → 汇总合并，全程自动协作 |
| **规则 + AI 双层检测** | 10+ 静态规则（空指针、SQL 注入、并发、资源泄漏）+ 大模型语义理解，互补覆盖 |
| **5 种 AI 提供商** | 同时支持 OpenAI、DeepSeek、Claude、Qwen、SiliconFlow，可单模型或双模型对比 |
| **SSE 流式渲染** | Agent 状态、chunk 进度、AI Token 流、评分计算全程实时推送 |
| **误报控制闭环** | 用户反馈 → 置信度统计 → 阈值自动调整，规则精准率持续提升 |
| **可解释 AI** | 每条发现附带 Why / Evidence / Impact / Fix 四维度解释和置信度评分 |
| **质量趋势看板** | 7/14/30 天趋势图（风险评分 + 发现数量），仓库维度，可视化追踪代码质量变化 |

## 系统架构

```
┌────────────────────────────────────────────────────────────┐
│                     CodePilot UI (Vue 3)                   │
│  ┌──────────┐  ┌────────────┐  ┌──────────┐  ┌──────────┐ │
│  │ HomePage │  │AnalysisPage│  │HistoryPage│  │Comparison│ │ │
│  │  PR 输入  │  │ 3 栏看板   │  │ 趋势图表  │  │ 7 维对比  │ │ │
│  └──────────┘  └────────────┘  └──────────┘  └──────────┘ │
└───────────────────────┬────────────────────────────────────┘
                        │ SSE / REST
┌───────────────────────▼────────────────────────────────────┐
│                Spring Boot 3.2 (WebFlux)                   │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │                   Analysis Pipeline                   │ │
│  │  RepoAnalyzer → DiffAnalyzer → ContextBuilder        │ │
│  │      → RiskDetector → AIReviewer → SummaryMerger     │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │  GitHub  │  │  AI Provider  │  │   Rule Engine        │ │
│  │  Client  │  │  Adapter      │  │   (Regex + Semantic) │ │
│  │          │  │  (5 models)   │  │   10+ built-in rules │ │
│  └──────────┘  └──────────────┘  └──────────────────────┘ │
└───────────────────────┬────────────────────────────────────┘
                        │
┌───────────────────────▼────────────────────────────────────┐
│              MySQL 8              │       Redis             │
│  analysis_record                  │    PR diff cache        │
│  rule_feedback                    │    Session cache        │
│  rule_confidence                  │                         │
│  evaluation_sample                │                         │
└───────────────────────────────────┴─────────────────────────┘
```

## 审查流水线

```
PR URL 输入
    │
    ▼
┌─────────────────────────┐
│  RepositoryAnalyzeAgent  │  识别项目结构、语言栈、框架版本
├─────────────────────────┤
│    DiffAnalyzeAgent      │  解析变更文件、增删行数、diff 块
├─────────────────────────┤
│    ContextBuildAgent     │  补全代码上下文、方法体、类定义
├─────────────────────────┤
│    RiskDetectAgent       │  运行规则引擎，匹配已知风险模式
├─────────────────────────┤
│   ReviewGenerateAgent    │  调用 LLM，生成语义级审查意见
├─────────────────────────┤
│    SummaryMergeAgent     │  合并规则发现 + AI 发现，计算评分
└─────────────────────────┘
    │
    ▼
┌─────────────────────────────┐
│  AnalysisResult             │
│  • 风险评分 (0-100)          │
│  • AI 发现 + 规则发现         │
│  • 置信度评分                 │
│  • 文件级风险热力图            │
│  • 5 因子评分分解             │
└─────────────────────────────┘
```

每个 Agent 在执行期间实时推送 SSE 事件（`AGENT_STARTED` / `AGENT_RUNNING` / `AGENT_COMPLETED` / `AGENT_FAILED`），前端通过动画状态机渲染时间线。

## 功能详情

### 1. 多智能体协作流水线

6 个专业化 Agent 按流水线顺序协作，每个 Agent 职责单一、可独立失败重试：

| Agent | 职责 | 关键技术 |
|-------|------|----------|
| **RepositoryAnalyzeAgent** | 识别仓库语言、构建工具、框架和依赖 | GitHub Tree API，多语言特征匹配 |
| **DiffAnalyzeAgent** | 解析 PR diff，按文件和 chunk 拆分 | Git diff parser，语义 diff 分块 |
| **ContextBuildAgent** | 为 diff 块补全上下文（方法签名、类结构） | 源码反查，AST 边界检测 |
| **RiskDetectAgent** | 执行 10+ 静态规则，匹配已知风险模式 | 正则 + 语义模式匹配 |
| **ReviewGenerateAgent** | 组装 prompt，调用 LLM 生成审查意见 | Prompt 工程，5 种模型适配 |
| **SummaryMergeAgent** | 合并规则发现和 AI 发现，去重排序，计算综合评分 | 加权融合，交叉验证 |

### 2. 多模型 AI 评审与对比

- **单模型模式**：选择任一提供商，快速完成审查
- **对比模式**：同时调用 2 个模型，结果在 7 个维度并排对比：
  - 发现数量对比（按严重程度分列）
  - 发现重叠分析（共识度 / 独有发现）
  - 评审文字并排展示（支持同步滚动）
  - 风险评分对比（5 因子分解）
  - 改进建议对比
  - 性能指标对比（Token、chunk 成功率）
  - 风险等级分布柱状图

### 3. 风险评分引擎

综合评分公式，5 个因子加权计算：

| 因子 | 权重 | 说明 |
|------|------|------|
| 文件数量 | 20% | 变更文件越多，风险基数越大 |
| 规则发现数 | 35% | 规则引擎命中的问题数量和严重程度 |
| 模块风险 | 15% | 变更模块的历史风险档案 |
| 变更量 | 15% | 增删行数，大改动更可能引入 bug |
| AI 评分 | 15% | 大模型给出的综合风险判断 |

最终得分 0-100，对应风险等级：**低风险** (0-25) / **中等风险** (26-50) / **高风险** (51-75) / **严重风险** (76-100)。

### 4. AI 置信度评估

每次分析附带 6 因子置信度评估，帮助评审人判断分析结果的可靠程度：

| 因子 | 说明 |
|------|------|
| 规则覆盖率 | 规则引擎覆盖的已知模式比例 |
| Chunk 成功率 | 代码上下文 chunk 的成功解析率 |
| 跨 Chunk 一致性 | 跨 chunk 发现的一致性程度 |
| 复杂度因子 | 代码圈复杂度对分析准确度的影响 |
| 仓库知识 | 对仓库历史和历史 bug 的了解程度 |
| Token 覆盖率 | 提示词 token 覆盖 diff 的完整度 |

### 5. 可解释风险聚焦面板

每条风险发现包含 4 层解释，标注来源（AI / 规则引擎）：

- **Why**：为什么判定为风险
- **Evidence**：具体代码片段证据
- **Impact**：潜在影响范围和严重程度
- **Fix**：修复建议

支持文件级聚焦视图，一键浏览每个文件的风险点，前后导航快速切换。

### 6. 误报控制系统

```
评审人审查发现
      │
      ├── 确认有用 ──→ 记录 CONFIRMED，提升规则置信度
      │
      └── 标记误报 ──→ 记录 FALSE_POSITIVE，降低规则置信度
                           │
                           ▼
                    置信度低于阈值 → 自动抑制该规则
                    置信度持续走低 → 触发规则重评估
```

反馈数据存储在 `rule_feedback` 和 `rule_confidence` 表中，可通过 `/api/v1/evaluation/run` 触发准确率/召回率/精确率计算。

### 7. 代码质量趋势追踪

- **概览卡片**：已分析 PR 总数、平均风险评分、平均发现数、统计周期
- **SVG 趋势图**：风险评分趋势（面积图）、发现数量趋势（折线图），时间窗口可选 7/14/30 天
- **历史记录表**：按仓库筛选，支持分页、回顾任意历史分析、导出历史报告
- **全局视图**：无仓库维度时展示所有仓库最近分析记录

### 8. Markdown 报告导出

分析完成后一键导出完整的 Markdown 评审报告，内容包括：
- PR 基本信息（仓库、标题、作者、变更统计）
- AI 评审结果（PR 摘要、风险分析、改进建议、综合评估）
- 规则发现详情（风险等级、警告信息、修复建议、文件位置）
- 风险评分与置信度评分

## 页面概览

| 页面 | 路由 | 功能 |
|------|------|------|
| **首页** | `/` | PR 链接输入、AI 模型选择（单选/多选对比）、示例 PR 快速填充、功能卡片展示 |
| **分析看板** | `/analysis` | 3 栏实时评审看板：Agent 时间线 + 风险热力图 + AI 流式输出 + 风险评分 + 置信度仪表盘 + 规则发现 + 文件聚焦面板 + 误报反馈 |
| **历史趋势** | `/history` | 质量趋势图（SVG 面积+折线）、概览卡片、历史记录表格、仓库筛选、时间窗口切换 |
| **模型对比** | `/comparison` | 7 维模型对比：发现统计、重叠分析、评审文字并排、评分对比、建议对比、性能指标、等级分布 |

## 快速开始

### 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.6+ |
| Node.js | 18+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |

### 1. 克隆项目

```bash
git clone https://github.com/your-org/CodePilot.git
cd CodePilot
```

### 2. 数据库准备

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS codepilot DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci"
```

应用启动时会自动通过 `schema.sql` 初始化所有表结构。

### 3. 后端启动

```bash
cd codepilot-server

# 必需环境变量
export MYSQL_USER=root
export MYSQL_PASSWORD=your_password
export GITHUB_TOKEN=ghp_xxxxxxxxxxxx      # GitHub API 访问令牌
export DEEPSEEK_API_KEY=sk-xxxxxxxxxxxx    # 默认 AI 提供商

# 可选：其他 AI 提供商
# export OPENAI_API_KEY=sk-...
# export CLAUDE_API_KEY=sk-ant-...
# export QWEN_API_KEY=sk-...
# export SILICONFLOW_API_KEY=sk-...

mvn spring-boot:run
```

后端运行在 `http://localhost:8080`。

### 4. 前端启动

```bash
cd codepilot-ui
npm install
npm run dev
```

前端运行在 `http://localhost:3000`，Vite 开发代理自动将 `/api` 请求转发至后端 8080 端口。

### 5. 健康检查

```bash
curl http://localhost:8080/api/health
# {"status":"UP","service":"codepilot","version":"1.0.0"}
```

## API 文档

### 基础信息

- 版本：`v1`
- 前缀：`/api/v1`
- 响应格式：`application/json`
- 流式接口：`text/event-stream` (SSE)

### PR 分析

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/pr/analyze` | 提交 PR 分析（同步，返回完整结果） |
| `POST` | `/pr/analyze/stream` | 提交 PR 分析（SSE 流式，支持多模型对比） |
| `GET` | `/pr/detail` | 获取 PR 基本信息（仓库、文件、提交等） |
| `GET` | `/pr/cache/{owner}/{repo}/{prNumber}` | 从 Redis 读取缓存的审查结果 |
| `DELETE` | `/pr/cache/{owner}/{repo}/{prNumber}` | 清除指定 PR 的缓存 |

**SSE 事件类型：**

| 事件 | 说明 |
|------|------|
| `AGENT_STARTED` | Agent 开始执行，携带 Agent 名称 |
| `AGENT_RUNNING` | Agent 执行中，携带进度信息 |
| `AGENT_COMPLETED` | Agent 执行完成 |
| `AGENT_FAILED` | Agent 执行失败，携带错误信息 |
| `CHUNK_PROGRESS` | 代码 chunk 处理进度，携带文件名和序号 |
| `AI_TOKEN` | LLM 流式输出的单个 token |
| `ANALYSIS_COMPLETE` | 分析完成，携带完整 AnalysisResult |
| `MULTI_PROVIDER_COMPLETE` | 多模型分析全部完成 |
| `ERROR` | 分析过程出错 |

### AI 提供商

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/ai/providers` | 列出当前已配置的可用 AI 提供商 |
| `POST` | `/ai/test/{provider}` | 测试指定提供商的连通性 |

### 历史与趋势

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/history/{owner}/{repo}` | 按仓库查询分析历史（分页） |
| `GET` | `/trends/{owner}/{repo}` | 按仓库查询质量趋势（7/14/30 天） |
| `GET` | `/compare/{owner}/{repo}/{prNumber}` | 对比同一 PR 的最近两次分析 |
| `GET` | `/history/recent` | 获取全局最近分析记录 |
| `GET` | `/history/repos` | 列出所有已分析过的仓库 |
| `GET` | `/analysis/{analysisId}` | 按分析 ID 获取完整结果（用于报告导出） |
| `POST` | `/history/recalculate` | 重新计算历史记录中的统计数据 |

### 误报反馈与评估

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/feedback` | 提交规则反馈（`CONFIRMED` / `FALSE_POSITIVE` / `MISSED_ISSUE`） |
| `GET` | `/feedback/stats` | 获取所有规则的反馈统计数据（精准率等） |
| `GET` | `/feedback/stats/{ruleName}` | 获取指定规则的详细统计 |
| `GET` | `/evaluation/samples` | 获取评估数据集样本 |
| `GET` | `/evaluation/rules` | 获取评估数据集中的规则列表 |
| `GET` | `/evaluation/run` | 运行评估，计算准确率 / 召回率 / F1 |

### 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/health` | 服务健康检查 |

## 项目结构

```
CodePilot/
├── codepilot-server/                          # Spring Boot 后端
│   └── src/main/
│       ├── java/com/codepilot/
│       │   ├── agent/                         # 6 个审查 Agent
│       │   │   ├── RepositoryAnalyzeAgent     # 仓库结构分析
│       │   │   ├── DiffAnalyzeAgent           # 差异变更解析
│       │   │   ├── ContextBuildAgent          # 代码上下文构建
│       │   │   ├── RiskDetectAgent            # 风险模式检测
│       │   │   ├── ReviewGenerateAgent        # AI 智能评审
│       │   │   └── SummaryMergeAgent          # 结果汇总合并
│       │   ├── ai/                            # AI 提供商适配层
│       │   │   ├── AiProvider                 # 统一提供商接口
│       │   │   ├── DeepSeekProvider           # DeepSeek 适配
│       │   │   ├── OpenAiProvider             # OpenAI 适配
│       │   │   ├── ClaudeProvider             # Anthropic Claude 适配
│       │   │   ├── QwenProvider               # 阿里 Qwen 适配
│       │   │   └── SiliconFlowProvider        # SiliconFlow 适配
│       │   ├── analysis/                      # 分析引擎与策略
│       │   │   ├── AnalysisEngine             # 分析编排引擎
│       │   │   ├── StrategySelector           # 语言/框架策略选择
│       │   │   └── ScoreCalculator            # 5 因子评分计算
│       │   ├── controller/                    # REST API 控制器
│       │   │   ├── PrAnalysisController       # PR 分析接口
│       │   │   ├── AiProviderController       # AI 提供商管理
│       │   │   ├── HistoryController          # 历史与趋势
│       │   │   ├── RuleFeedbackController     # 反馈与评估
│       │   │   └── HealthController           # 健康检查
│       │   ├── github/                        # GitHub API 客户端
│       │   ├── rule/                          # 规则引擎
│       │   │   ├── Rule                       # 规则抽象定义
│       │   │   ├── RuleEngine                 # 规则执行引擎
│       │   │   └── rules/                     # 10+ 内置规则实现
│       │   ├── review/                        # 审查流水线编排
│       │   └── store/                         # MyBatis Plus 数据持久化
│       └── resources/
│           ├── application.yml                # 应用配置
│           └── schema.sql                     # 数据库 DDL
│
├── codepilot-ui/                              # Vue 3 前端
│   └── src/
│       ├── views/                             # 页面组件
│       │   ├── HomePage.vue                   # 首页（PR 输入）
│       │   ├── AnalysisPage.vue               # 分析看板（3 栏）
│       │   ├── HistoryPage.vue                # 历史趋势
│       │   └── ComparisonPage.vue             # 模型对比
│       ├── components/                        # 通用组件 (16 个)
│       │   ├── PrSummary.vue                  # PR 摘要卡片
│       │   ├── AiThinkingTimeline.vue         # Agent 时间线
│       │   ├── AgentStatusBar.vue             # 实时状态栏
│       │   ├── AiReviewPanel.vue              # AI 评审面板
│       │   ├── MarkdownRenderer.vue           # Markdown 渲染器
│       │   ├── RiskHeatMap.vue                # 文件风险热力图
│       │   ├── RiskFocusPanel.vue             # 风险聚焦面板
│       │   ├── RiskScoreCard.vue              # 风险评分仪表盘
│       │   ├── ConfidenceGauge.vue            # 置信度仪表盘
│       │   ├── RuleFindings.vue               # 规则发现列表
│       │   ├── FileAnalysisList.vue           # 文件分析列表
│       │   ├── ProviderSelector.vue           # AI 模型选择器
│       │   ├── ProviderSwitchBar.vue          # 多模型切换栏
│       │   ├── DiffViewer.vue                 # 代码差异查看器
│       │   ├── StreamingTimeline.vue          # 传统时间线（兼容）
│       │   └── ProviderComparisonPanel.vue    # 内联对比面板
│       ├── stores/                            # Pinia 状态管理
│       │   └── analysis.ts                    # 分析状态（SSE/Agent/多模型）
│       ├── router/                            # Vue Router 路由
│       └── utils/                             # 工具函数
│           ├── report.ts                      # Markdown 报告生成
│           └── api.ts                         # API 请求封装
│
└── README.md
```

## 数据库模型

### analysis_record — 分析记录

存储每次 PR 分析的完整结果（JSON 字段存储风险发现、文件分析、评分细节等），支持历史回顾和趋势聚合。

### rule_feedback — 规则反馈

| 字段 | 说明 |
|------|------|
| `analysis_id` | 关联分析记录 |
| `rule_name` | 规则名称 |
| `finding_id` | 发现项 ID |
| `feedback_type` | `CONFIRMED` / `FALSE_POSITIVE` / `MISSED_ISSUE` |
| `reviewer` | 评审人（默认 "user"） |
| `comment` | 可选备注 |

### rule_confidence — 规则置信度

| 字段 | 说明 |
|------|------|
| `rule_name` | 规则名称 |
| `total_checks` | 总检测次数 |
| `confirmed_count` | 确认有效次数 |
| `false_positive_count` | 误报次数 |
| `precision` | 精准率 = confirmed / (confirmed + false_positive) |
| `recall` | 召回率 |
| `f1_score` | F1 分数 |
| `threshold` | 当前阈值（低于此值自动抑制规则） |

### evaluation_sample — 评估样本

存储标注好的评估数据集，用于离线计算规则的准确率、召回率和 F1 分数。支持按规则筛选和批量评估。

## AI 提供商配置

CodePilot 通过统一的 `AiProvider` 接口抽象了 5 种大模型，可通过环境变量灵活切换：

| 提供商 | 环境变量 | 默认模型 |
|--------|----------|----------|
| OpenAI | `OPENAI_API_KEY` | GPT-4o |
| DeepSeek | `DEEPSEEK_API_KEY` | deepseek-chat |
| Anthropic Claude | `CLAUDE_API_KEY` | claude-sonnet-4-6 |
| 阿里 Qwen | `QWEN_API_KEY` | qwen-plus |
| SiliconFlow | `SILICONFLOW_API_KEY` | Qwen/Qwen2.5-72B-Instruct |

**切换默认提供商：**

```bash
export AI_DEFAULT_PROVIDER=deepseek   # openai / deepseek / claude / qwen / siliconflow
```

**WebFlux 非阻塞调用：** AI 请求通过 Spring WebFlux `WebClient` 以非阻塞方式调用，确保分析流水线在高并发场景下保持吞吐量。

---

<p align="center">
  <sub>CodePilot v1.0.0 · Built with ❤️ by the CodePilot Team</sub>
</p>
