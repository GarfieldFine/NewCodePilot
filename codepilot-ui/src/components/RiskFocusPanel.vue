<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import type { FileAnalysis, RiskFinding, RiskLevel, RuleResult } from '@/types'

const props = defineProps<{ file: FileAnalysis; allFiles: FileAnalysis[] }>()
const emit = defineEmits<{ close: []; selectFile: [file: FileAnalysis] }>()

const riskConfig: Record<RiskLevel, { color: string; bg: string }> = {
  CRITICAL: { color: '#fca5a5', bg: 'rgba(239, 68, 68, 0.12)' },
  HIGH: { color: '#fdba74', bg: 'rgba(249, 115, 22, 0.1)' },
  MEDIUM: { color: '#fde047', bg: 'rgba(234, 179, 8, 0.08)' },
  LOW: { color: '#86efac', bg: 'rgba(34, 197, 94, 0.06)' }
}

const currentIdx = computed(() =>
  props.allFiles.findIndex(f => f.filename === props.file.filename)
)

const aiFindings = computed(() => props.file.aiFindings || [])
const ruleFindings = computed(() => props.file.findings || [])

type FindingItem = (RuleResult & { _type: 'rule' }) | (RiskFinding & { _type: 'ai' })
const allFindings = computed<FindingItem[]>(() => [
  ...ruleFindings.value.map(f => ({ ...f, _type: 'rule' as const })),
  ...aiFindings.value.map(f => ({ ...f, _type: 'ai' as const }))
])

function findingRiskLevel(f: FindingItem): RiskLevel {
  return f._type === 'rule' ? f.riskLevel : f.level
}
function findingMessage(f: FindingItem): string {
  return f._type === 'rule' ? f.message : f.description
}
function findingRuleName(f: FindingItem): string {
  return f._type === 'rule' ? f.ruleName : f.category
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    e.preventDefault()
    const delta = e.key === 'ArrowDown' ? 1 : -1
    const next = (currentIdx.value + delta + props.allFiles.length) % props.allFiles.length
    emit('selectFile', props.allFiles[next])
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="overlay" @click.self="emit('close')">
    <div class="panel">
      <div class="panel-sidebar">
        <h4 class="sidebar-title">Files</h4>
        <div class="sidebar-list">
          <button
            v-for="f in allFiles"
            :key="f.filename"
            class="sidebar-file"
            :class="{ active: f.filename === file.filename }"
            @click="emit('selectFile', f)"
          >
            <span class="sidebar-dot" :style="{ background: riskConfig[f.riskLevel].color }"></span>
            <span class="sidebar-name">{{ f.filename.split('/').pop() }}</span>
          </button>
        </div>
      </div>

      <div class="panel-content">
        <div class="panel-header">
          <div>
            <h2 class="file-title">{{ file.filename }}</h2>
            <div class="file-stats">
              <span class="status-badge">{{ file.status }}</span>
              <span class="add">+{{ file.additions }}</span>
              <span class="del">-{{ file.deletions }}</span>
            </div>
          </div>
          <button class="close-btn" @click="emit('close')">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>

        <div v-if="allFindings.length === 0" class="empty">
          No issues found in this file.
        </div>

        <div v-else class="findings-list">
          <div
            v-for="(finding, idx) in allFindings"
            :key="idx"
            class="finding-card"
          >
            <div class="finding-header">
              <span class="level-badge" :style="{ color: riskConfig[findingRiskLevel(finding)].color, background: riskConfig[findingRiskLevel(finding)].bg }">
                {{ findingRiskLevel(finding) }}
              </span>
              <span class="rule-name">{{ findingRuleName(finding) }}</span>
              <span class="finding-category">{{ finding.category }}</span>
            </div>
            <p class="finding-msg">{{ findingMessage(finding) }}</p>
            <p v-if="finding.suggestion" class="finding-suggestion">
              <strong>建议:</strong> {{ finding.suggestion }}
            </p>

            <div v-if="finding._type === 'ai' && (finding.why || finding.evidence || finding.impact)" class="finding-tabs">
              <details v-if="finding.why">
                <summary>Why</summary>
                <p>{{ finding.why }}</p>
              </details>
              <details v-if="finding.impact">
                <summary>Impact</summary>
                <p>{{ finding.impact }}</p>
              </details>
              <details v-if="finding.evidence">
                <summary>Evidence</summary>
                <pre>{{ finding.evidence }}</pre>
              </details>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fade-in 0.2s ease;
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.panel {
  display: flex;
  width: 85vw;
  max-width: 1100px;
  height: 80vh;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg, 12px);
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
  animation: slide-up 0.25s ease;
}

@keyframes slide-up {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.panel-sidebar {
  width: 220px;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  padding: 14px 16px 8px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px;
}

.sidebar-file {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  border: none;
  background: transparent;
  cursor: pointer;
  width: 100%;
  text-align: left;
  font-size: 11px;
  color: var(--text-secondary);
  transition: background 0.15s;
}

.sidebar-file:hover { background: var(--bg-elevated); }
.sidebar-file.active { background: rgba(99, 102, 241, 0.1); color: var(--text-primary); }

.sidebar-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.sidebar-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}

.file-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  font-family: var(--font-mono, 'JetBrains Mono', monospace);
  word-break: break-all;
}

.file-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.status-badge {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--bg-elevated);
  color: var(--text-muted);
  text-transform: uppercase;
}

.add { color: #22c55e; font-size: 12px; font-family: var(--font-mono, 'JetBrains Mono', monospace); }
.del { color: #ef4444; font-size: 12px; font-family: var(--font-mono, 'JetBrains Mono', monospace); }

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}

.close-btn:hover { background: var(--bg-elevated); color: var(--text-primary); }

.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--text-muted);
  font-size: 13px;
}

.findings-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.finding-card {
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 14px;
}

.finding-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.level-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
}

.rule-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.finding-category {
  font-size: 10px;
  color: var(--text-muted);
  background: var(--bg-card);
  padding: 1px 6px;
  border-radius: 4px;
}

.finding-msg { font-size: 13px; color: var(--text-secondary); margin: 6px 0; line-height: 1.5; }
.finding-suggestion { font-size: 12px; color: var(--text-muted); }

.finding-suggestion strong { color: var(--text-secondary); }

.finding-tabs {
  margin-top: 10px;
  border-top: 1px solid var(--border);
  padding-top: 8px;
}

.finding-tabs details {
  margin-bottom: 4px;
}

.finding-tabs summary {
  font-size: 11px;
  font-weight: 600;
  color: var(--primary);
  cursor: pointer;
  padding: 2px 0;
}

.finding-tabs p, .finding-tabs pre {
  font-size: 12px;
  color: var(--text-muted);
  margin: 4px 0 4px 8px;
  line-height: 1.5;
}

.finding-tabs pre {
  background: var(--bg-card);
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
  white-space: pre-wrap;
}
</style>
