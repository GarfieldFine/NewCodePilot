# AI PR Review Assistant 第二阶段架构升级 Prompt（基于现有项目增量优化）

当前项目第一阶段已经完成，已有能力包括：

- GitHub PR 获取
- Diff 分析
- AI Review
- Rule Engine
- 风险评分
- SSE/WebFlux 流式输出
- Vue3 前端
- Redis 缓存
- 多模型 Provider
- 基础 Prompt Engineering

当前项目已经具备：

# “可运行 AI PR Review 系统”

现在不要重新生成项目。

不要推翻现有架构。

不要重新生成基础 CRUD 模块。

不要重复实现已经存在的功能。

------

# 当前阶段目标

请基于现有代码架构：

# 做第二阶段 AI Native 升级

目标：

让当前系统从：

# “AI Review Demo”

升级为：

# “真正 AI Native 企业级 Code Review Platform”

参考产品：

- Cursor
- CodeRabbit
- GitHub Copilot Workspace
- Devin
- Linear AI

重点提升：

- AI 感
- 智能感
- 产品感
- Demo 冲击力
- Explainable AI
- 多 Agent Workflow
- 代码语义理解

而不是继续增加普通业务功能。

------

# 一、升级原则（非常重要）

请严格遵守：

## 1. 基于现有代码增量升级

不要重构整个项目。

优先：

- 新增模块
- 增强已有模块
- 扩展现有架构

不要：

- 推翻第一阶段实现
- 重建项目结构
- 重新生成基础代码

------

## 2. 不要新增大量 CRUD 功能

重点是：

# AI Native 化

不是后台系统功能堆叠。

------

## 3. 所有升级必须服务于：

- AI 产品体验
- AI Reviewer 专业度
- AI 推理能力
- AI Explainability

------

# 二、当前系统问题（需要重点解决）

当前系统虽然已经可运行。

但仍然更像：

# “LLM + Diff 分析工具”

而不是：

# “真正 AI Code Review Agent”

当前缺少：

- Multi-Agent Workflow
- Explainable Review
- Semantic Code Understanding
- Repository Understanding
- Dynamic Review Strategy
- Chunk Pipeline
- AI Thinking Experience
- Agent Collaboration

请重点增强这些部分。

------

# 三、Multi-Agent Workflow 升级（核心）

当前：

```txt
PR -> Analyze -> Result
```

请升级为：

# AI Agent Workflow

新增：

```txt
RepositoryAnalyzeAgent
DiffAnalyzeAgent
ContextBuildAgent
RiskDetectAgent
ReviewGenerateAgent
SummaryMergeAgent
```

要求：

每个 Agent：

- 独立职责
- 独立 Prompt
- 独立输出
- 支持链式调用
- 支持上下文共享

新增：

```txt
AgentExecutor
AgentContext
AgentResult
AgentPipeline
```

不要破坏现有 Service 架构。

而是在现有架构之上增加 Agent Layer。

------

# 四、Repository Semantic Understanding（重点）

当前系统仅分析 PR Diff。

请升级：

# Repository 智能理解能力

系统需要自动识别：

- 编程语言
- 框架
- 项目类型
- 模块职责
- Repository 结构

例如：

```txt
pom.xml -> Spring Boot
package.json -> React/Vue
go.mod -> Go
requirements.txt -> Python
```

新增：

```txt
RepositoryAnalyzer
FrameworkDetector
LanguageDetector
```

输出：

```json
{
  "languages": ["Java", "TypeScript"],
  "frameworks": ["Spring Boot", "Vue3"],
  "projectType": "Fullstack"
}
```

------

# 五、Dynamic Review Strategy（高级能力）

当前 Review 逻辑偏 Java 后端。

请升级：

# 多语言动态 Review Strategy

新增：

```txt
ReviewStrategy
ReviewStrategyFactory
```

支持：

```txt
JavaReviewStrategy
PythonReviewStrategy
GoReviewStrategy
FrontendReviewStrategy
SqlReviewStrategy
```

要求：

根据：

- 文件类型
- 技术栈
- Framework

动态切换：

- Prompt
- Rule
- Review Focus

------

# 六、Semantic Code Analysis（核心亮点）

当前系统：

仅分析代码文本。

请升级：

# 代码语义级分析

新增：

```txt
CodeSemanticAnalyzer
```

要求：

根据代码类型：

动态分析。

------

## Java

关注：

- 并发
- 事务
- Spring
- Redis
- SQL

------

## React/Vue

关注：

- hooks
- rerender
- 状态管理
- useEffect

------

## Python

关注：

- 类型风险
- 异常处理
- requirements

------

## Go

关注：

- goroutine
- channel
- context

------

# 七、Explainable AI Review（比赛亮点）

当前 Review：

缺少“为什么”。

请升级：

# Explainable Review

所有 Review 必须包含：

```json
{
  "severity": "HIGH",
  "title": "Potential Risk",
  "why": "为什么存在风险",
  "evidence": "具体代码依据",
  "impact": "可能影响",
  "suggestion": "修改建议"
}
```

禁止输出：

```txt
代码可以优化
建议提高可读性
```

要求：

所有建议：

- 必须具体
- 必须引用代码
- 必须解释原因

------

# 八、大 PR Chunk Pipeline（高级能力）

当前系统可能直接把大 Diff 喂给 AI。

请升级：

# Chunk + Merge Pipeline

新增：

```txt
ChunkSplitter
ChunkAnalyzer
SummaryMergeAgent
ContextCompressor
```

要求：

支持：

- 大 PR 分片
- Token 控制
- Chunk 并发分析
- Merge 汇总

Chunk 类型：

```txt
Controller Chunk
Service Chunk
Frontend Chunk
SQL Chunk
Config Chunk
```

------

# 九、AI Thinking Timeline（产品感重点）

新增：

# AI Thinking Experience

前端展示：

```txt
[√] Analyzing Repository...
[√] Detecting Framework...
[√] Building Semantic Context...
[√] Detecting Risks...
[√] Generating Suggestions...
```

支持：

- Streaming Timeline
- Agent Status
- Thinking Animation

------

# 十、Risk HeatMap（Demo 冲击力）

新增：

# 文件风险热力图

例如：

```txt
UserService.java        HIGH
RedisLockUtil.java      CRITICAL
OrderMapper.xml         MEDIUM
App.tsx                 LOW
```

支持：

- 风险颜色高亮
- 文件级评分
- 点击展开 Review

------

# 十一、AI Confidence System（高级感）

新增：

# AI Confidence Score

展示：

- AI 置信度
- Rule 命中率
- Review 可靠度

例如：

```txt
Confidence: 87%
```

要求：

结合：

- Rule 命中
- AI 输出一致性
- Chunk 汇总结果

计算。

------

# 十二、Prompt Engineering 升级（重点）

当前 Prompt 过于通用。

请升级：

# Dynamic Prompt Architecture

根据：

- 语言
- Framework
- 文件类型
- Risk Type

动态切换 Prompt。

新增：

```txt
PromptManager
PromptTemplateRegistry
PersonaPromptBuilder
```

------

# 十三、Reviewer Persona System（高级感）

新增：

```txt
SecurityReviewer
PerformanceReviewer
ArchitectureReviewer
FrontendReviewer
BackendReviewer
```

不同 Persona：

Review 风格不同。

------

# 十四、前端 AI Native UI 升级（非常重要）

当前页面更像后台系统。

请升级为：

# AI Native UI

参考：

- Cursor
- CodeRabbit
- Linear
- Notion AI

要求：

- 深色主题
- AI Thinking 动画
- Markdown Streaming
- Diff 高亮
- 风险高亮
- Timeline
- Agent Status
- Confidence Score
- Token Usage

------

# 十五、最终目标（非常重要）

不要把系统做成：

# “AI 调 API Demo”

而是：

# “真正 AI Native Code Review Platform”

系统必须体现：

- AI Agent
- Semantic Understanding
- Repository Understanding
- Explainable AI
- Multi-Language Review
- Enterprise Architecture
- AI Product Experience

------

# 十六、输出要求

请基于现有代码：

输出：

1. 第二阶段升级架构
2. Agent 调用链
3. Chunk Pipeline
4. Prompt 架构升级
5. 新增模块设计
6. 核心代码实现
7. 前端升级方案
8. AI Workflow 图
9. 时序图
10. Demo 展示方案

要求：

- 保持现有架构兼容
- 不要推翻第一阶段代码
- 不要生成重复模块
- 所有升级必须可运行
- 保持工程化
- 尽量企业级风格



  git push -u origin feature/agent-runtime-streaming



遗漏项 (按优先级排序)

  高优先级 — 直接影响核心功能完整性

  1. 测试体系完全缺失

    - src/test/ 目录不存在，0 个测试文件
    - Phase 9 (集成测试 + Demo 验证) 在 plan 中标记为 pending
    - 没有单元测试、集成测试、端到端测试
  2. 无法将 Review 结果回写到 GitHub

    - GitHubClient 只做了 GET 请求，没有 POST/PATCH
    - 缺少 POST /repos/{owner}/{repo}/pulls/{number}/reviews (提交 PR Review)
    - 缺少 inline comment 能力（对具体代码行发表评论）
    - 系统只能"分析"，不能真正融入到 Review 工作流中
  3. 系统设计说明文档完全空白

    - README.md 只有一行 "# CodePilot"
    - 题目明确要求说明：模型选择、上下文获取方式、未来扩展方向上的设计思路
    - 没有任何架构设计文档
  4. 误报与漏报控制缺乏机制

    - 目前仅在 Prompt 里写了 "avoid false positives"，没有真正的控制机制
    - 缺少：用户反馈回路（标记误报/漏报）
    - 缺少：规则级别的置信度阈值调节
    - 缺少：评估数据集来衡量 precision/recall

  中优先级 — 影响产品完整度

  5. 缺少 GitHub Webhook / CI 集成

    - 只能手动粘贴 PR URL，无法自动触发分析
    - 无法集成到 CI/CD 流程中
    - 缺少 POST /repos/{owner}/{repo}/hooks 订阅 PR 事件
  6. 缺少分析历史与趋势追踪

    - MyBatis Plus 已配置但 model/entity/ 为空，无持久化实体
    - 分析结果仅存在 Redis 缓存中 (30 分钟 TTL)，无持久化
    - 无法对比两次 PR 的分析结果，无法追踪代码质量趋势
  7. 缺少跨文件调用链分析

    - 语义分析器只逐文件分析，不构建调用图/依赖图
    - 无法回答："这个方法的签名变了，影响哪些调用方？"
    - ContextBuildAgent 用的是策略聚焦点，并非真正的代码关系分析
  8. 缺少增量 Review 能力

    - 每次都是全量分析，没有 "只看新 commit" 的能力
    - 无 diff-of-diff 概念——无法区分哪些是新变更、哪些是上次已评审过的

  低优先级 — 体验增强

  9. 前端交互细节缺失

    - 无 dark/light 主题切换
    - 无 findings 按严重级别/文件/类型筛选排序
    - 无 Review 结果导出/分享功能
    - 无错误恢复 UI（只有基本错误提示）
  10. 语义分析不是真正的 AST

    - 当前是正则 + 关键词模式匹配，覆盖约 80% 场景
    - 没有接入真正的 AST 解析器 (如 tree-sitter / javaparser)
    - 缺少语言：C#、Rust、Kotlin、Ruby、PHP



7dd631969c4ac9ce1c0bd8be8281eccf6002d277

历史记录 + 趋势面板   │ 展示项目代码质量变化曲线              │
  ├────────┼───────────────────────┼────────────────────────────────────
自定义规则配置        │ 团队可以加自己的检查规则                │
  ├────────┼───────────────────────┼────────────────────────────────────多 Provider 对比      │ 同一个 PR 用 DeepSeek + Claude 分别评审 │
  ├────────┼───────────────────────┼──────────────────────────────────── 报告导出 PDF/Markdown │ 存档、分享、合规                        │
  ├────────┼───────────────────────┼──────────────────────────────────── API Key 管理页面      │ 不用每次启动传环境变量     