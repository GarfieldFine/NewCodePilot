<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'

marked.use({ breaks: true, gfm: true })

const props = defineProps<{ content: string; loading: boolean }>()

function preprocessMarkdown(raw: string): string {
  // Remove repeated separators from chunk merging (3+ consecutive dashes)
  let text = raw.replace(/\n---\n/g, '\n\n')
  // Collapse 3+ consecutive newlines into 2
  text = text.replace(/\n{3,}/g, '\n\n')
  // Remove common AI preamble patterns
  text = text.replace(/^(好的|明白了|收到|OK)[，,].*?\n/gm, '')
  return text.trim()
}

const renderedHtml = computed(() => {
  if (!props.content) return ''
  try {
    return marked.parse(preprocessMarkdown(props.content)) as string
  } catch {
    return props.content.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }
})
</script>

<template>
  <div class="ai-review">
    <h3 class="section-title">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
      AI 评审结果
    </h3>

    <div v-if="loading && !content" class="ai-thinking">
      <div class="thinking-dots">
        <span></span><span></span><span></span>
      </div>
      <p>AI 正在分析代码变更...</p>
    </div>

    <div v-if="content" class="markdown-body" v-html="renderedHtml"></div>

    <div v-if="!loading && !content" class="empty">
      <p>等待 AI 评审结果...</p>
    </div>
  </div>
</template>

<style scoped>
.ai-review {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 24px;
  min-height: 300px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  color: var(--text-primary);
}

.section-title svg {
  color: var(--primary);
}

.ai-thinking {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--text-secondary);
  font-size: 14px;
  gap: 16px;
}

.thinking-dots {
  display: flex;
  gap: 6px;
}

.thinking-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
  animation: pulse 1.4s ease-in-out infinite;
}

.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes pulse {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1.2); }
}

.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--text-muted);
  font-size: 14px;
}

.markdown-body {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.markdown-body :deep(h3) {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 24px 0 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}

.markdown-body :deep(h3:first-of-type) {
  margin-top: 0;
}

.markdown-body :deep(h4) {
  font-size: 13px;
  font-weight: 600;
  margin: 16px 0 6px;
  color: var(--text-primary);
}

.markdown-body :deep(h5) {
  font-size: 12px;
  font-weight: 600;
  margin: 12px 0 4px;
  color: var(--text-secondary);
}

.markdown-body :deep(p) {
  margin: 8px 0;
}

.markdown-body :deep(strong) {
  color: var(--text-primary);
  font-weight: 600;
}

.markdown-body :deep(em) {
  font-style: italic;
}

.markdown-body :deep(a) {
  color: var(--primary);
  text-decoration: underline;
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
  padding: 12px 14px;
  overflow-x: auto;
  margin: 10px 0;
}

.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  color: var(--text-secondary);
}

.markdown-body :deep(li) {
  margin-bottom: 4px;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 18px;
  margin: 6px 0;
}

.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--primary);
  padding-left: 12px;
  margin: 12px 0;
  color: var(--text-muted);
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--border);
  padding: 6px 10px;
  text-align: left;
  font-size: 12px;
}

.markdown-body :deep(th) {
  background: var(--bg-elevated);
  font-weight: 600;
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 16px 0;
}
</style>
