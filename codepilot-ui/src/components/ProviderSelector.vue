<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { getAvailableProviders } from '@/api'

const props = defineProps<{ modelValue: string[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()

const availableProviders = ref<string[]>([])
const loading = ref(true)
const error = ref('')

const providerLabels: Record<string, string> = {
  openai: 'OpenAI',
  deepseek: 'DeepSeek',
  claude: 'Claude',
  qwen: 'Qwen',
  siliconflow: '硅基流动'
}

const providerColors: Record<string, string> = {
  openai: '#10a37f',
  deepseek: '#6366f1',
  claude: '#d97706',
  qwen: '#2563eb',
  siliconflow: '#8b5cf6'
}

onMounted(async () => {
  try {
    availableProviders.value = await getAvailableProviders()
    if (props.modelValue.length === 0 && availableProviders.value.length > 0) {
      emit('update:modelValue', [availableProviders.value[0]])
    }
  } catch (e: any) {
    error.value = e.message || 'Failed to load providers'
  } finally {
    loading.value = false
  }
})

function toggle(provider: string) {
  const selected = [...props.modelValue]
  const idx = selected.indexOf(provider)
  if (idx >= 0) {
    if (selected.length > 1) {
      selected.splice(idx, 1)
    }
  } else {
    selected.push(provider)
  }
  emit('update:modelValue', selected)
}

function isSelected(provider: string): boolean {
  return props.modelValue.includes(provider)
}
</script>

<template>
  <div class="provider-selector">
    <div class="selector-label">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/>
      </svg>
      AI 模型选择
      <span v-if="props.modelValue.length > 1" class="comparison-badge">对比模式</span>
    </div>

    <div v-if="loading" class="selector-loading">
      <span class="loading-pulse"></span>
      加载可用模型...
    </div>

    <div v-else-if="error" class="selector-error">{{ error }}</div>

    <div v-else class="selector-chips">
      <button
        v-for="provider in availableProviders"
        :key="provider"
        class="provider-chip"
        :class="{ selected: isSelected(provider) }"
        :style="isSelected(provider) ? {
          borderColor: providerColors[provider] || 'var(--primary)',
          background: (providerColors[provider] || 'var(--primary)') + '18'
        } : {}"
        @click="toggle(provider)"
      >
        <span
          class="chip-dot"
          :style="{ background: isSelected(provider) ? (providerColors[provider] || 'var(--primary)') : 'var(--border-light)' }"
        ></span>
        <span class="chip-name">{{ providerLabels[provider] || provider }}</span>
        <span v-if="isSelected(provider)" class="chip-check">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
        </span>
      </button>
    </div>

    <p v-if="props.modelValue.length < 2 && availableProviders.length > 1" class="selector-hint">
      选择 2 个以上模型可启用 AI 对比评审
    </p>
  </div>
</template>

<style scoped>
.provider-selector {
  margin-top: 16px;
  padding: 14px 18px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

.selector-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 10px;
}

.selector-label svg {
  color: var(--primary);
}

.comparison-badge {
  font-size: 10px;
  padding: 1px 8px;
  border-radius: 10px;
  background: rgba(99, 102, 241, 0.15);
  color: var(--primary);
  font-weight: 500;
}

.selector-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

.loading-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
  animation: pulse-dot 1.5s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.selector-error {
  font-size: 12px;
  color: var(--danger);
}

.selector-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.provider-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--bg-elevated);
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
  font-family: var(--font-sans);
}

.provider-chip:hover {
  border-color: var(--border-light);
  color: var(--text-primary);
}

.provider-chip.selected {
  color: var(--text-primary);
  font-weight: 500;
}

.chip-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  transition: background 0.2s;
}

.chip-name {
  line-height: 1;
}

.chip-check {
  display: flex;
  align-items: center;
  color: inherit;
}

.selector-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 10px;
  margin-bottom: 0;
}
</style>
