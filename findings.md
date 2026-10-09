# Findings & Decisions: CodePilot 第二阶段 AI Native 升级

## 现有系统分析

### 后端 (codepilot-server)
- 44 Java 文件, 14 packages
- Spring Boot 3.2.5 + WebFlux + Redis + MyBatis Plus
- 架构: Controller → Service → ReviewEngine → AI Provider + Rule Engine
- 4 个 AI Provider: OpenAI (gpt-4o), DeepSeek (deepseek-chat), Claude (claude-sonnet-4-6), Qwen (qwen-plus)
- 10 条 Regex 规则 (SQL注入/Redis锁/空catch/ThreadLocal/空指针/IO循环/大事务/大对象/长方法/魔数)
- 5 维度风险评分 (文件数/规则发现/模块敏感度/变更量/AI发现)
- 已有 PrSplitter (max 10 files / 500 lines per chunk)
- 已有 SSE 流式 (Sinks.Many + Flux)
- Prompt 硬编码在 PromptBuilder.java
- model/entity/ 为空 (MyBatis Plus 配置但无实体)
- 无测试代码

### 前端 (codepilot-ui)
- 15 源文件 (10 .vue/.ts + 5 config)
- Vue 3.4 + Pinia + Vue Router + Element Plus (未实际使用)
- 自定义暗色主题 (34 CSS variables)
- SSE 流式处理: 原生 fetch + ReadableStream + TextDecoder
- marked 和 highlight.js 已安装但未使用
- 6 个组件: HomePage, AnalysisPage, PrSummary, StreamingTimeline, AiReviewPanel, RiskScoreCard, FileAnalysisList, RuleFindings

### 核心问题
1. 单一线程模型: PR → ReviewEngine.analyze() → 线性执行，无 Agent 概念
2. Prompt 硬编码: 所有 prompt 在 PromptBuilder.java 字符串常量中
3. 无仓库理解: 仅分析 diff 文本，不了解项目类型/框架
4. 无语义分析: 规则引擎是纯正则，不识别 AST 结构
5. 无 Explainability: Review 输出无 why/evidence/impact
6. 前端像后台系统: 缺乏 AI Native 产品感

---

## 第二阶段设计决策

### 决策 1: Agent Layer 架构
- **方案**: 在现有 Service 层之上增加 Agent Layer
- **原因**: 保持 ReviewEngine 作为编排器，Agent 作为独立执行单元
- **Agent 接口**: getName() + analyze(AgentContext) + analyzeStream(AgentContext)
- **AgentPipeline**: 支持 sequential (→) 和 parallel (⇉) 阶段

### 决策 2: 仓库分析方式
- **方案**: 基于 GitHub API 获取仓库文件树 + 本地分析
- **检测逻辑**:
  - pom.xml / build.gradle → Java + Spring Boot / Gradle
  - package.json → dependencies 分析 → React / Vue / Next.js / Express
  - go.mod → Go + module path
  - requirements.txt / setup.py / pyproject.toml → Python
  - Cargo.toml → Rust
  - .sql 文件 → 数据库 Migration

### 决策 3: 动态 Review Strategy
- **方案**: Strategy 模式，根据 FileAnalysis.language + RepositoryContext.frameworks 匹配
- **工厂**: ReviewStrategyFactory 维护 Map<String, ReviewStrategy>
- **Fallback**: DefaultReviewStrategy (通用策略)

### 决策 4: 语义分析深度
- **方案**: 混合方式 - 正则规则增强 + 关键词模式 + AST 启发式
- **Java**: 识别 @Transactional / synchronized / ThreadPoolExecutor / RedisTemplate
- **Frontend**: 识别 useState / useEffect / useMemo / useCallback
- **Go**: 识别 go func / chan / context.WithCancel / defer
- **Python**: 识别 try-except / type hints / async def

### 决策 5: Prompt 外部化
- **方案**: 从 PromptBuilder.java 迁移到 resources/prompts/*.md
- **模板引擎**: 简单的 {{variable}} 替换 + 条件块
- **Persona**: 每个 Persona 有自己的 system prompt 模板

### 决策 6: Chunk Pipeline 升级
- **方案**: 增强 PrSplitter，增加 ChunkType 分类 + Token 估算
- **并发**: 使用 Reactor Flux.flatMap 并发分析多个 Chunk
- **汇总**: SummaryMergeAgent 负责去重 + 排序 + 置信度

### 决策 7: 前端升级策略
- **方案**: 渐进增强，不重建页面
- **保留**: AnalysisPage 三栏布局，Store 架构
- **新增**: Agent 状态组件、HeatMap、ConfidenceGauge
- **替换**: StreamingTimeline → AiThinkingTimeline
- **激活**: 使用 marked + highlight.js 替代手写渲染

---

## Agent 调用链设计

```
PR 输入
  │
  ▼
AgentPipeline
  │
  ├─[Stage 1: SEQUENTIAL]─────────────────────────┐
  │  RepositoryAnalyzeAgent                        │
  │    → 检测语言/框架/模块                         │
  │    → 输出 RepositoryContext                    │
  │                                                │
  │  DiffAnalyzeAgent                              │
  │    → 分析变更语义                               │
  │    → 输出 DiffAnalysis                          │
  │                                                │
  │  ContextBuildAgent                              │
  │    → 构建语义上下文                             │
  │    → 输出 SemanticContext                       │
  └────────────────────────────────────────────────┘
  │
  ├─[Stage 2: PARALLEL]────────────────────────────┐
  │  RiskDetectAgent (Rule Engine)                  │
  │  RiskDetectAgent (AI Risk Scan)                 │
  │  ChunkAnalyzer (per Chunk)                      │
  └────────────────────────────────────────────────┘
  │
  ├─[Stage 3: SEQUENTIAL]─────────────────────────┐
  │  ReviewGenerateAgent (per Chunk/Persona)        │
  │    → 生成结构化 Review                          │
  │    → 输出 ReviewResult                          │
  └────────────────────────────────────────────────┘
  │
  ▼
SummaryMergeAgent
  → 合并 + 去重 + 排序 + 置信度
  → 输出 FinalAnalysisResult
```

---

## SSE 事件类型升级

```typescript
// 现有
type SseEventType = 'status' | 'token' | 'ai_token' | 'complete' | 'error'

// 升级后
type SseEventType =
  | 'agent_start'      // Agent 开始执行 {agentName, stage}
  | 'agent_progress'   // Agent 进度 {agentName, progress, message}
  | 'agent_complete'   // Agent 完成 {agentName, result}
  | 'agent_token'      // Agent 流式 Token {agentName, content}
  | 'pipeline_stage'   // Pipeline 阶段切换 {stage, agents}
  | 'status'           // 兼容旧版
  | 'token'            // 兼容旧版
  | 'complete'
  | 'error'
```

---

## 已验证的技术可行性

- Spring Boot WebFlux 支持 Flux.concat / Flux.merge 实现 Agent 流水线
- Sinks.Many 可在多个 Agent 间共享，流式推送
- GitHub Contents API 可获取仓库文件树 (1000 文件限制)
- 正则 + 关键词模式可覆盖 80% 语义分析场景
- Vue3 reactive store 可实时驱动 Agent 状态 UI
