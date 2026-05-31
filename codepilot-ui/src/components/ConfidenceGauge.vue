<script setup lang="ts">
import { computed } from 'vue'
import type { ConfidenceScores } from '@/types'

const props = defineProps<{ confidence: ConfidenceScores | null }>()

const level = computed(() => props.confidence?.confidenceLevel || null)
const overall = computed(() => (props.confidence?.overallConfidence ?? 0) / 100)
const factors = computed(() => props.confidence?.factors || {})
const summary = computed(() => props.confidence?.summary || '')

const circumference = 2 * Math.PI * 52

const dashOffset = computed(() =>
  circumference * (1 - overall.value)
)

const levelColor = computed(() => {
  if (level.value === 'HIGH') return '#22c55e'
  if (level.value === 'MEDIUM') return '#f59e0b'
  return '#ef4444'
})
</script>

<template>
  <div v-if="confidence" class="confidence-gauge">
    <h3 class="gauge-title">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
      </svg>
      AI 置信度评估
    </h3>

    <div class="gauge-body">
      <div class="ring-container">
        <svg class="ring" width="120" height="120" viewBox="0 0 120 120">
          <circle cx="60" cy="60" r="52" fill="none" stroke="var(--bg-elevated)" stroke-width="8"/>
          <circle
            cx="60" cy="60" r="52"
            fill="none"
            :stroke="levelColor"
            stroke-width="8"
            stroke-linecap="round"
            :stroke-dasharray="circumference"
            :stroke-dashoffset="dashOffset"
            transform="rotate(-90 60 60)"
            class="progress-ring"
          />
          <text x="60" y="56" text-anchor="middle" class="ring-value">
            {{ Math.round(overall * 100) }}%
          </text>
          <text x="60" y="72" text-anchor="middle" class="ring-label">
            {{ level }}
          </text>
        </svg>
      </div>

      <div v-if="Object.keys(factors).length > 0" class="factor-bars">
        <div
          v-for="(value, key) in factors"
          :key="key"
          class="factor-row"
        >
          <span class="factor-name">{{ key }}</span>
          <div class="factor-track">
            <div
              class="factor-fill"
              :class="{
                low: value < 0.4,
                mid: value >= 0.4 && value < 0.7,
                high: value >= 0.7
              }"
              :style="{ width: (value * 100) + '%' }"
            ></div>
          </div>
          <span class="factor-val">{{ Math.round(value * 100) }}%</span>
        </div>
      </div>
    </div>

    <p v-if="summary" class="gauge-summary">{{ summary }}</p>
  </div>
</template>

<style scoped>
.confidence-gauge {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 16px;
}

.gauge-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.gauge-body {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.ring-container {
  flex-shrink: 0;
}

.progress-ring {
  transition: stroke-dashoffset 1s ease;
}

.ring-value {
  font-size: 20px;
  font-weight: 700;
  fill: var(--text-primary);
}

.ring-label {
  font-size: 10px;
  font-weight: 600;
  fill: var(--text-secondary);
}

.factor-bars {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.factor-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.factor-name {
  font-size: 10px;
  color: var(--text-muted);
  width: 80px;
  flex-shrink: 0;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
}

.factor-track {
  flex: 1;
  height: 6px;
  background: var(--bg-elevated);
  border-radius: 3px;
  overflow: hidden;
}

.factor-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.8s ease;
}

.factor-fill.low { background: #ef4444; }
.factor-fill.mid { background: #f59e0b; }
.factor-fill.high { background: #22c55e; }

.factor-val {
  font-size: 10px;
  color: var(--text-muted);
  width: 30px;
  text-align: right;
  font-family: var(--font-mono, 'JetBrains Mono', monospace);
}

.gauge-summary {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 10px;
  line-height: 1.5;
}
</style>
