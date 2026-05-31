<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { marked } from 'marked'
import { useAnalysisStore } from '@/stores/analysis'
import type { RiskLevel } from '@/types'

const router = useRouter()
const store = useAnalysisStore()

const riskLevels: RiskLevel[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

const leftPanel = ref<HTMLElement | null>(null)
const rightPanel = ref<HTMLElement | null>(null)

const providerLabels: Record<string, string> = {
  openai: 'OpenAI', deepseek: 'DeepSeek', claude: 'Claude', qwen: 'Qwen', siliconflow: '硅基流动'
}

const providerColors: Record<string, string> = {
  openai: '#10a37f', deepseek: '#6366f1', claude: '#d97706', qwen: '#2563eb', siliconflow: '#8b5cf6'
}

function riskColor(score: number): string {
  if (score >= 70) return '#ef4444'
  if (score >= 40) return '#f59e0b'
  return '#22c55e'
}

function levelLabel(level: string): string {
  const map: Record<string, string> = { CRITICAL: '严重', HIGH: '高', MEDIUM: '中', LOW: '低' }
  return map[level] || level
}

function levelColor(level: string): string {
  const map: Record<string, string> = { CRITICAL: '#ef4444', HIGH: '#f97316', MEDIUM: '#f59e0b', LOW: '#22c55e' }
  return map[level] || '#6b7280'
}

const providers = computed(() => store.selectedProviders)

const scoreDelta = computed(() => {
  const a = store.providerResults[providers.value[0]]?.riskScore?.totalScore ?? 0
  const b = store.providerResults[providers.value[1]]?.riskScore?.totalScore ?? 0
  return Math.abs(a - b)
})

const providerA = computed(() => providers.value[0] || '')
const providerB = computed(() => providers.value[1] || '')

const resultA = computed(() => store.providerResults[providerA.value])
const resultB = computed(() => store.providerResults[providerB.value])

const htmlA = computed(() => {
  const content = resultA.value?.aiRawOutput || store.providerStreams[providerA.value] || ''
  return content ? (marked.parse(content) as string) : ''
})

const htmlB = computed(() => {
  const content = resultB.value?.aiRawOutput || store.providerStreams[providerB.value] || ''
  return content ? (marked.parse(content) as string) : ''
})

function handleScroll(source: 'left' | 'right') {
  const src = source === 'left' ? leftPanel.value : rightPanel.value
  const target = source === 'left' ? rightPanel.value : leftPanel.value
  if (!src || !target) return
  target.scrollTop = src.scrollTop
}

function maxBreakdown(provider: string): number {
  const bd = store.providerResults[provider]?.riskScore?.ruleBreakdown
  if (!bd) return 1
  return Math.max(1, ...Object.values(bd))
}

// ---- AI output parsing ----

interface ParsedFinding {
  level: RiskLevel
  title: string
}

interface ParsedSuggestion {
  title: string
  description: string
}

function parseFindings(markdown: string): ParsedFinding[] {
  if (!markdown) return []
  // Extract the "### Risk Analysis" section
  const sectionMatch = markdown.match(/(?:###|##)\s*Risk\s*Analysis?\s*\n([\s\S]*?)(?=\n(?:###|##)\s|$)/i)
  if (!sectionMatch) return []
  const section = sectionMatch[1]
  const findings: ParsedFinding[] = []
  const lineRegex = /^\s*[-*]\s*\*\*\[(CRITICAL|HIGH|MEDIUM|LOW)\]\*\*:?\s*(.+)/gm
  let match: RegExpExecArray | null
  while ((match = lineRegex.exec(section)) !== null) {
    findings.push({ level: match[1] as RiskLevel, title: match[2].trim() })
  }
  return findings
}

function parseSuggestions(markdown: string): ParsedSuggestion[] {
  if (!markdown) return []
  // Extract the "### Review Suggestions" section
  const sectionMatch = markdown.match(/(?:###|##)\s*Review\s*Suggestions?\s*\n([\s\S]*?)(?=\n(?:###|##)\s|$)/i)
  if (!sectionMatch) return []
  const section = sectionMatch[1]
  const suggestions: ParsedSuggestion[] = []
  // Split by numbered items: "1. **Title**" or "1.**Title**"
  const blocks = section.split(/\n(?=\d+\.\s*\*\*)/)
  for (const block of blocks) {
    const titleMatch = block.match(/^\d+\.\s*\*\*(.+?)\*\*/m)
    if (titleMatch) {
      const desc = block.replace(/^\d+\.\s*\*\*.+?\*\*.*\n?/, '').trim()
      suggestions.push({ title: titleMatch[1].trim(), description: desc })
    }
  }
  return suggestions
}

// ---- Computed: AI findings & suggestions per provider ----

const findingsA = computed(() => parseFindings(resultA.value?.aiRawOutput || ''))
const findingsB = computed(() => parseFindings(resultB.value?.aiRawOutput || ''))
const suggestionsA = computed(() => parseSuggestions(resultA.value?.aiRawOutput || ''))
const suggestionsB = computed(() => parseSuggestions(resultB.value?.aiRawOutput || ''))

const findingCountA = computed(() => {
  const counts: Record<string, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
  for (const f of findingsA.value) counts[f.level]++
  return counts
})

const findingCountB = computed(() => {
  const counts: Record<string, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
  for (const f of findingsB.value) counts[f.level]++
  return counts
})

const totalFindingsA = computed(() => findingsA.value.length)
const totalFindingsB = computed(() => findingsB.value.length)

// Keyword overlap analysis
const STOP_WORDS = new Set(['the', 'a', 'an', 'is', 'are', 'was', 'were', 'be', 'been', 'being',
  'have', 'has', 'had', 'do', 'does', 'did', 'will', 'would', 'could', 'should', 'may', 'might',
  'can', 'shall', 'to', 'of', 'in', 'for', 'on', 'with', 'at', 'by', 'from', 'as', 'into',
  'this', 'that', 'these', 'those', 'it', 'its', 'and', 'or', 'but', 'not', 'no', 'if', 'then',
  'than', 'too', 'very', 'just', 'about', 'also', 'code', 'PR', 'pull', 'request'])

function extractKeywords(title: string): Set<string> {
  return new Set(
    title.toLowerCase()
      .replace(/[^a-z0-9一-鿿\s]/g, ' ')
      .split(/\s+/)
      .filter(w => w.length > 2 && !STOP_WORDS.has(w))
  )
}

function findingsOverlap(fsA: ParsedFinding[], fsB: ParsedFinding[]): Set<number> {
  // Returns indices of findings in A that have a match in B
  const bKeywords = fsB.map(f => extractKeywords(f.title))
  const matched = new Set<number>()
  for (let i = 0; i < fsA.length; i++) {
    const kwA = extractKeywords(fsA[i].title)
    if (kwA.size === 0) continue
    for (const kwB of bKeywords) {
      if (kwB.size === 0) continue
      let overlap = 0
      for (const w of kwA) { if (kwB.has(w)) overlap++ }
      if (overlap >= 2 || (overlap >= 1 && kwA.size <= 2)) {
        matched.add(i)
        break
      }
    }
  }
  return matched
}

const overlapIndices = computed(() => {
  // Returns indices in findingsA that overlap with findingsB
  return findingsOverlap(findingsA.value, findingsB.value)
})

const overlapCount = computed(() => overlapIndices.value.size)
const uniqueACount = computed(() => totalFindingsA.value - overlapCount.value)

const reverseOverlapIndices = computed(() => {
  // Finds indices in findingsB that overlap with findingsA
  const kwA = findingsA.value.map(f => extractKeywords(f.title))
  const matched = new Set<number>()
  for (let i = 0; i < findingsB.value.length; i++) {
    const kwB = extractKeywords(findingsB.value[i].title)
    if (kwB.size === 0) continue
    for (const kwa of kwA) {
      if (kwa.size === 0) continue
      let overlap = 0
      for (const w of kwB) { if (kwa.has(w)) overlap++ }
      if (overlap >= 2 || (overlap >= 1 && kwB.size <= 2)) {
        matched.add(i)
        break
      }
    }
  }
  return matched
})

const uniqueBCount = computed(() => totalFindingsB.value - reverseOverlapIndices.value.size)

const agreementRate = computed(() => {
  const total = overlapCount.value + uniqueACount.value + uniqueBCount.value
  if (total === 0) return 0
  return Math.round((overlapCount.value / total) * 100)
})

const uniqueA = computed(() => findingsA.value.filter((_, i) => !overlapIndices.value.has(i)))
const uniqueB = computed(() => findingsB.value.filter((_, i) => !reverseOverlapIndices.value.has(i)))

const expandedUnique = ref(false)

onMounted(() => {
  if (!store.isMultiProvider || providers.value.length < 2) {
    router.replace('/analysis')
  }
})
</script>

<template>
  <div class="comparison-page">
    <!-- Top bar -->
    <header class="top-bar">
      <button class="back-btn" @click="router.push('/analysis')">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        返回分析
      </button>
      <div class="top-title">
        <span class="logo-text">CodePilot</span>
        <span class="logo-divider">/</span>
        <span>模型对比分析</span>
      </div>
      <div class="provider-badges">
        <span
          v-for="p in providers"
          :key="p"
          class="provider-badge"
          :style="{ borderColor: providerColors[p] || 'var(--primary)', color: (providerColors[p] || 'var(--primary)') }"
        >
          <span class="badge-dot" :style="{ background: providerColors[p] || 'var(--primary)' }"></span>
          {{ providerLabels[p] || p }}
        </span>
      </div>
    </header>

    <div class="comparison-content">
      <!-- Section 1: AI 发现对比 -->
      <section class="section">
        <h2 class="section-title">AI 发现对比</h2>
        <div class="delta-badge" v-if="totalFindingsA !== totalFindingsB">
          {{ providerLabels[providerA] || providerA }} 比 {{ providerLabels[providerB] || providerB }}
          <strong>{{ totalFindingsA > totalFindingsB ? '多' : '少' }}发现 {{ Math.abs(totalFindingsA - totalFindingsB) }} 个风险</strong>
        </div>
        <div class="delta-badge" v-else>两个模型发现的风险数量相同</div>

        <div class="two-col">
          <div class="findings-card" v-for="p in providers" :key="p">
            <div class="findings-header">
              <span class="card-dot" :style="{ background: providerColors[p] || 'var(--primary)' }"></span>
              {{ providerLabels[p] || p }}
              <span class="findings-total">{{ p === providerA ? totalFindingsA : totalFindingsB }} 个风险</span>
            </div>
            <div class="findings-bars" v-if="(p === providerA ? totalFindingsA : totalFindingsB) > 0">
              <div class="findings-row" v-for="level in riskLevels" :key="level">
                <span class="findings-label" :style="{ color: levelColor(level) }">{{ levelLabel(level) }}</span>
                <div class="findings-bar-track">
                  <div
                    class="findings-bar-fill"
                    :style="{
                      width: (p === providerA ? totalFindingsA : totalFindingsB) > 0
                        ? (((p === providerA ? findingCountA[level] : findingCountB[level]) || 0) / (p === providerA ? totalFindingsA : totalFindingsB) * 100) + '%'
                        : '0%',
                      background: levelColor(level)
                    }"
                  ></div>
                </div>
                <span class="findings-val">{{ p === providerA ? (findingCountA[level] || 0) : (findingCountB[level] || 0) }}</span>
              </div>
            </div>
            <div v-else class="output-empty">该模型未发现风险</div>
          </div>
        </div>
      </section>

      <!-- Section 2: 发现重叠分析 -->
      <section class="section">
        <h2 class="section-title">发现重叠分析</h2>
        <div class="overlap-card" v-if="totalFindingsA > 0 || totalFindingsB > 0">
          <div class="overlap-stats">
            <div class="overlap-stat">
              <span class="overlap-number">{{ overlapCount }}</span>
              <span class="overlap-label">共同发现</span>
            </div>
            <div class="overlap-divider"></div>
            <div class="overlap-stat">
              <span class="overlap-number" :style="{ color: providerColors[providerA] || 'var(--primary)' }">{{ uniqueACount }}</span>
              <span class="overlap-label">仅 {{ providerLabels[providerA] || providerA }}</span>
            </div>
            <div class="overlap-divider"></div>
            <div class="overlap-stat">
              <span class="overlap-number" :style="{ color: providerColors[providerB] || 'var(--primary)' }">{{ uniqueBCount }}</span>
              <span class="overlap-label">仅 {{ providerLabels[providerB] || providerB }}</span>
            </div>
          </div>
          <div class="agreement-row">
            <span class="agreement-label">一致率</span>
            <div class="agreement-bar-track">
              <div
                class="agreement-bar-fill"
                :style="{ width: agreementRate + '%' }"
              ></div>
            </div>
            <span class="agreement-value">{{ agreementRate }}%</span>
          </div>

          <!-- Unique findings lists -->
          <div class="unique-lists" v-if="uniqueACount > 0 || uniqueBCount > 0">
            <button class="expand-toggle" @click="expandedUnique = !expandedUnique">
              {{ expandedUnique ? '收起' : '展开' }}独有发现详情
              <svg :class="{ rotated: expandedUnique }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
            </button>
            <div class="unique-grid" v-if="expandedUnique">
              <div class="unique-col" v-if="uniqueA.length > 0">
                <div class="unique-col-header" :style="{ color: providerColors[providerA] || 'var(--primary)' }">
                  仅 {{ providerLabels[providerA] || providerA }} 发现 ({{ uniqueA.length }})
                </div>
                <ul class="unique-list">
                  <li v-for="(f, i) in uniqueA" :key="i">
                    <span class="finding-level-tag" :style="{ background: levelColor(f.level), color: '#fff' }">{{ levelLabel(f.level) }}</span>
                    <span class="finding-title-text">{{ f.title }}</span>
                  </li>
                </ul>
              </div>
              <div class="unique-col" v-if="uniqueB.length > 0">
                <div class="unique-col-header" :style="{ color: providerColors[providerB] || 'var(--primary)' }">
                  仅 {{ providerLabels[providerB] || providerB }} 发现 ({{ uniqueB.length }})
                </div>
                <ul class="unique-list">
                  <li v-for="(f, i) in uniqueB" :key="i">
                    <span class="finding-level-tag" :style="{ background: levelColor(f.level), color: '#fff' }">{{ levelLabel(f.level) }}</span>
                    <span class="finding-title-text">{{ f.title }}</span>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="output-empty">两个模型均未发现 AI 风险</div>
      </section>

      <!-- Section 3: AI 评审输出对比 -->
      <section class="section">
        <h2 class="section-title">AI 评审输出对比</h2>
        <div class="two-col output-cols">
          <div class="output-card">
            <div class="output-card-header">
              <span class="card-dot" :style="{ background: providerColors[providerA] || 'var(--primary)' }"></span>
              {{ providerLabels[providerA] || providerA }}
            </div>
            <div
              ref="leftPanel"
              class="output-body markdown-body"
              v-html="htmlA"
              @scroll="handleScroll('left')"
            ></div>
            <div v-if="!htmlA" class="output-empty">等待评审结果...</div>
          </div>
          <div class="output-card">
            <div class="output-card-header">
              <span class="card-dot" :style="{ background: providerColors[providerB] || 'var(--primary)' }"></span>
              {{ providerLabels[providerB] || providerB }}
            </div>
            <div
              ref="rightPanel"
              class="output-body markdown-body"
              v-html="htmlB"
              @scroll="handleScroll('right')"
            ></div>
            <div v-if="!htmlB" class="output-empty">等待评审结果...</div>
          </div>
        </div>
      </section>

      <!-- Section 4: 风险评分对比 -->
      <section class="section">
        <h2 class="section-title">风险评分对比</h2>
        <div class="delta-badge" v-if="scoreDelta > 0">
          评分差异: <strong>{{ scoreDelta }}</strong> 分
        </div>

        <div class="two-col">
          <!-- Provider A -->
          <div class="score-card" v-if="resultA?.riskScore">
            <div class="score-card-header">
              <span class="card-dot" :style="{ background: providerColors[providerA] || 'var(--primary)' }"></span>
              {{ providerLabels[providerA] || providerA }}
            </div>
            <div class="score-ring" :style="{ borderColor: riskColor(resultA.riskScore.totalScore) }">
              <span class="score-number" :style="{ color: riskColor(resultA.riskScore.totalScore) }">{{ resultA.riskScore.totalScore }}</span>
              <span class="score-max">/ {{ resultA.riskScore.maxScore }}</span>
            </div>
            <div class="score-breakdown">
              <div class="breakdown-row" v-for="dim in [
                { key: 'fileCountScore', label: '文件数量' },
                { key: 'ruleFindingsScore', label: '规则发现' },
                { key: 'moduleScore', label: '模块评分' },
                { key: 'volumeScore', label: '变更量' },
                { key: 'aiScore', label: 'AI评分' }
              ]" :key="dim.key">
                <span class="breakdown-label">{{ dim.label }}</span>
                <div class="breakdown-bar-track">
                  <div
                    class="breakdown-bar-fill"
                    :style="{
                      width: Math.min(100, ((resultA.riskScore as any)[dim.key] / resultA.riskScore.maxScore) * 100) + '%',
                      background: riskColor((resultA.riskScore as any)[dim.key])
                    }"
                  ></div>
                </div>
                <span class="breakdown-val">{{ (resultA.riskScore as any)[dim.key] }}</span>
              </div>
            </div>
          </div>

          <!-- Provider B -->
          <div class="score-card" v-if="resultB?.riskScore">
            <div class="score-card-header">
              <span class="card-dot" :style="{ background: providerColors[providerB] || 'var(--primary)' }"></span>
              {{ providerLabels[providerB] || providerB }}
            </div>
            <div class="score-ring" :style="{ borderColor: riskColor(resultB.riskScore.totalScore) }">
              <span class="score-number" :style="{ color: riskColor(resultB.riskScore.totalScore) }">{{ resultB.riskScore.totalScore }}</span>
              <span class="score-max">/ {{ resultB.riskScore.maxScore }}</span>
            </div>
            <div class="score-breakdown">
              <div class="breakdown-row" v-for="dim in [
                { key: 'fileCountScore', label: '文件数量' },
                { key: 'ruleFindingsScore', label: '规则发现' },
                { key: 'moduleScore', label: '模块评分' },
                { key: 'volumeScore', label: '变更量' },
                { key: 'aiScore', label: 'AI评分' }
              ]" :key="dim.key">
                <span class="breakdown-label">{{ dim.label }}</span>
                <div class="breakdown-bar-track">
                  <div
                    class="breakdown-bar-fill"
                    :style="{
                      width: Math.min(100, ((resultB.riskScore as any)[dim.key] / resultB.riskScore.maxScore) * 100) + '%',
                      background: riskColor((resultB.riskScore as any)[dim.key])
                    }"
                  ></div>
                </div>
                <span class="breakdown-val">{{ (resultB.riskScore as any)[dim.key] }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Section 5: 建议对比 -->
      <section class="section">
        <h2 class="section-title">建议对比</h2>
        <div class="two-col">
          <div class="suggest-card" v-for="p in providers" :key="p">
            <div class="suggest-header">
              <span class="card-dot" :style="{ background: providerColors[p] || 'var(--primary)' }"></span>
              {{ providerLabels[p] || p }}
              <span class="suggest-count">{{ p === providerA ? suggestionsA.length : suggestionsB.length }} 条建议</span>
            </div>
            <ul class="suggest-list" v-if="(p === providerA ? suggestionsA.length : suggestionsB.length) > 0">
              <li v-for="(s, i) in (p === providerA ? suggestionsA : suggestionsB)" :key="i" class="suggest-item">
                <span class="suggest-index">{{ i + 1 }}</span>
                <span class="suggest-title">{{ s.title }}</span>
              </li>
            </ul>
            <div v-else class="output-empty">该模型未提供建议</div>
          </div>
        </div>
      </section>

      <!-- Section 6: 指标对比 -->
      <section class="section">
        <h2 class="section-title">指标对比</h2>
        <table class="metrics-table">
          <thead>
            <tr>
              <th>指标</th>
              <th v-for="p in providers" :key="p">{{ providerLabels[p] || p }}</th>
              <th>差异</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td class="metric-name">Token 总量</td>
              <td v-for="p in providers" :key="p">
                {{ store.providerResults[p]?.tokenUsage ? (store.providerResults[p].tokenUsage.totalTokens / 1000).toFixed(1) + 'k' : '-' }}
              </td>
              <td class="metric-delta">
                {{ resultA?.tokenUsage && resultB?.tokenUsage ? (Math.abs(resultA.tokenUsage.totalTokens - resultB.tokenUsage.totalTokens) / 1000).toFixed(1) + 'k' : '-' }}
              </td>
            </tr>
            <tr>
              <td class="metric-name">Prompt Token</td>
              <td v-for="p in providers" :key="p">
                {{ store.providerResults[p]?.tokenUsage ? (store.providerResults[p].tokenUsage.promptTokens / 1000).toFixed(1) + 'k' : '-' }}
              </td>
              <td class="metric-delta">
                {{ resultA?.tokenUsage && resultB?.tokenUsage ? (Math.abs(resultA.tokenUsage.promptTokens - resultB.tokenUsage.promptTokens) / 1000).toFixed(1) + 'k' : '-' }}
              </td>
            </tr>
            <tr>
              <td class="metric-name">Completion Token</td>
              <td v-for="p in providers" :key="p">
                {{ store.providerResults[p]?.tokenUsage ? (store.providerResults[p].tokenUsage.completionTokens / 1000).toFixed(1) + 'k' : '-' }}
              </td>
              <td class="metric-delta">
                {{ resultA?.tokenUsage && resultB?.tokenUsage ? (Math.abs(resultA.tokenUsage.completionTokens - resultB.tokenUsage.completionTokens) / 1000).toFixed(1) + 'k' : '-' }}
              </td>
            </tr>
            <tr>
              <td class="metric-name">Chunk 成功率</td>
              <td v-for="p in providers" :key="p">
                {{ store.providerResults[p]?.tokenUsage ? Math.round((store.providerResults[p].tokenUsage.successfulChunks / store.providerResults[p].tokenUsage.totalChunks) * 100) + '%' : '-' }}
              </td>
              <td class="metric-delta">-</td>
            </tr>
            <tr>
              <td class="metric-name">置信度</td>
              <td v-for="p in providers" :key="p">
                <span v-if="store.providerResults[p]?.confidenceScores" :class="{
                  'conf-high': store.providerResults[p].confidenceScores.confidenceLevel === 'HIGH',
                  'conf-mid': store.providerResults[p].confidenceScores.confidenceLevel === 'MEDIUM',
                  'conf-low': store.providerResults[p].confidenceScores.confidenceLevel === 'LOW'
                }">
                  {{ Math.round(store.providerResults[p].confidenceScores.overallConfidence) }}%
                  {{ store.providerResults[p].confidenceScores.confidenceLevel === 'HIGH' ? '高' : store.providerResults[p].confidenceScores.confidenceLevel === 'MEDIUM' ? '中' : '低' }}
                </span>
                <span v-else>-</span>
              </td>
              <td class="metric-delta">
                {{ resultA?.confidenceScores && resultB?.confidenceScores ? Math.abs(Math.round(resultA.confidenceScores.overallConfidence) - Math.round(resultB.confidenceScores.overallConfidence)) + '%' : '-' }}
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Section 7: 风险等级分布对比 -->
      <section class="section">
        <h2 class="section-title">风险等级分布对比</h2>
        <div class="two-col">
          <div class="dist-card" v-for="p in providers" :key="p">
            <div class="dist-header">
              <span class="card-dot" :style="{ background: providerColors[p] || 'var(--primary)' }"></span>
              {{ providerLabels[p] || p }}
            </div>
            <div class="dist-bars" v-if="store.providerResults[p]?.riskScore?.ruleBreakdown && Object.keys(store.providerResults[p].riskScore.ruleBreakdown).length > 0">
              <div
                class="dist-row"
                v-for="level in riskLevels"
                :key="level"
              >
                <span class="dist-label" :style="{ color: levelColor(level) }">{{ levelLabel(level) }}</span>
                <div class="dist-bar-track">
                  <div
                    class="dist-bar-fill"
                    :style="{
                      width: maxBreakdown(p) > 0 ? ((store.providerResults[p].riskScore.ruleBreakdown[level] || 0) / maxBreakdown(p) * 100) + '%' : '0%',
                      background: levelColor(level)
                    }"
                  ></div>
                </div>
                <span class="dist-val">{{ store.providerResults[p].riskScore.ruleBreakdown[level] || 0 }}</span>
              </div>
            </div>
            <div v-else-if="!store.providerResults[p]" class="output-empty">暂无数据，请重新运行评审</div>
            <div v-else class="output-empty">未触发静态规则，无风险等级分布</div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.comparison-page {
  min-height: 100vh;
  background: var(--bg-root);
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 100;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  color: var(--text-secondary);
  font-size: 13px;
  padding: 7px 14px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
  font-family: var(--font-sans);
}

.back-btn:hover {
  border-color: var(--border-light);
  color: var(--text-primary);
}

.top-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.logo-text {
  font-weight: 800;
  font-size: 18px;
  background: var(--gradient-hero);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.logo-divider {
  color: var(--text-muted);
  font-weight: 400;
}

.provider-badges {
  display: flex;
  gap: 8px;
}

.provider-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  padding: 4px 12px;
  border: 1px solid;
  border-radius: 100px;
}

.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.comparison-content {
  max-width: 1600px;
  margin: 0 auto;
  padding: 24px 20px;
}

.section {
  margin-bottom: 40px;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.delta-badge {
  display: inline-block;
  font-size: 13px;
  color: var(--text-muted);
  padding: 4px 12px;
  background: var(--bg-elevated);
  border-radius: 100px;
  margin-bottom: 16px;
}

.delta-badge strong {
  color: var(--text-primary);
}

/* Two column grid */
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

/* Score cards */
.score-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 24px;
  text-align: center;
}

.score-card-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.card-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.score-ring {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  border: 5px solid;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
}

.score-number {
  font-size: 28px;
  font-weight: 800;
  line-height: 1;
}

.score-max {
  font-size: 11px;
  color: var(--text-muted);
}

.score-breakdown {
  text-align: left;
}

.breakdown-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.breakdown-label {
  width: 70px;
  font-size: 12px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.breakdown-bar-track {
  flex: 1;
  height: 4px;
  background: var(--bg-elevated);
  border-radius: 2px;
  overflow: hidden;
}

.breakdown-bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.6s ease;
}

.breakdown-val {
  width: 30px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  text-align: right;
}

/* Output columns */
.output-cols {
  align-items: start;
}

.output-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.output-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border);
  background: var(--bg-elevated);
}

.output-body {
  padding: 16px;
  max-height: 600px;
  overflow-y: auto;
}

.output-empty {
  padding: 40px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}

/* Metrics table */
.metrics-table {
  width: 100%;
  border-collapse: collapse;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.metrics-table th {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 12px 16px;
  text-align: left;
  background: var(--bg-elevated);
  border-bottom: 1px solid var(--border);
}

.metrics-table td {
  font-size: 13px;
  color: var(--text-secondary);
  padding: 10px 16px;
  border-bottom: 1px solid var(--border);
}

.metrics-table tr:last-child td {
  border-bottom: none;
}

.metric-name {
  font-weight: 500;
  color: var(--text-primary);
}

.metric-delta {
  color: var(--primary);
  font-weight: 600;
}

/* Distribution cards */
.dist-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 20px;
}

.dist-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.dist-bars {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dist-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dist-label {
  width: 36px;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.dist-bar-track {
  flex: 1;
  height: 6px;
  background: var(--bg-elevated);
  border-radius: 3px;
  overflow: hidden;
}

.dist-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.6s ease;
}

.dist-val {
  width: 24px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  text-align: right;
}

/* AI findings comparison */
.findings-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 20px;
}

.findings-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.findings-total {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
}

.findings-bars {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.findings-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.findings-label {
  width: 36px;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.findings-bar-track {
  flex: 1;
  height: 6px;
  background: var(--bg-elevated);
  border-radius: 3px;
  overflow: hidden;
}

.findings-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.6s ease;
}

.findings-val {
  width: 24px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  text-align: right;
}

/* Overlap analysis */
.overlap-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 24px;
}

.overlap-stats {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  margin-bottom: 20px;
}

.overlap-stat {
  text-align: center;
  padding: 0 32px;
}

.overlap-number {
  display: block;
  font-size: 28px;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1;
}

.overlap-label {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 6px;
}

.overlap-divider {
  width: 1px;
  height: 40px;
  background: var(--border);
}

.agreement-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--bg-elevated);
  border-radius: var(--radius-sm);
}

.agreement-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  white-space: nowrap;
}

.agreement-bar-track {
  flex: 1;
  height: 8px;
  background: var(--bg-root);
  border-radius: 4px;
  overflow: hidden;
}

.agreement-bar-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--gradient-hero);
  transition: width 0.8s ease;
}

.agreement-value {
  font-size: 14px;
  font-weight: 700;
  color: var(--primary);
  white-space: nowrap;
}

/* Unique findings lists */
.unique-lists {
  margin-top: 16px;
}

.expand-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  padding: 6px 0;
  font-family: var(--font-sans);
  transition: color 0.15s;
}

.expand-toggle:hover {
  color: var(--text-primary);
}

.expand-toggle svg {
  transition: transform 0.2s ease;
}

.expand-toggle svg.rotated {
  transform: rotate(180deg);
}

.unique-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 12px;
}

.unique-col-header {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.unique-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.unique-list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 0;
  font-size: 12px;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border);
  line-height: 1.5;
}

.unique-list li:last-child {
  border-bottom: none;
}

.finding-level-tag {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 3px;
  flex-shrink: 0;
  margin-top: 2px;
  text-transform: uppercase;
}

.finding-title-text {
  flex: 1;
}

/* Suggestion comparison */
.suggest-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 20px;
}

.suggest-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.suggest-count {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
}

.suggest-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.suggest-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.suggest-item:last-child {
  border-bottom: none;
}

.suggest-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--bg-elevated);
  font-size: 11px;
  font-weight: 700;
  color: var(--text-muted);
  flex-shrink: 0;
}

.suggest-title {
  flex: 1;
  padding-top: 2px;
}

/* Confidence colors */
.conf-high { color: #22c55e; }
.conf-mid { color: #f59e0b; }
.conf-low { color: #ef4444; }

/* Markdown body */
.markdown-body {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.markdown-body :deep(h3) {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 20px 0 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--border);
}

.markdown-body :deep(h3:first-of-type) {
  margin-top: 0;
}

.markdown-body :deep(h4) {
  font-size: 13px;
  font-weight: 600;
  margin: 14px 0 6px;
  color: var(--text-primary);
}

.markdown-body :deep(p) {
  margin: 6px 0;
}

.markdown-body :deep(strong) {
  color: var(--text-primary);
  font-weight: 600;
}

.markdown-body :deep(code) {
  font-family: var(--font-mono);
  font-size: 12px;
}

.markdown-body :deep(:not(pre) > code) {
  background: var(--code-bg);
  padding: 1px 5px;
  border-radius: 3px;
  color: var(--primary);
}

.markdown-body :deep(pre) {
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  overflow-x: auto;
  margin: 8px 0;
}

.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  color: var(--text-secondary);
}

.markdown-body :deep(li) {
  margin-bottom: 3px;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 16px;
  margin: 4px 0;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--primary);
  padding-left: 10px;
  margin: 10px 0;
  color: var(--text-muted);
}

@media (max-width: 900px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
