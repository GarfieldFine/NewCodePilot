<script setup lang="ts">
import { computed } from 'vue'
import type { AnalysisStatus } from '@/types'

interface AgentStep {
  agentName: string
  message: string
  time: number
  type: string
}

const props = defineProps<{
  agentEvents: AgentStep[]
  activeAgents: string[]
  currentStage: string
  status: AnalysisStatus | ''
}>()

const AGENT_LABELS: Record<string, string> = {
  RepositoryAnalyzeAgent: '仓库结构分析',
  DiffAnalyzeAgent: '差异变更分析',
  ContextBuildAgent: '代码上下文构建',
  RiskDetectAgent: '风险模式检测',
  ReviewGenerateAgent: 'AI 智能评审',
  SummaryMergeAgent: '结果汇总合并'
}

const STAGE_LABELS: Record<string, string> = {
  'Understanding Repository': '理解仓库结构',
  'Detecting Risks': '检测代码风险',
  'Generating AI Review': 'AI 生成评审',
  'Merging Analysis': '汇总分析结果'
}

const agentStartEvents = computed(() =>
  props.agentEvents.filter(e => e.type === 'AGENT_STARTED')
)

const agentCompleteEvents = computed(() =>
  props.agentEvents.filter(e => e.type === 'AGENT_COMPLETED')
)

const agentFailedEvents = computed(() =>
  props.agentEvents.filter(e => e.type === 'AGENT_FAILED')
)

const pipelineStages = computed(() =>
  props.agentEvents.filter(e => e.type === 'PIPELINE_STAGE')
)

function agentLabel(name: string) {
  return AGENT_LABELS[name] || name
}

function stageLabel(name: string) {
  return STAGE_LABELS[name] || name
}

function agentState(name: string): 'running' | 'done' | 'failed' | 'waiting' {
  if (agentFailedEvents.value.some(e => e.agentName === name)) return 'failed'
  if (agentCompleteEvents.value.some(e => e.agentName === name)) return 'done'
  if (props.activeAgents.includes(name)) return 'running'
  return 'waiting'
}

const allAgentNames = [
  'RepositoryAnalyzeAgent', 'DiffAnalyzeAgent', 'ContextBuildAgent',
  'RiskDetectAgent', 'ReviewGenerateAgent', 'SummaryMergeAgent'
]

const agentList = computed(() =>
  allAgentNames.map(name => ({
    name,
    label: agentLabel(name),
    state: agentState(name)
  }))
)
</script>

<template>
  <div class="ai-timeline">
    <h3 class="timeline-title">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
      </svg>
      Agent 工作流
    </h3>

    <div class="stages">
      <div
        v-for="stage in pipelineStages"
        :key="stage.time"
        class="stage-tag"
      >
        {{ stageLabel(stage.agentName) }}
      </div>
    </div>

    <div class="agent-list">
      <TransitionGroup name="agent-item">
        <div
          v-for="agent in agentList"
          :key="agent.name"
          class="agent-row"
          :class="agent.state"
        >
          <div class="agent-icon">
            <svg v-if="agent.state === 'done'" class="checkmark" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
            <div v-else-if="agent.state === 'running'" class="active-ring"></div>
            <svg v-else-if="agent.state === 'failed'" class="fail-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
            <div v-else class="waiting-dot"></div>
          </div>
          <span class="agent-label">{{ agent.label }}</span>
          <span v-if="agent.state === 'running'" class="agent-status-text">运行中...</span>
          <span v-else-if="agent.state === 'done'" class="agent-status-text done">完成</span>
          <span v-else-if="agent.state === 'failed'" class="agent-status-text failed">失败</span>
        </div>
      </TransitionGroup>
    </div>
  </div>
</template>

<style scoped>
.ai-timeline {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 16px;
}

.timeline-title {
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

.timeline-title svg { color: var(--primary); }

.stages {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 12px;
}

.stage-tag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  background: var(--bg-elevated);
  color: var(--text-muted);
  border: 1px solid var(--border);
}

.agent-list { display: flex; flex-direction: column; gap: 2px; }

.agent-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  transition: background 0.3s ease;
}

.agent-row.running {
  background: rgba(99, 102, 241, 0.08);
}

.agent-icon {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.checkmark {
  color: #22c55e;
  stroke-dasharray: 30;
  stroke-dashoffset: 30;
  animation: draw-check 0.4s ease forwards;
}

@keyframes draw-check {
  to { stroke-dashoffset: 0; }
}

.active-ring {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid var(--primary);
  border-top-color: transparent;
  animation: spin-ring 1s linear infinite;
}

@keyframes spin-ring {
  to { transform: rotate(360deg); }
}

.fail-icon { color: #ef4444; }

.waiting-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--border-light);
}

.agent-label {
  font-size: 12px;
  color: var(--text-secondary);
  flex: 1;
}

.agent-row.running .agent-label { color: var(--text-primary); font-weight: 500; }
.agent-row.done .agent-label { color: var(--text-primary); }

.agent-status-text {
  font-size: 11px;
  color: var(--primary);
}

.agent-status-text.done { color: #22c55e; }
.agent-status-text.failed { color: #ef4444; }

.agent-item-enter-active { transition: all 0.3s ease; }
.agent-item-leave-active { transition: all 0.2s ease; }
.agent-item-enter-from { opacity: 0; transform: translateY(-8px); }
.agent-item-leave-to { opacity: 0; }
</style>
