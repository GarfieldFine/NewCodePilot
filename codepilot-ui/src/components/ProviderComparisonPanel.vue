<script setup lang="ts">
import { ref, computed } from 'vue'
import { marked } from 'marked'
import type { AnalysisResult } from '@/types'

const props = defineProps<{
  providers: string[]
  results: Record<string, AnalysisResult>
  streams: Record<string, string>
}>()

const collapsed = ref(false)
const activeProvider = ref(props.providers[0] || '')

const providerLabels: Record<string, string> = {
  openai: 'OpenAI', deepseek: 'DeepSeek', claude: 'Claude', qwen: 'Qwen', siliconflow: '硅基流动'
}

const providerColors: Record<string, string> = {
  openai: '#10a37f', deepseek: '#6366f1', claude: '#d97706', qwen: '#2563eb', siliconflow: '#8b5cf6'
}

function riskLevelColor(score: number): string {
  if (score >= 70) return '#ef4444'
  if (score >= 40) return '#f59e0b'
  return '#22c55e'
}

function selectProvider(name: string) {
  activeProvider.value = name
}

const activeContent = computed(() => {
  const result = props.results[activeProvider.value]
  if (result) return result.aiRawOutput
  return props.streams[activeProvider.value] || ''
})

const activeHtml = computed(() => {
  const content = activeContent.value
  if (!content) return ''
  return marked.parse(content) as string
})

const riskDelta = computed(() => {
  const scores = props.providers
    .map(p => props.results[p]?.riskScore?.totalScore)
    .filter((s): s is number => s !== undefined && s !== null)
  if (scores.length < 2) return null
  const max = Math.max(...scores)
  const min = Math.min(...scores)
  return { max, min, delta: max - min }
})

const hasAnyResults = computed(() =>
  props.providers.some(p => props.results[p])
)

const hasAnyStreaming = computed(() =>
  props.providers.some(p => props.streams[p])
)
</script>

<template>
  <div class="comparison-panel" v-if="providers.length > 1">
    <div class="comparison-header" @click="collapsed = !collapsed">
      <div class="header-left">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/>
        </svg>
        <span class="header-title">模型对比</span>
        <span v-if="hasAnyStreaming && !hasAnyResults" class="streaming-badge">评审中...</span>
      </div>
      <svg
        class="collapse-icon"
        :class="{ collapsed }"
        width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
      >
        <polyline points="6 9 12 15 18 9"/>
      </svg>
    </div>

    <div v-if="!collapsed" class="comparison-body">
      <!-- Tab bar -->
      <div class="tab-bar">
        <button
          v-for="provider in providers"
          :key="provider"
          class="tab"
          :class="{ active: provider === activeProvider }"
          :style="provider === activeProvider ? {
            borderBottomColor: providerColors[provider] || 'var(--primary)',
            color: 'var(--text-primary)'
          } : {}"
          @click="selectProvider(provider)"
        >
          <span
            class="tab-dot"
            :style="{ background: providerColors[provider] || 'var(--primary)' }"
          ></span>
          {{ providerLabels[provider] || provider }}
          <span
            v-if="results[provider]?.riskScore"
            class="tab-risk"
            :style="{ color: riskLevelColor(results[provider].riskScore.totalScore) }"
          >
            {{ results[provider].riskScore.totalScore }}分
          </span>
        </button>
      </div>

      <div class="comparison-grid">
        <!-- Left: per-provider metrics -->
        <div class="comparison-metrics">
          <!-- Risk scores -->
          <div class="metric-group">
            <h5>风险评分对比</h5>
            <div
              v-for="provider in providers"
              :key="'risk-' + provider"
              class="metric-row"
            >
              <span class="metric-label">
                <span class="metric-dot" :style="{ background: providerColors[provider] || 'var(--primary)' }"></span>
                {{ providerLabels[provider] || provider }}
              </span>
              <div v-if="results[provider]?.riskScore" class="metric-bar-wrapper">
                <div class="metric-bar-track">
                  <div
                    class="metric-bar-fill"
                    :style="{
                      width: Math.min(100, (results[provider].riskScore.totalScore / (results[provider].riskScore.maxScore || 100)) * 100) + '%',
                      background: riskLevelColor(results[provider].riskScore.totalScore)
                    }"
                  ></div>
                </div>
                <span class="metric-val">{{ results[provider].riskScore.totalScore }}</span>
              </div>
              <span v-else class="metric-pending">等待中...</span>
            </div>
            <div v-if="riskDelta" class="risk-delta">
              评分差异: <strong>{{ riskDelta.delta }}</strong> 分
            </div>
          </div>

          <!-- Token usage -->
          <div class="metric-group">
            <h5>Token 用量对比</h5>
            <div
              v-for="provider in providers"
              :key="'token-' + provider"
              class="metric-row"
            >
              <span class="metric-label">
                <span class="metric-dot" :style="{ background: providerColors[provider] || 'var(--primary)' }"></span>
                {{ providerLabels[provider] || provider }}
              </span>
              <span v-if="results[provider]?.tokenUsage" class="metric-val">
                {{ (results[provider].tokenUsage.totalTokens / 1000).toFixed(1) }}k
              </span>
              <span v-else class="metric-pending">-</span>
            </div>
          </div>

          <!-- Confidence -->
          <div class="metric-group">
            <h5>置信度对比</h5>
            <div
              v-for="provider in providers"
              :key="'conf-' + provider"
              class="metric-row"
            >
              <span class="metric-label">
                <span class="metric-dot" :style="{ background: providerColors[provider] || 'var(--primary)' }"></span>
                {{ providerLabels[provider] || provider }}
              </span>
              <span v-if="results[provider]?.confidenceScores" class="metric-val" :class="{
                'conf-high': results[provider].confidenceScores.confidenceLevel === 'HIGH',
                'conf-mid': results[provider].confidenceScores.confidenceLevel === 'MEDIUM',
                'conf-low': results[provider].confidenceScores.confidenceLevel === 'LOW'
              }">
                {{ Math.round(results[provider].confidenceScores.overallConfidence) }}% {{ results[provider].confidenceScores.confidenceLevel === 'HIGH' ? '高' : results[provider].confidenceScores.confidenceLevel === 'MEDIUM' ? '中' : '低' }}
              </span>
              <span v-else class="metric-pending">-</span>
            </div>
          </div>
        </div>

        <!-- Right: selected provider's output -->
        <div class="comparison-output">
          <h5 class="output-title">
            <span class="tab-dot" :style="{ background: providerColors[activeProvider] || 'var(--primary)' }"></span>
            {{ providerLabels[activeProvider] || activeProvider }} 评审输出
            <span v-if="!results[activeProvider] && streams[activeProvider]" class="streaming-indicator">实时生成中...</span>
          </h5>
          <div v-if="activeHtml" class="output-body markdown-body" v-html="activeHtml"></div>
          <div v-else class="output-empty">
            <p>等待 {{ providerLabels[activeProvider] || activeProvider }} 评审结果...</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.comparison-panel {
  margin: 20px auto 0;
  max-width: 1600px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.comparison-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}

.comparison-header:hover {
  background: var(--bg-elevated);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
}

.header-title {
  font-size: 14px;
  font-weight: 600;
}

.streaming-badge {
  font-size: 10px;
  padding: 1px 8px;
  border-radius: 10px;
  background: rgba(99, 102, 241, 0.15);
  color: var(--primary);
  font-weight: 500;
}

.collapse-icon {
  color: var(--text-muted);
  transition: transform 0.2s;
}

.collapse-icon.collapsed {
  transform: rotate(-90deg);
}

.comparison-body {
  border-top: 1px solid var(--border);
}

.tab-bar {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--border);
  padding: 0 16px;
}

.tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  font-family: var(--font-sans);
}

.tab:hover {
  color: var(--text-primary);
}

.tab-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.tab-risk {
  font-size: 11px;
  font-weight: 600;
}

.comparison-grid {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 0;
  min-height: 300px;
}

.comparison-metrics {
  border-right: 1px solid var(--border);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.metric-group h5 {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin: 0 0 8px;
}

.metric-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.metric-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
  width: 80px;
  flex-shrink: 0;
}

.metric-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.metric-bar-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
}

.metric-bar-track {
  flex: 1;
  height: 4px;
  background: var(--bg-elevated);
  border-radius: 2px;
  overflow: hidden;
}

.metric-bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.6s ease;
}

.metric-val {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  width: 30px;
  text-align: right;
}

.metric-pending {
  font-size: 11px;
  color: var(--text-muted);
}

.conf-high { color: #22c55e !important; }
.conf-mid { color: #f59e0b !important; }
.conf-low { color: #ef4444 !important; }

.risk-delta {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px solid var(--border);
}

.risk-delta strong {
  color: var(--text-primary);
}

.comparison-output {
  padding: 16px 20px;
  overflow-y: auto;
  max-height: 600px;
}

.output-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.streaming-indicator {
  font-size: 11px;
  font-weight: 400;
  color: var(--primary);
  animation: blink 1.5s ease-in-out infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.output-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--text-muted);
  font-size: 13px;
}

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
</style>
