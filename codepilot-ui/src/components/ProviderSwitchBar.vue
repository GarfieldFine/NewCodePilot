<script setup lang="ts">
import { computed } from 'vue'
import { useAnalysisStore } from '@/stores/analysis'

const store = useAnalysisStore()

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

const hasCompleted = computed(() =>
  store.selectedProviders.some(p => store.providerResults[p])
)
</script>

<template>
  <div class="switch-bar">
    <span class="switch-label">评审模型</span>
    <button
      v-for="provider in store.selectedProviders"
      :key="provider"
      class="switch-tab"
      :class="{ active: provider === store.displayProvider }"
      :style="provider === store.displayProvider ? {
        borderBottomColor: providerColors[provider] || 'var(--primary)',
        color: 'var(--text-primary)'
      } : {}"
      @click="store.switchProvider(provider)"
    >
      <span
        class="switch-dot"
        :style="{ background: providerColors[provider] || 'var(--primary)' }"
      ></span>
      {{ providerLabels[provider] || provider }}
      <span
        v-if="store.providerResults[provider]?.riskScore"
        class="switch-risk"
        :style="{ color: riskColor(store.providerResults[provider].riskScore.totalScore) }"
      >
        {{ store.providerResults[provider].riskScore.totalScore }}分
      </span>
      <span
        v-else-if="store.providerStreams[provider] && !store.providerResults[provider]"
        class="switch-streaming"
      >
        <span class="stream-dot-pulse"></span>
        评审中
      </span>
    </button>
    <router-link
      v-if="hasCompleted"
      to="/comparison"
      class="compare-link"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>
      </svg>
      对比分析
    </router-link>
  </div>
</template>

<style scoped>
.switch-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 20px;
  max-width: 1600px;
  margin: 0 auto;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border);
}

.switch-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-right: 12px;
}

.switch-tab {
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

.switch-tab:hover {
  color: var(--text-primary);
}

.switch-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.switch-risk {
  font-size: 11px;
  font-weight: 600;
}

.switch-streaming {
  font-size: 11px;
  color: var(--primary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.stream-dot-pulse {
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

.compare-link {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--primary);
  text-decoration: none;
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  border: 1px solid rgba(99, 102, 241, 0.25);
  transition: all 0.15s;
  font-weight: 500;
}

.compare-link:hover {
  background: rgba(99, 102, 241, 0.1);
}
</style>
