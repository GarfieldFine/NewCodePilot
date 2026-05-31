<script setup lang="ts">
import { computed } from 'vue'
import type { FileAnalysis, RiskFinding, RiskLevel } from '@/types'

const props = defineProps<{
  file: FileAnalysis
  allFiles: FileAnalysis[]
}>()

const emit = defineEmits<{
  close: []
  selectFile: [file: FileAnalysis]
}>()

const activeTab = computed(() => 'why')

const riskColor: Record<RiskLevel, string> = {
  CRITICAL: 'var(--risk-critical)',
  HIGH: 'var(--risk-high)',
  MEDIUM: 'var(--risk-medium)',
  LOW: 'var(--risk-low)'
}

const riskBg: Record<RiskLevel, string> = {
  CRITICAL: 'rgba(239, 68, 68, 0.12)',
  HIGH: 'rgba(249, 115, 22, 0.1)',
  MEDIUM: 'rgba(251, 191, 36, 0.08)',
  LOW: 'rgba(52, 211, 153, 0.06)'
}

// Combine rule findings + AI findings
const allFindings = computed(() => {
  const items: Array<{
    level: RiskLevel
    description: string
    suggestion: string
    why?: string
    evidence?: string
    impact?: string
    source: 'rule' | 'ai'
  }> = []

  for (const f of props.file.findings) {
    items.push({
      level: f.riskLevel,
      description: f.message,
      suggestion: f.suggestion,
      source: 'rule'
    })
  }

  if (props.file.aiFindings) {
    for (const f of props.file.aiFindings) {
      items.push({
        level: f.level,
        description: f.description,
        suggestion: f.suggestion,
        why: f.why,
        evidence: f.evidence,
        impact: f.impact,
        source: 'ai'
      })
    }
  }

  return items
})

// Sort files by risk for sidebar mini-map
const sortedFiles = computed(() => {
  const order: Record<RiskLevel, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 }
  return [...props.allFiles].sort((a, b) => order[a.riskLevel] - order[b.riskLevel])
})

const currentIndex = computed(() => {
  return sortedFiles.value.findIndex(f => f.filename === props.file.filename)
})

function selectPrev() {
  const idx = currentIndex.value
  if (idx > 0) emit('selectFile', sortedFiles.value[idx - 1])
}

function selectNext() {
  const idx = currentIndex.value
  if (idx < sortedFiles.value.length - 1) emit('selectFile', sortedFiles.value[idx + 1])
}

function getStatusLabel(status: string): string {
  const map: Record<string, string> = { added: 'A', modified: 'M', removed: 'D', renamed: 'R' }
  return map[status] || status
}
</script>

<template>
  <div class="focus-overlay" @click.self="emit('close')">
    <!-- Sidebar mini-map -->
    <div class="focus-sidebar">
      <div class="sidebar-label">Files</div>
      <div
        v-for="f in sortedFiles"
        :key="f.filename"
        class="sidebar-dot"
        :class="{ active: f.filename === file.filename }"
        :style="{ '--dot-color': riskColor[f.riskLevel] }"
        :title="`${f.filename} — ${f.riskLevel}`"
        @click="emit('selectFile', f)"
      >
        <span class="dot-core"></span>
        <span class="dot-ring" v-if="f.filename === file.filename"></span>
      </div>
    </div>

    <!-- Focus panel -->
    <div class="focus-panel">
      <!-- Header -->
      <div class="focus-header">
        <button class="close-btn" @click="emit('close')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
        <div class="header-main">
          <span class="file-status-badge" :style="{ color: getStatusLabel(file.status) === 'A' ? 'var(--success)' : getStatusLabel(file.status) === 'D' ? 'var(--danger)' : 'var(--warning)' }">
            {{ getStatusLabel(file.status) }}
          </span>
          <span class="file-name">{{ file.filename }}</span>
          <span class="file-lang">{{ file.language }}</span>
        </div>
        <div class="header-stats">
          <span class="risk-pill" :style="{ background: riskBg[file.riskLevel], color: riskColor[file.riskLevel] }">
            {{ file.riskLevel }}
          </span>
          <span class="stat-add">+{{ file.additions }}</span>
          <span class="stat-del">-{{ file.deletions }}</span>
        </div>
      </div>

      <!-- Nav arrows -->
      <div class="focus-nav">
        <button class="nav-btn prev" @click="selectPrev" :disabled="currentIndex <= 0">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
          Previous
        </button>
        <span class="nav-pos">{{ currentIndex + 1 }} / {{ sortedFiles.length }}</span>
        <button class="nav-btn next" @click="selectNext" :disabled="currentIndex >= sortedFiles.length - 1">
          Next
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
        </button>
      </div>

      <!-- AI Explanation panels -->
      <div class="focus-body" v-if="allFindings.length > 0">
        <div v-for="(finding, i) in allFindings" :key="i" class="finding-card">
          <div class="finding-header">
            <span class="finding-level" :style="{ color: riskColor[finding.level] }">{{ finding.level }}</span>
            <span class="finding-source">{{ finding.source === 'ai' ? 'AI' : 'Rule Engine' }}</span>
          </div>
          <p class="finding-desc">{{ finding.description }}</p>

          <!-- Explainability sections -->
          <div class="explain-grid" v-if="finding.why || finding.impact">
            <div class="explain-block why" v-if="finding.why">
              <h4>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
                Why this is {{ finding.level }} risk
              </h4>
              <p>{{ finding.why }}</p>
            </div>
            <div class="explain-block impact" v-if="finding.impact">
              <h4>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
                Impact
              </h4>
              <p>{{ finding.impact }}</p>
            </div>
          </div>

          <!-- Evidence snippet -->
          <div class="explain-block evidence" v-if="finding.evidence">
            <h4>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
              Evidence
            </h4>
            <pre><code>{{ finding.evidence }}</code></pre>
          </div>

          <!-- Fix suggestion -->
          <div class="explain-block fix" v-if="finding.suggestion">
            <h4>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
              How to fix
            </h4>
            <p>{{ finding.suggestion }}</p>
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div v-else class="focus-empty">
        <p>No detailed findings available for this file.</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.focus-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(4px);
  animation: overlay-in 0.25s ease;
}

@keyframes overlay-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.focus-sidebar {
  width: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 16px 6px;
  overflow-y: auto;
}

.sidebar-label {
  font-size: 9px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 8px;
  writing-mode: vertical-rl;
}

.sidebar-dot {
  position: relative;
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.2s ease;
}

.sidebar-dot:hover { transform: scale(1.3); }

.sidebar-dot.active { transform: scale(1.4); }

.dot-core {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--dot-color);
  transition: all 0.2s ease;
}

.sidebar-dot.active .dot-core {
  width: 10px;
  height: 10px;
  box-shadow: 0 0 8px var(--dot-color);
}

.dot-ring {
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 2px solid var(--dot-color);
  animation: ring-pulse 2s ease-in-out infinite;
}

@keyframes ring-pulse {
  0%, 100% { opacity: 0.4; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.2); }
}

.focus-panel {
  flex: 1;
  background: var(--bg-primary);
  border-left: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  animation: panel-slide 0.3s ease;
  max-width: 800px;
  margin-left: auto;
}

@keyframes panel-slide {
  from { transform: translateX(40px); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}

.focus-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid var(--border);
  flex-wrap: wrap;
  gap: 10px;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.close-btn:hover {
  border-color: var(--danger);
  color: var(--danger);
}

.header-main {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.file-status-badge {
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
  flex-shrink: 0;
}

.file-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  font-family: var(--font-mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-lang {
  font-size: 11px;
  color: var(--text-muted);
  background: var(--bg-elevated);
  padding: 2px 8px;
  border-radius: 100px;
  flex-shrink: 0;
}

.header-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.risk-pill {
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 100px;
  font-family: var(--font-mono);
}

.stat-add { color: var(--success); font-size: 13px; font-family: var(--font-mono); }
.stat-del { color: var(--danger); font-size: 13px; font-family: var(--font-mono); }

.focus-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 24px;
  border-bottom: 1px solid var(--border);
}

.nav-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  color: var(--text-secondary);
  font-size: 12px;
  padding: 5px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
  font-family: var(--font-sans);
}

.nav-btn:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
}

.nav-btn:disabled {
  opacity: 0.3;
  cursor: default;
}

.nav-pos {
  font-size: 12px;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.focus-body {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.finding-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 20px;
}

.finding-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.finding-level {
  font-size: 11px;
  font-weight: 700;
  font-family: var(--font-mono);
}

.finding-source {
  font-size: 10px;
  color: var(--text-muted);
  background: var(--bg-elevated);
  padding: 2px 6px;
  border-radius: 4px;
}

.finding-desc {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.6;
  margin-bottom: 16px;
}

.explain-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.explain-block {
  background: var(--bg-elevated);
  border-radius: var(--radius-sm);
  padding: 14px;
}

.explain-block h4 {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.explain-block h4 svg { opacity: 0.6; flex-shrink: 0; }

.explain-block p {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.6;
}

.explain-block.why { border-left: 3px solid var(--warning); }
.explain-block.impact { border-left: 3px solid var(--danger); }
.explain-block.evidence {
  border-left: 3px solid var(--info);
  margin-bottom: 12px;
}
.explain-block.fix { border-left: 3px solid var(--success); }

.explain-block pre {
  margin: 0;
  padding: 10px;
  background: var(--bg-primary);
  border-radius: var(--radius-sm);
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.5;
  font-family: var(--font-mono);
}

.explain-block code {
  color: var(--text-primary);
  font-family: var(--font-mono);
}

.focus-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  color: var(--text-muted);
  font-size: 14px;
}

@media (max-width: 600px) {
  .explain-grid {
    grid-template-columns: 1fr;
  }
  .focus-sidebar { display: none; }
  .focus-panel { max-width: 100%; }
}
</style>
