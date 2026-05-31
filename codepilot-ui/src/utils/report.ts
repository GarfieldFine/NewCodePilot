import type { AnalysisResult, RuleResult, FileAnalysis } from '@/types'

const RISK_COLOR: Record<string, string> = {
  CRITICAL: '🔴', HIGH: '🟠', MEDIUM: '🟡', LOW: '🟢'
}

function esc(text: string | null | undefined): string {
  if (!text) return ''
  return text.replace(/\|/g, '\\|')
}

function dateStr(): string {
  return new Date().toISOString().replace('T', ' ').substring(0, 19)
}

function formatRiskScore(score: any): string {
  if (!score) return 'N/A'
  const level = score.riskLevel || 'UNKNOWN'
  return `${score.totalScore || 0} / ${score.maxScore || 100} (${level})`
}

function groupByLevel(results: RuleResult[]): Record<string, RuleResult[]> {
  const groups: Record<string, RuleResult[]> = { CRITICAL: [], HIGH: [], MEDIUM: [], LOW: [] }
  for (const r of results) {
    const level = r.riskLevel || 'LOW'
    if (!groups[level]) groups[level] = []
    groups[level].push(r)
  }
  return groups
}

export function generateMarkdownReport(result: AnalysisResult): string {
  const lines: string[] = []

  // ============== 1. 基本信息 ==============
  lines.push('# CodePilot AI 代码评审报告', '')
  lines.push('## 基本信息', '')
  lines.push(`| 项目 | 内容 |`)
  lines.push(`|------|------|`)
  lines.push(`| 分析 ID | \`${esc(result.analysisId)}\` |`)
  lines.push(`| PR 标题 | ${esc(result.prTitle)} |`)
  lines.push(`| PR 链接 | [${esc(result.owner)}/${esc(result.repo)}#${result.prNumber}](${esc(result.prUrl)}) |`)
  lines.push(`| 作者 | ${esc(result.author)} |`)
  lines.push(`| 变更文件 | ${result.changedFiles || 0} 个 |`)
  lines.push(`| 代码变更 | +${result.additions || 0} / -${result.deletions || 0} 行 |`)
  lines.push(`| 生成时间 | ${dateStr()} |`)
  lines.push('')

  // ============== 2. 风险评分总览 ==============
  lines.push('## 风险评分总览', '')
  const rs = result.riskScore
  if (rs) {
    lines.push(`| 维度 | 得分 |`)
    lines.push(`|------|------|`)
    lines.push(`| **综合风险分** | **${rs.totalScore} / ${rs.maxScore || 100}** — ${rs.riskLevel || 'N/A'} |`)
    lines.push(`| 文件数量分 | ${rs.fileCountScore ?? '-'} |`)
    lines.push(`| 规则发现分 | ${rs.ruleFindingsScore ?? '-'} |`)
    lines.push(`| 模块分 | ${rs.moduleScore ?? '-'} |`)
    lines.push(`| 代码量分 | ${rs.volumeScore ?? '-'} |`)
    lines.push(`| AI 评分 | ${rs.aiScore ?? '-'} |`)
    lines.push('')

    if (rs.ruleBreakdown) {
      lines.push('### 规则发现分布', '')
      lines.push(`| 级别 | 数量 |`)
      lines.push(`|------|------|`)
      for (const [level, count] of Object.entries(rs.ruleBreakdown)) {
        const icon = RISK_COLOR[level] || ''
        lines.push(`| ${icon} ${level} | ${count} |`)
      }
      lines.push('')
    }

    lines.push(`### 热度图`, '')
    lines.push(`[*${rs.totalScore} / ${rs.maxScore || 100}*] 风险评分`, '')
    const barLen = Math.max(1, Math.round((rs.totalScore / (rs.maxScore || 100)) * 40))
    lines.push(`\`${'█'.repeat(barLen)}${'░'.repeat(40 - barLen)}\``)
  } else {
    lines.push('无风险评分数据')
  }
  lines.push('')

  // ============== 3. 置信度评估 ==============
  const cs = result.confidenceScores as Record<string, any> | null | undefined
  lines.push('## 置信度评估', '')
  if (cs) {
    const overall = (cs.overallConfidence as number) ?? (cs.overall_confidence as number)
    const level = cs.confidenceLevel ?? cs.confidence_level ?? 'N/A'
    const summary = cs.summary ?? ''
    lines.push(`| 项目 | 内容 |`)
    lines.push(`|------|------|`)
    lines.push(`| 综合置信度 | **${overall != null ? (overall * 100).toFixed(1) + '%' : 'N/A'}** — ${level} |`)
    if (summary) {
      lines.push(`| 概述 | ${esc(String(summary))} |`)
    }
    lines.push('')

    const factors = cs.factors ?? cs.factors_detail ?? {}
    if (factors && Object.keys(factors).length > 0) {
      lines.push('### 置信度因素', '')
      lines.push(`| 因素 | 得分 |`)
      lines.push(`|------|------|`)
      for (const [k, v] of Object.entries(factors)) {
        const pct = typeof v === 'number' ? (v * 100).toFixed(1) + '%' : String(v)
        lines.push(`| ${esc(k)} | ${pct} |`)
      }
      lines.push('')
    }
  } else {
    lines.push('无置信度评估数据', '')
  }

  // ============== 4. 规则发现列表 ==============
  lines.push('## 规则发现列表', '')
  const ruleResults = result.ruleResults || []
  if (ruleResults.length > 0) {
    const grouped = groupByLevel(ruleResults)
    for (const level of ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']) {
      const items = grouped[level]
      if (!items || items.length === 0) continue
      const icon = RISK_COLOR[level] || ''
      lines.push(`### ${icon} ${level} (${items.length} 项)`, '')
      lines.push(`| 规则 | 文件 | 行号 | 描述 | 建议 |`)
      lines.push(`|------|------|------|------|------|`)
      for (const r of items) {
        const confidence = r.confidence != null ? ` (${(r.confidence * 100).toFixed(0)}%)` : ''
        lines.push(
          `| ${esc(r.ruleName)}${confidence} | \`${esc(r.file)}\` | ${r.line ?? '-'} | ${esc(r.message)} | ${esc(r.suggestion)} |`
        )
      }
      lines.push('')
    }
  } else {
    lines.push('未发现规则级别问题', '')
  }

  // ============== 5. AI 评审意见 ==============
  lines.push('## AI 评审意见', '')
  if (result.aiRawOutput && result.aiRawOutput.trim()) {
    lines.push('> 以下内容由 AI 模型生成，保留原始 Markdown 格式')
    lines.push('')
    lines.push(result.aiRawOutput)
  } else {
    lines.push('无 AI 评审输出')
  }
  lines.push('')

  // ============== 6. 逐文件分析 ==============
  lines.push('## 逐文件分析', '')
  const fileAnalysis = result.fileAnalysis || []
  if (fileAnalysis.length > 0) {
    lines.push(`| 文件 | 语言 | 状态 | +行 | -行 | 风险 | 发现数 |`)
    lines.push(`|------|------|------|------|------|------|------|`)
    for (const f of fileAnalysis) {
      const icon = RISK_COLOR[f.riskLevel] || ''
      const findings = f.findings?.length ?? (f as any).aiFindings?.length ?? 0
      lines.push(
        `| \`${esc(f.filename)}\` | ${f.language ?? '-'} | ${f.status ?? '-'} | ${f.additions ?? 0} | ${f.deletions ?? 0} | ${icon} ${f.riskLevel ?? '-'} | ${findings} |`
      )
    }
    lines.push('')
  } else {
    lines.push('无逐文件分析数据', '')
  }

  // ============== 7. AI 工作流时间线 ==============
  lines.push('## AI 工作流时间线', '')
  const timeline = result.agentTimeline || []
  if (timeline.length > 0) {
    for (let i = 0; i < timeline.length; i++) {
      lines.push(`${i + 1}. ${esc(timeline[i])}`)
    }
    lines.push('')
  } else {
    lines.push('无工作流时间线数据', '')
  }

  // ============== 8. Token 用量 ==============
  lines.push('## Token 用量', '')
  const tu = result.tokenUsage
  if (tu) {
    lines.push(`| 指标 | 用量 |`)
    lines.push(`|------|------|`)
    lines.push(`| Prompt Tokens | ${tu.promptTokens?.toLocaleString() ?? '-'} |`)
    lines.push(`| Completion Tokens | ${tu.completionTokens?.toLocaleString() ?? '-'} |`)
    lines.push(`| **Total Tokens** | **${tu.totalTokens?.toLocaleString() ?? '-'}** |`)
    if ((tu as any).totalChunks != null) {
      lines.push(`| Chunks | ${(tu as any).totalChunks} / ${(tu as any).successfulChunks ?? '-'} |`)
    }
  } else {
    lines.push('无 Token 用量数据')
  }
  lines.push('')

  return lines.join('\n')
}

export function downloadMarkdown(content: string, filename: string) {
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
