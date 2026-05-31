<script setup lang="ts">
import { computed } from 'vue'
import type { FileAnalysis, RiskLevel } from '@/types'

const props = defineProps<{ files: FileAnalysis[] }>()
const emit = defineEmits<{ select: [file: FileAnalysis] }>()

const riskConfig: Record<RiskLevel, { color: string; bg: string; order: number }> = {
  CRITICAL: { color: '#fca5a5', bg: 'rgba(239, 68, 68, 0.15)', order: 0 },
  HIGH: { color: '#fdba74', bg: 'rgba(249, 115, 22, 0.12)', order: 1 },
  MEDIUM: { color: '#fde047', bg: 'rgba(234, 179, 8, 0.1)', order: 2 },
  LOW: { color: '#86efac', bg: 'rgba(34, 197, 94, 0.08)', order: 3 }
}

const counts = computed(() => {
  const c: Record<RiskLevel, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
  props.files.forEach(f => { c[f.riskLevel]++ })
  return c
})

const levels: RiskLevel[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

const sortedFiles = computed(() =>
  [...props.files].sort((a, b) => riskConfig[a.riskLevel].order - riskConfig[b.riskLevel].order)
)
</script>

<template>
  <div class="risk-heatmap">
    <h3 class="heatmap-title">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/>
      </svg>
      文件风险热力图
    </h3>

    <div class="summary-pills">
      <div
        v-for="level in levels"
        :key="level"
        v-show="counts[level] > 0"
        class="pill"
        :style="{ background: riskConfig[level].bg, color: riskConfig[level].color, borderColor: riskConfig[level].color + '40' }"
      >
        {{ level }}: {{ counts[level] }}
      </div>
    </div>

    <div class="file-grid">
      <button
        v-for="file in sortedFiles"
        :key="file.filename"
        class="file-row"
        @click="emit('select', file)"
      >
        <div class="file-risk-dot" :style="{ background: riskConfig[file.riskLevel].color }"></div>
        <div class="file-info">
          <span class="file-name">{{ file.filename }}</span>
          <div class="file-meta">
            <span class="status-badge">{{ file.status }}</span>
            <span class="change-stats">
              <span class="add">+{{ file.additions }}</span>
              <span class="del">-{{ file.deletions }}</span>
            </span>
          </div>
        </div>
        <span class="file-risk-label" :style="{ color: riskConfig[file.riskLevel].color }">
          {{ file.riskLevel }}
        </span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.risk-heatmap {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 16px;
}

.heatmap-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.summary-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.pill {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
  border: 1px solid;
}

.file-grid {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 400px;
  overflow-y: auto;
}

.file-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  cursor: pointer;
  transition: background 0.15s ease;
  text-align: left;
  width: 100%;
}

.file-row:hover {
  background: var(--bg-elevated);
}

.file-risk-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 6px currentColor;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 12px;
  color: var(--text-primary);
  font-family: var(--font-mono, 'JetBrains Mono', monospace);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

.status-badge {
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--bg-elevated);
  color: var(--text-muted);
  text-transform: uppercase;
}

.change-stats {
  font-size: 10px;
  font-family: var(--font-mono, 'JetBrains Mono', monospace);
}

.add { color: #22c55e; }
.del { color: #ef4444; }

.file-risk-label {
  font-size: 10px;
  font-weight: 600;
  flex-shrink: 0;
}
</style>
