# CodePilot — AI 原生企业级代码审查平台

基于多智能体协作的 GitHub PR 自动化审查工具，支持 5 种 AI 提供商，提供流式实时反馈、语义级代码分析和误报控制。

## 技术栈

| 层级 | 技术 |
|------|------|
| **后端** | Java 17, Spring Boot 3.2.5, Spring WebFlux, MyBatis Plus 3.5.6, Redis (Lettuce), MySQL 8 |
| **前端** | Vue 3.4, TypeScript 5.4, Vite 5, Pinia, Vue Router 4, Element Plus 2.6 |
| **AI** | OpenAI GPT-4o, DeepSeek, Anthropic Claude, 阿里 Qwen, SiliconFlow |

## 核心功能

- **多智能体流水线** — 6 个专业 Agent（仓库分析、Diff 分析、上下文构建、风险检测、审查生成、摘要合并）协作完成代码审查
- **语义级代码分析** — 超越正则匹配，检测 `@Transactional` 误用、`useEffect` 依赖问题、Go channel 死锁等深层模式
- **动态审查策略** — 自动识别语言和框架（Java/Spring Boot、Python/Django、Go、React/Vue、JavaScript/TypeScript），应用针对性规则
- **流式实时反馈** — 基于 SSE 推送审查进度，展示 Agent 状态、Token 消耗和置信度评分
- **可解释 AI 输出** — 每个发现附带 `why`、`evidence`、`impact` 和置信度评分
- **误报控制系统** — 规则反馈收集 → 置信度统计 → 自动调整规则阈值，持续降低误报率
- **Markdown 报告导出** — 支持导出格式化的审查报告

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- Node.js 18+
- MySQL 8.0+
- Redis 6.0+

### 后端启动

```bash
cd codepilot-server

# 配置环境变量
export MYSQL_USER=root
export MYSQL_PASSWORD=your_password
export GITHUB_TOKEN=ghp_xxxxxxxxxxxx
export DEEPSEEK_API_KEY=sk-xxxxxxxxxxxx  # 默认 AI 提供商

# 确保 MySQL 中已创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS codepilot DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci"

# 启动（schema.sql 自动初始化表结构）
mvn spring-boot:run
```

服务运行在 `http://localhost:8080`。

### 前端启动

```bash
cd codepilot-ui
npm install
npm run dev
```

前端运行在 `http://localhost:3000`，API 请求自动代理到后端 8080 端口。

### AI 提供商配置

默认使用 DeepSeek，可通过环境变量切换：

| 提供商 | 环境变量 |
|--------|----------|
| OpenAI | `OPENAI_API_KEY` |
| DeepSeek | `DEEPSEEK_API_KEY` |
| Claude | `CLAUDE_API_KEY` |
| 阿里 Qwen | `QWEN_API_KEY` |
| SiliconFlow | `SILICONFLOW_API_KEY` |

切换默认提供商：`AI_DEFAULT_PROVIDER=openai`（可选值：`openai` / `deepseek` / `claude` / `qwen` / `siliconflow`）

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/analyze` | 提交 PR 审查（同步） |
| `POST` | `/api/analyze/stream` | 提交 PR 审查（SSE 流式） |
| `GET` | `/api/detail/{analysisId}` | 查询审查详情 |
| `GET` | `/api/cache/{owner}/{repo}/{pr}` | 查询缓存 |
| `DELETE` | `/api/cache/{owner}/{repo}/{pr}` | 清除缓存 |

## 项目结构

```
CodePilot/
├── codepilot-server/          # Spring Boot 后端
│   └── src/main/java/com/codepilot/
│       ├── agent/             # 6 个审查 Agent
│       ├── ai/                # AI 提供商适配层
│       ├── analysis/          # 分析引擎与策略
│       ├── controller/        # REST API 控制器
│       ├── github/            # GitHub API 客户端
│       ├── rule/              # 规则引擎（正则 + 语义）
│       ├── review/            # 审查流水线
│       └── store/             # 数据持久化
├── codepilot-ui/              # Vue 3 前端
│   └── src/
│       ├── components/        # 通用组件
│       ├── pages/             # 页面组件
│       ├── stores/            # Pinia 状态管理
│       └── utils/             # 工具函数
└── README.md
```

## 数据库表

| 表名 | 用途 |
|------|------|
| `analysis_record` | 存储每次分析记录及结果 |
| `rule_feedback` | 用户对规则发现的反馈（确认/误报/漏报） |
| `rule_confidence` | 规则置信度统计，驱动阈值自动调整 |
| `evaluation_sample` | 评估数据集样本，用于衡量准确率/召回率 |
