<script setup lang="ts">
import { computed } from 'vue'
import type { TokenUsage, ChunkProgress } from '@/types'

const props = defineProps<{
  activeAgents: string[]
  tokenUsage: TokenUsage | null
  status: string
  chunkProgress?: ChunkProgress | null
}>()

const agentCount = computed(() => props.activeAgents.length)

const tokenDisplay = computed(() => {
  if (!props.tokenUsage || props.tokenUsage.totalTokens === 0) return null
  const k = Math.round(props.tokenUsage.totalTokens / 1000)
  return `${k}K tokens`
})

const chunkDisplay = computed(() => {
  if (!props.tokenUsage) return null
  const t = props.tokenUsage
  if (t.totalChunks === 0) return null
  return `${t.successfulChunks}/${t.totalChunks} chunks`
})

const isRunning = computed(() =>
  props.status && props.status !== 'COMPLETED' && props.status !== 'FAILED'
)
</script>

<template>
  <div class="agent-status-bar" v-if="isRunning || tokenUsage">
    <!-- Live agent indicators -->
    <div class="status-section" v-if="isRunning && activeAgents.length > 0">
      <div class="agent-badges">
        <span
          v-for="name in activeAgents"
          :key="name"
          class="agent-badge"
        >
          <span class="badge-dot"></span>
          {{ name.replace('Agent', '') }}
        </span>
      </div>
    </div>

    <!-- Chunk progress -->
    <div class="chunk-section" v-if="chunkProgress && chunkProgress.total > 0">
      <div class="chunk-info">
        <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>
        </svg>
        <span class="chunk-label">Analyzing</span>
        <span class="chunk-counter">{{ chunkProgress.current }}/{{ chunkProgress.total }}</span>
        <span class="chunk-filename" v-if="chunkProgress.fileName">{{ chunkProgress.fileName }}</span>
      </div>
      <div class="chunk-bar">
        <div class="chunk-fill" :style="{ width: (chunkProgress.current / chunkProgress.total * 100) + '%' }"></div>
      </div>
    </div>

    <!-- Token & chunk stats -->
    <div class="stats-section" v-if="tokenUsage">
      <div class="stat" v-if="tokenDisplay" :title="`Prompt: ${tokenUsage.promptTokens} | Completion: ${tokenUsage.completionTokens}`">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
        </svg>
        {{ tokenDisplay }}
      </div>
      <div class="stat" v-if="chunkDisplay">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
          <rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/>
        </svg>
        {{ chunkDisplay }}
      </div>
    </div>

    <!-- Running indicator -->
    <div class="running-indicator" v-if="isRunning">
      <span class="running-text">Agent {{ agentCount }} 运行中</span>
      <span class="running-dots">
        <span></span><span></span><span></span>
      </span>
    </div>
  </div>
</template>

<style scoped>
.agent-status-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 16px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 12px;
  flex-wrap: wrap;
}

.status-section {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-badges {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.agent-badge {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  background: rgba(99, 102, 241, 0.1);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 12px;
  font-size: 11px;
  color: var(--primary);
  font-weight: 500;
}

.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary);
  animation: pulse-dot 1.2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.stats-section {
  display: flex;
  gap: 14px;
  margin-left: auto;
}

.stat {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.stat svg {
  opacity: 0.5;
}

.chunk-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 180px;
}

.chunk-info {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--text-muted);
}

.chunk-info svg {
  opacity: 0.5;
  flex-shrink: 0;
}

.chunk-label {
  white-space: nowrap;
}

.chunk-counter {
  font-family: var(--font-mono);
  font-weight: 600;
  color: var(--primary);
}

.chunk-filename {
  font-family: var(--font-mono);
  font-size: 10px;
  opacity: 0.7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 120px;
}

.chunk-bar {
  width: 100%;
  height: 3px;
  background: var(--bg-elevated);
  border-radius: 2px;
  overflow: hidden;
}

.chunk-fill {
  height: 100%;
  background: var(--gradient-hero);
  border-radius: 2px;
  transition: width 0.3s ease;
}

.running-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 11px;
}

.running-text {
  white-space: nowrap;
}

.running-dots {
  display: flex;
  gap: 3px;
}

.running-dots span {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: dots-fade 1.4s ease-in-out infinite;
}

.running-dots span:nth-child(2) { animation-delay: 0.2s; }
.running-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dots-fade {
  0%, 80%, 100% { opacity: 0.2; }
  40% { opacity: 0.8; }
}
</style>
