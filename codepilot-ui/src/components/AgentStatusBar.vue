<script setup lang="ts">
import type { TokenUsage, ChunkProgress } from '@/types'

defineProps<{
  activeAgents: string[]
  tokenUsage: TokenUsage | null
  status: string
  chunkProgress?: ChunkProgress | null
}>()

function formatTokens(n: number): string {
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}
</script>

<template>
  <div class="agent-bar">
    <div class="bar-left">
      <div
        v-for="agent in activeAgents"
        :key="agent"
        class="agent-badge"
      >
        <span class="pulse-dot"></span>
        {{ agent }}
      </div>
      <span v-if="activeAgents.length > 0" class="running-hint">
        <span class="dots-anim"><span>.</span><span>.</span><span>.</span></span>
      </span>
      <span v-if="activeAgents.length === 0 && status && status !== 'COMPLETED' && status !== 'FAILED'" class="running-hint">
        处理中<span class="dots-anim"><span>.</span><span>.</span><span>.</span></span>
      </span>
    </div>

    <div class="bar-right">
      <div v-if="chunkProgress && chunkProgress.total > 0" class="chunk-info">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
        </svg>
        <span>{{ chunkProgress.current }}/{{ chunkProgress.total }}</span>
        <span v-if="chunkProgress.fileName" class="chunk-file">{{ chunkProgress.fileName }}</span>
      </div>

      <div v-if="tokenUsage" class="token-info">
        <span title="Prompt tokens">{{ formatTokens(tokenUsage.promptTokens) }}</span>
        <span class="token-sep">+</span>
        <span title="Completion tokens">{{ formatTokens(tokenUsage.completionTokens) }}</span>
        <span class="token-sep">=</span>
        <span title="Total tokens">{{ formatTokens(tokenUsage.totalTokens) }}</span>
        <span class="token-label">tokens</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agent-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  min-height: 40px;
}

.bar-left, .bar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 12px;
  background: var(--bg-elevated);
  font-size: 11px;
  color: var(--text-secondary);
  border: 1px solid var(--border);
}

.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary);
  animation: pulse-dot 1.5s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.running-hint {
  font-size: 11px;
  color: var(--text-muted);
}

.dots-anim span {
  animation: dot-seq 1.4s ease-in-out infinite;
}
.dots-anim span:nth-child(2) { animation-delay: 0.2s; }
.dots-anim span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dot-seq {
  0%, 80%, 100% { opacity: 0.2; }
  40% { opacity: 1; }
}

.chunk-info {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--text-muted);
}

.chunk-info svg { opacity: 0.6; }

.chunk-file {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.token-info {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--text-muted);
  font-family: var(--font-mono, 'JetBrains Mono', monospace);
}

.token-sep { opacity: 0.5; }
.token-label { opacity: 0.6; margin-left: 2px; }
</style>
