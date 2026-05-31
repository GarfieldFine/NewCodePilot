<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getHistory, getTrends, getRecentHistory, getRepos, getAnalysisResult } from '@/api'
import type { HistoryItem, TrendPoint, TrendData, RepoInfo, HistoryPageData } from '@/types'
import { generateMarkdownReport, downloadMarkdown } from '@/utils/report'

const router = useRouter()

// State
const repos = ref<RepoInfo[]>([])
const selectedRepo = ref('')
const timeRange = ref(30)
const trendData = ref<TrendData | null>(null)
const history = ref<HistoryPageData | null>(null)
const loading = ref(false)
const showGlobal = ref(true)

// SVG chart dimensions
const CHART_W = 560
const CHART_H = 200
const PAD = { top: 16, right: 16, bottom: 28, left: 42 }
const plotW = CHART_W - PAD.left - PAD.right
const plotH = CHART_H - PAD.top - PAD.bottom

onMounted(async () => {
  try {
    const [repoList, recent] = await Promise.all([
      getRepos(),
      getRecentHistory(50)
    ])
    repos.value = repoList
    if (repoList.length > 0) {
      selectedRepo.value = repoList[0].repoKey
      showGlobal.value = false
      await loadData()
    } else {
      showGlobal.value = true
      history.value = {
        items: recent,
        total: recent.length,
        page: 1,
        size: 50,
        totalPages: 1
      }
    }
  } catch { /* empty repo */ }
})

async function loadData() {
  if (!selectedRepo.value) return
  loading.value = true
  try {
    const [owner, repo] = selectedRepo.value.split('/')
    const [t, h] = await Promise.all([
      getTrends(owner, repo, timeRange.value),
      getHistory(owner, repo, 1, 50)
    ])
    trendData.value = t
    history.value = h
  } catch { /* handle error */ }
  loading.value = false
}

watch(timeRange, () => { if (selectedRepo.value) loadData() })

function selectRepo(key: string) {
  selectedRepo.value = key
  showGlobal.value = false
  loadData()
}

// Chart helpers
function xScale(index: number, total: number): number {
  if (total <= 1) return PAD.left + plotW / 2
  return PAD.left + (index / (total - 1)) * plotW
}

function yScale(value: number, max: number): number {
  if (max === 0) return CHART_H - PAD.bottom
  return CHART_H - PAD.bottom - (value / max) * plotH
}

function riskColor(score: number): string {
  if (score >= 70) return '#ef4444'
  if (score >= 40) return '#f97316'
  if (score >= 20) return '#fbbf24'
  return '#34d399'
}

function buildPath(points: TrendPoint[], getValue: (p: TrendPoint) => number): string {
  if (!points.length) return ''
  const maxVal = Math.max(...points.map(getValue), 1)
  return points.map((p, i) =>
    `${i === 0 ? 'M' : 'L'} ${xScale(i, points.length).toFixed(1)} ${yScale(getValue(p), maxVal).toFixed(1)}`
  ).join(' ')
}

function buildAreaPath(points: TrendPoint[], getValue: (p: TrendPoint) => number): string {
  const line = buildPath(points, getValue)
  if (!points.length) return ''
  const lastX = xScale(points.length - 1, points.length).toFixed(1)
  return `${line} L ${lastX} ${CHART_H - PAD.bottom} L ${PAD.left} ${CHART_H - PAD.bottom} Z`
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function formatDateTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

// Grid lines
const yTicks = [0, 0.25, 0.5, 0.75, 1.0]

const maxFindings = computed(() => {
  const pts = trendData.value?.findingsTrend
  if (!pts?.length) return 10
  return Math.max(...pts.map(p => p.avgFindings), 1)
})

function goToAnalysis(item: HistoryItem) {
  if (item.analysisId) {
    router.push({ path: '/analysis', query: { analysisId: item.analysisId } })
  }
}

const exportingId = ref<string | null>(null)

async function exportHistoryReport(item: HistoryItem) {
  if (!item.analysisId || exportingId.value) return
  exportingId.value = item.analysisId
  try {
    const result = await getAnalysisResult(item.analysisId)
    if (result) {
      const md = generateMarkdownReport(result)
      const ts = new Date().toISOString().replace(/[:.]/g, '-').substring(0, 19)
      const filename = `CodePilot-PR${item.prNumber}-${ts}.md`
      downloadMarkdown(md, filename)
    }
  } catch { /* download failed */ }
  exportingId.value = null
}
</script>

<template>
  <div class="history-page">
    <header class="page-header">
      <div class="header-left">
        <button class="back-btn" @click="router.push('/')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
        </button>
        <h1>代码质量趋势</h1>
      </div>
      <div class="header-controls">
        <div v-if="repos.length > 0" class="repo-selector">
          <select v-model="selectedRepo" @change="selectRepo(($event.target as HTMLSelectElement).value)">
            <option v-for="r in repos" :key="r.repoKey" :value="r.repoKey">
              {{ r.repoKey }}
            </option>
          </select>
        </div>
        <div class="time-tabs">
          <button :class="{ active: timeRange === 7 }" @click="timeRange = 7">7 天</button>
          <button :class="{ active: timeRange === 14 }" @click="timeRange = 14">14 天</button>
          <button :class="{ active: timeRange === 30 }" @click="timeRange = 30">30 天</button>
        </div>
      </div>
    </header>

    <!-- Empty state: no data yet -->
    <div v-if="!loading && !trendData && !history?.items?.length" class="empty-state">
      <div class="empty-icon">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
      </div>
      <h2>暂无分析数据</h2>
      <p>完成首次 PR 分析后，代码质量趋势将在此展示</p>
      <button class="go-btn" @click="router.push('/')">去分析 PR</button>
    </div>

    <div v-else-if="trendData" class="dashboard">
      <!-- Summary cards -->
      <div class="summary-row">
        <div class="summary-card">
          <span class="card-label">已分析 PR</span>
          <span class="card-value">{{ trendData.totalPRsAnalyzed }}</span>
        </div>
        <div class="summary-card">
          <span class="card-label">平均风险分</span>
          <span class="card-value" :style="{ color: riskColor(trendData.overallAvgRiskScore) }">
            {{ trendData.overallAvgRiskScore }}
          </span>
        </div>
        <div class="summary-card">
          <span class="card-label">平均发现数</span>
          <span class="card-value">{{ trendData.overallAvgFindings }}</span>
        </div>
        <div class="summary-card">
          <span class="card-label">趋势周期</span>
          <span class="card-value">{{ trendData.trendDays }} 天</span>
        </div>
      </div>

      <!-- Charts row -->
      <div class="charts-row">
        <!-- Risk Score Trend -->
        <div class="chart-card">
          <h3>风险评分趋势</h3>
          <svg :viewBox="`0 0 ${CHART_W} ${CHART_H}`" class="trend-chart">
            <!-- Y axis grid lines -->
            <line v-for="t in yTicks" :key="'y'+t"
              :x1="PAD.left" :x2="CHART_W - PAD.right"
              :y1="yScale(t, 1)" :y2="yScale(t, 1)"
              stroke="var(--border)" stroke-width="0.5" stroke-dasharray="3,3"
            />
            <text v-for="t in yTicks" :key="'yl'+t"
              :x="PAD.left - 6" :y="yScale(t, 1) + 4"
              text-anchor="end" class="axis-label"
            >{{ Math.round(t * 100) }}</text>

            <!-- Risk area -->
            <path
              :d="buildAreaPath(trendData.riskScoreTrend, p => p.avgRiskScore)"
              fill="url(#riskGrad)" opacity="0.15"
            />
            <!-- Risk line -->
            <path
              :d="buildPath(trendData.riskScoreTrend, p => p.avgRiskScore)"
              fill="none" stroke="var(--primary)" stroke-width="2" stroke-linejoin="round"
            />

            <!-- Data points -->
            <circle v-for="(p, i) in trendData.riskScoreTrend" :key="'rp'+i"
              :cx="xScale(i, trendData.riskScoreTrend.length)" :cy="yScale(p.avgRiskScore, 100)"
              r="3" fill="var(--primary)" stroke="var(--bg-card)" stroke-width="1.5"
            />

            <!-- X axis labels -->
            <text v-for="(p, i) in trendData.riskScoreTrend" :key="'rx'+i"
              :x="xScale(i, trendData.riskScoreTrend.length)" :y="CHART_H - 4"
              text-anchor="middle" class="axis-label"
              v-show="trendData.riskScoreTrend.length <= 14 || i % Math.ceil(trendData.riskScoreTrend.length / 10) === 0"
            >{{ formatDate(p.date) }}</text>

            <defs>
              <linearGradient id="riskGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="var(--primary)" stop-opacity="0.3"/>
                <stop offset="100%" stop-color="var(--primary)" stop-opacity="0"/>
              </linearGradient>
            </defs>
          </svg>
        </div>

        <!-- Findings Trend -->
        <div class="chart-card">
          <h3>发现问题趋势</h3>
          <svg :viewBox="`0 0 ${CHART_W} ${CHART_H}`" class="trend-chart">
            <line v-for="t in yTicks" :key="'y'+t"
              :x1="PAD.left" :x2="CHART_W - PAD.right"
              :y1="yScale(t, 1)" :y2="yScale(t, 1)"
              stroke="var(--border)" stroke-width="0.5" stroke-dasharray="3,3"
            />
            <text v-for="t in yTicks" :key="'yl'+t"
              :x="PAD.left - 6" :y="yScale(t, 1) + 4"
              text-anchor="end" class="axis-label"
            >{{ Math.round(t * (maxFindings || 10)) }}</text>

            <path
              :d="buildAreaPath(trendData.findingsTrend, p => p.avgFindings)"
              fill="url(#findGrad)" opacity="0.15"
            />
            <path
              :d="buildPath(trendData.findingsTrend, p => p.avgFindings)"
              fill="none" stroke="var(--warning)" stroke-width="2" stroke-linejoin="round"
            />

            <circle v-for="(p, i) in trendData.findingsTrend" :key="'fp'+i"
              :cx="xScale(i, trendData.findingsTrend.length)" :cy="yScale(p.avgFindings, maxFindings)"
              r="3" fill="var(--warning)" stroke="var(--bg-card)" stroke-width="1.5"
            />

            <text v-for="(p, i) in trendData.findingsTrend" :key="'fx'+i"
              :x="xScale(i, trendData.findingsTrend.length)" :y="CHART_H - 4"
              text-anchor="middle" class="axis-label"
              v-show="trendData.findingsTrend.length <= 14 || i % Math.ceil(trendData.findingsTrend.length / 10) === 0"
            >{{ formatDate(p.date) }}</text>

            <defs>
              <linearGradient id="findGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="var(--warning)" stop-opacity="0.3"/>
                <stop offset="100%" stop-color="var(--warning)" stop-opacity="0"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
      </div>

      <!-- History table -->
      <div class="history-section">
        <h3>分析历史</h3>
        <div class="history-table-wrap">
          <table class="history-table">
            <thead>
              <tr>
                <th>PR #</th>
                <th>标题</th>
                <th>作者</th>
                <th>风险分</th>
                <th>发现数</th>
                <th>时间</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in history?.items" :key="item.analysisId" @click="goToAnalysis(item)" class="clickable-row">
                <td class="mono">#{{ item.prNumber }}</td>
                <td class="title-cell">{{ item.prTitle }}</td>
                <td>{{ item.author }}</td>
                <td>
                  <span class="risk-badge" :style="{ background: riskColor(item.riskScore), color: '#fff' }">
                    {{ item.riskScore }}
                  </span>
                </td>
                <td class="mono">{{ item.totalFindings }}</td>
                <td class="time-cell">{{ formatDateTime(item.createdAt) }}</td>
                <td>
                  <button class="row-export-btn" @click.stop="exportHistoryReport(item)" :disabled="exportingId === item.analysisId">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                  </button>
                </td>
              </tr>
              <tr v-if="!history?.items?.length">
                <td colspan="7" class="empty-row">暂无历史记录</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Global view: no trends yet, show recent -->
    <div v-else-if="showGlobal && history?.items?.length" class="history-section standalone">
      <h3>最近分析</h3>
      <div class="history-table-wrap">
        <table class="history-table">
          <thead>
            <tr>
              <th>PR #</th>
              <th>标题</th>
              <th>作者</th>
              <th>风险分</th>
              <th>发现数</th>
              <th>时间</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in history.items" :key="item.analysisId" @click="goToAnalysis(item)" class="clickable-row">
              <td class="mono">#{{ item.prNumber }}</td>
              <td class="title-cell">{{ item.prTitle }}</td>
              <td>{{ item.author }}</td>
              <td>
                <span class="risk-badge" :style="{ background: riskColor(item.riskScore), color: '#fff' }">
                  {{ item.riskScore }}
                </span>
              </td>
              <td class="mono">{{ item.totalFindings }}</td>
              <td class="time-cell">{{ formatDateTime(item.createdAt) }}</td>
              <td>
                <button class="row-export-btn" @click.stop="exportHistoryReport(item)" :disabled="exportingId === item.analysisId">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.history-page {
  min-height: 100vh;
  background: var(--bg-primary);
  padding: 24px 32px;
  max-width: 1280px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h1 {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.back-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.back-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.repo-selector select {
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 13px;
  font-family: var(--font-mono);
  cursor: pointer;
  outline: none;
}

.repo-selector select:focus {
  border-color: var(--primary);
}

.time-tabs {
  display: flex;
  border-radius: var(--radius-sm);
  overflow: hidden;
  border: 1px solid var(--border);
}

.time-tabs button {
  padding: 6px 14px;
  border: none;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.time-tabs button + button {
  border-left: 1px solid var(--border);
}

.time-tabs button.active {
  background: var(--primary);
  color: #fff;
}

.time-tabs button:hover:not(.active) {
  background: var(--bg-elevated);
  color: var(--text-primary);
}

/* Empty state */
.empty-state {
  text-align: center;
  padding: 80px 24px;
}

.empty-icon {
  color: var(--text-muted);
  margin-bottom: 16px;
}

.empty-state h2 {
  font-size: 18px;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.empty-state p {
  color: var(--text-muted);
  margin-bottom: 24px;
  font-size: 14px;
}

.go-btn {
  padding: 10px 24px;
  border-radius: var(--radius-md);
  border: none;
  background: var(--primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}

.go-btn:hover {
  filter: brightness(1.1);
}

/* Dashboard */
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Summary cards */
.summary-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.summary-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.card-label {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
}

.card-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-mono);
}

/* Charts */
.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.chart-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 20px;
}

.chart-card h3 {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.trend-chart {
  width: 100%;
  height: auto;
}

.axis-label {
  font-size: 9px;
  fill: var(--text-muted);
  font-family: var(--font-mono);
}

/* History table */
.history-section {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 20px;
}

.history-section.standalone {
  margin-top: 0;
}

.history-section h3 {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 16px;
}

.history-table-wrap {
  overflow-x: auto;
}

.history-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.history-table th {
  text-align: left;
  padding: 10px 14px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid var(--border);
}

.history-table td {
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
  color: var(--text-primary);
}

.history-table tbody tr:last-child td {
  border-bottom: none;
}

.clickable-row {
  cursor: pointer;
  transition: background 0.1s;
}

.clickable-row:hover {
  background: var(--bg-elevated);
}

.mono {
  font-family: var(--font-mono);
  font-size: 12px;
}

.title-cell {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time-cell {
  color: var(--text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.risk-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 100px;
  font-size: 11px;
  font-weight: 600;
  font-family: var(--font-mono);
  min-width: 28px;
  text-align: center;
}

.empty-row {
  text-align: center;
  color: var(--text-muted);
  padding: 32px 14px !important;
}

.row-export-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s;
  padding: 0;
}

.row-export-btn:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
  background: rgba(99, 102, 241, 0.08);
}

.row-export-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>

