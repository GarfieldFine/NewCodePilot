# Task Plan: CodePilot 第二阶段 AI Native 升级

## Goal

将 CodePilot 从 "AI Review Demo" 升级为 "AI Native 企业级 Code Review Platform"

参考产品: Cursor / CodeRabbit / GitHub Copilot Workspace / Devin / Linear AI

## 升级原则

- **增量升级**: 基于现有代码，新增模块 + 增强已有模块 + 扩展现有架构
- **不推翻第一阶段**: 保持现有 Controller→Service→AIProvider 架构
- **不新增 CRUD**: 专注于 AI Native 化，不是后台功能堆叠
- **服务 AI 体验**: 所有升级服务于 AI Reviewer 专业度、推理能力、Explainability

## Current Phase

Phase 8 (第二阶段的第八阶段): 前端 AI Native UI 升级

---

## Phase 1: Multi-Agent Workflow 核心架构

**状态:** in_progress
**目标:** 在现有 Service 架构之上增加 Agent Layer

### 1.1 Agent 基础框架 (agent/)
- [ ] `AgentContext.java` — 共享上下文容器 (Map<String, Object>)
- [ ] `AgentResult.java` — 执行结果封装 (status + output + metadata)
- [ ] `Agent.java` — Agent 接口 (getName / analyze / analyzeStream)
- [ ] `AgentExecutor.java` — 执行器，支持链式调用和并发
- [ ] `AgentPipeline.java` — 流水线编排 (sequential + parallel stages)

### 1.2 六个核心 Agent (agent/agents/)
- [ ] `RepositoryAnalyzeAgent.java` — 仓库结构分析 (语言/框架/模块)
- [ ] `DiffAnalyzeAgent.java` — Diff 深度分析 (变更语义理解)
- [ ] `ContextBuildAgent.java` — 语义上下文构建 (代码关系图)
- [ ] `RiskDetectAgent.java` — 风险检测 (结合规则+语义)
- [ ] `ReviewGenerateAgent.java` — Review 生成 (结构化输出)
- [ ] `SummaryMergeAgent.java` — 汇总合并 (多 Chunk 结果合并)

### 1.3 集成现有架构
- [ ] `ReviewEngine` 重构 — AgentPipeline 替代线性调用，保持 API 兼容
- [ ] `PrAnalysisService` 适配 — 支持 Agent 流式状态事件

---

## Phase 2: Repository Semantic Understanding

**状态:** pending
**目标:** 自动识别项目语言、框架、模块职责

### 2.1 仓库分析器 (repository/)
- [ ] `RepositoryAnalyzer.java` — 仓库分析主入口
- [ ] `FrameworkDetector.java` — 框架检测 (pom.xml→Spring Boot, package.json→React/Vue, go.mod→Go 等)
- [ ] `LanguageDetector.java` — 语言检测与统计
- [ ] `ModuleClassifier.java` — 模块职责分类 (controller/service/dao/config)
- [ ] `RepositoryContext.java` — 仓库上下文模型

### 2.2 GitHub 集成增强
- [ ] `GitHubClient` 增强 — 获取仓库根目录文件列表 (GET /repos/:owner/:repo/contents/)
- [ ] 文件树分析 + README 解析

---

## Phase 3: Dynamic Review Strategy

**状态:** ✅ complete
**目标:** 多语言/多框架动态 Review 策略

### 3.1 策略框架 (strategy/)
- [x] `ReviewStrategy.java` — 策略接口 (supports / buildPrompt / getFocusAreas)
- [x] `ReviewStrategyFactory.java` — 策略工厂 (根据语言/框架匹配)
- [x] `JavaReviewStrategy.java` — 并发/事务/Spring/Redis/SQL
- [x] `PythonReviewStrategy.java` — 类型/异常/依赖
- [x] `GoReviewStrategy.java` — goroutine/channel/context
- [x] `FrontendReviewStrategy.java` — hooks/rerender/状态管理
- [x] `SqlReviewStrategy.java` — 索引/锁/迁移

### 3.2 策略集成
- [x] `ContextBuildAgent` 集成策略工厂
- [x] `ReviewGenerateAgent` 集成动态 Prompt

---

## Phase 4: Semantic Code Analysis

**状态:** ✅ complete
**目标:** 代码语义级分析（超越正则，达到 AST 级别理解）

### 4.1 语义分析器 (semantic/)
- [x] `CodeSemanticAnalyzer.java` — 语义分析主入口
- [x] `JavaSemanticAnalyzer.java` — @Transactional / synchronized / ThreadPool / Redis
- [x] `FrontendSemanticAnalyzer.java` — useEffect deps / useState / memo
- [x] `PythonSemanticAnalyzer.java` — try-except / type hints / GIL
- [x] `GoSemanticAnalyzer.java` — goroutine leak / channel deadlock / defer
- [x] `SemanticContext.java` / `SemanticFinding.java` — 语义分析模型

### 4.2 集成
- [x] `DiffAnalyzeAgent` 集成语义分析器
- [x] `RiskDetectAgent` 使用语义发现增强规则

---

## Phase 5: Chunk Pipeline 升级

**状态:** ✅ complete
**目标:** 大 PR 智能分片 + 并发分析 + 汇总

### 5.1 Chunk 系统 (chunk/)
- [x] `ChunkType.java` — 枚举 (CONTROLLER, SERVICE, DAO, FRONTEND, SQL, CONFIG, TEST, OTHER)
- [x] `ChunkSplitter.java` — 增强版分片器 (按类型分组 + Token 估算)
- [x] `ChunkAnalyzer.java` — Chunk 并发分析调度器
- [x] `ContextCompressor.java` — 上下文压缩 (Diff压缩 + 文件摘要)

### 5.2 集成
- [x] `ReviewGenerateAgent` — 使用 ChunkSplitter + ChunkAnalyzer 替代顺序循环
- [x] `SummaryMergeAgent` — Chunk 结果去重 + 增强置信度计算

---

## Phase 6: Prompt Engineering 升级

**状态:** ✅ complete
**目标:** 动态 Prompt 架构 + Persona 系统 + 外部化模板

### 6.1 Prompt 管理 (prompt/)
- [x] `PromptManager.java` — 模板加载/缓存/{{placeholder}}渲染
- [x] `PersonaPromptBuilder.java` — Persona 感知的 Prompt 构建器
- [x] `resources/prompts/system-base.md` — 外部化基础 System Prompt
- [x] `resources/prompts/review-template.md` — 外部化 Review 模板

### 6.2 Reviewer Persona (persona/)
- [x] `ReviewerPersona.java` — Persona 接口
- [x] `SecurityReviewer.java` — 安全审计风格 (OWASP, CWE, CVSS)
- [x] `PerformanceReviewer.java` — 性能分析风格
- [x] `ArchitectureReviewer.java` — 架构设计风格
- [x] `BackendReviewer.java` — 后端专业风格
- [x] `FrontendReviewer.java` — 前端专业风格

### 6.3 集成
- [x] `ReviewGenerateAgent` — 使用 PersonaPromptBuilder 替代手动 Prompt 拼装

---

## Phase 7: Explainable AI + Confidence System

**状态:** ✅ complete
**目标:** 所有 Review 包含 why/evidence/impact + 多因子 AI 置信度

### 7.1 Explainable Review
- [x] 增强 `ReviewSuggestion` — 添加 why / evidence / impact 字段
- [x] 增强 `RiskFinding` — 添加 why / evidence / impact / confidenceScore 字段
- [x] `PersonaPromptBuilder` — 已强制结构化 Explainable 输出 (Phase 6 已完成)

### 7.2 AI Confidence System (confidence/)
- [x] `ConfidenceCalculator.java` — 6 因子加权置信度计算
  - Rule 命中率权重 (0.20)
  - AI 输出一致性 (0.25) — 跨 Chunk 成功率和长度方差分析
  - Cross-Chunk 一致性 (0.15) — 输出长度变异系数
  - 代码变更复杂度因子 (0.20)
  - 仓库知识因子 (0.10) — 语言/框架广度
  - Token 覆盖率 (0.10) — AI 上下文充分性

### 7.3 集成
- [x] `SummaryMergeAgent` — 使用 ConfidenceCalculator 替代内联置信度计算

---

## Phase 8: 前端 AI Native UI 升级

**状态:** ✅ complete
**目标:** 从后台系统风格升级为 AI Native 产品体验

### 8.1 新组件 (components/)
- [x] `AiThinkingTimeline.vue` — Agent 工作流动画，替换 StreamingTimeline
- [x] `RiskHeatMap.vue` — 文件风险热力图 (颜色热力 + 点击展开)
- [x] `ConfidenceGauge.vue` — AI 置信度仪表盘 (SVG 环形图 + 6 因子分解)
- [x] `AgentStatusBar.vue` — Agent 执行状态条 (Live agent badges + Token/Chunk 统计)
- [x] `DiffViewer.vue` — 增强 Diff 高亮 (使用 highlight.js 语法着色)
- [x] `MarkdownRenderer.vue` — 升级 Markdown 渲染 (使用 marked 库 + highlight.js)

### 8.2 视觉升级
- [x] 深色主题增强 (新增 --shadow-glow-sm, --shadow-elevated, --shadow-card, --radius-full, --transition-spring)
- [x] AI Thinking 粒子动画 (particle-orbit 旋转粒子 + 12 点星轨)
- [x] Micro-interactions (hover glow, transform translate, live pulse animations)
- [x] Token Usage 实时展示 (AgentStatusBar 中实时显示 token/chunk 统计)

### 8.3 数据层
- [x] Store 增强 — agentEvents/activeAgents/tokenUsage/confidence 状态 + computed 派生
- [x] API 层增强 — 新 SSE 事件类型 (agent_start/agent_complete/agent_error/agent_skip/pipeline_stage)
- [x] 后端 SSE 格式修复 — AgentPipeline.executeStream() 添加 `data:` 前缀
- [x] TokenUsage 模型 — 新增 ai/model/TokenUsage.java，集成到 AnalysisResult
- [x] 类型定义增强 — 新增 TokenUsage/ConfidenceScores/RepositoryProfile/AgentEvent 接口

---

## Phase 9: 集成测试 + Demo 验证

**状态:** pending
**目标:** 端到端验证，准备 Demo

- [ ] Agent Pipeline 集成测试
- [ ] 多语言 PR Review 验证 (Java / Python / Go / Vue)
- [ ] 前端 UI 完整走查
- [ ] Demo 脚本: Java Spring Boot PR + Vue Frontend PR + 大 PR 分片

---

## 关键决策记录

| 决策 | 原因 |
|------|------|
| Agent Layer 在 Service 之上 | 不破坏现有架构，ReviewEngine 作为 AgentPipeline 编排器 |
| 前端不引入新 UI 框架 | 现有自定义 CSS 体系可扩展，减少依赖 |
| Prompt 外部化到 resources/prompts/ | 便于管理和迭代，与代码解耦 |
| 六个 Agent 而非两个 | 职责单一，独立演进，独立 Prompt |

## 错误记录

| 错误 | 尝试 | 解决方案 |
|------|------|----------|
| - | - | - |
