<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'

const props = defineProps<{ content: string; loading: boolean }>()

marked.use({
  renderer: {
    code(code: string, infostring: string | undefined, _escaped: boolean) {
      const language = infostring || ''
      const highlighted = language && hljs.getLanguage(language)
        ? hljs.highlight(code, { language }).value
        : hljs.highlightAuto(code).value
      return `<pre><code class="hljs language-${language}">${highlighted}</code></pre>\n`
    }
  }
})

const renderedHtml = computed(() => {
  if (!props.content) return ''
  return marked.parse(props.content) as string
})
</script>

<template>
  <div class="markdown-renderer">
    <div v-if="loading && !content" class="loading-state">
      <span class="loading-dot"></span>
      <span class="loading-dot"></span>
      <span class="loading-dot"></span>
      <span class="loading-text">AI is analyzing code changes...</span>
    </div>
    <div v-else-if="content" class="markdown-body" v-html="renderedHtml"></div>
    <div v-else class="empty-state">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
        <polyline points="14 2 14 8 20 8"/>
        <line x1="16" y1="13" x2="8" y2="13"/>
        <line x1="16" y1="17" x2="8" y2="17"/>
        <polyline points="10 9 9 9 8 9"/>
      </svg>
      <span>Waiting for AI review results...</span>
    </div>
  </div>
</template>

<style scoped>
.markdown-renderer { min-height: 200px; }

.loading-state {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 20px 0;
}

.loading-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary);
  animation: dot-bounce 1.4s ease-in-out infinite both;
}
.loading-dot:nth-child(2) { animation-delay: 0.16s; }
.loading-dot:nth-child(3) { animation-delay: 0.32s; }

@keyframes dot-bounce {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1.2); }
}

.loading-text {
  font-size: 13px;
  color: var(--text-muted);
  margin-left: 8px;
}

.empty-state {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 13px;
  padding: 20px 0;
}
.empty-state svg { opacity: 0.5; }

.markdown-body {
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.7;
}

.markdown-body :deep(h3) {
  font-size: 15px;
  font-weight: 600;
  margin: 20px 0 10px;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border);
  padding-bottom: 8px;
}

.markdown-body :deep(h4) {
  font-size: 13px;
  font-weight: 600;
  margin: 16px 0 6px;
  color: var(--text-secondary);
}

.markdown-body :deep(p) { margin: 8px 0; }

.markdown-body :deep(ul), .markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.markdown-body :deep(li) { margin: 4px 0; }

.markdown-body :deep(code) {
  font-family: var(--font-mono, 'JetBrains Mono', monospace);
  font-size: 12px;
}

.markdown-body :deep(pre) {
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 14px;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
}

.markdown-body :deep(:not(pre) > code) {
  background: var(--bg-elevated);
  padding: 2px 6px;
  border-radius: 3px;
}

.markdown-body :deep(strong) { font-weight: 600; color: var(--text-primary); }

.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--primary);
  padding-left: 12px;
  margin: 12px 0;
  color: var(--text-secondary);
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.markdown-body :deep(th), .markdown-body :deep(td) {
  border: 1px solid var(--border);
  padding: 8px 12px;
  text-align: left;
  font-size: 13px;
}

.markdown-body :deep(th) {
  background: var(--bg-elevated);
  font-weight: 600;
}
</style>
