import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { AnalysisResult, SseEvent, AgentEvent, AgentEventType, TokenUsage, ConfidenceScores, AnalysisStatus, ChunkProgress } from '@/types'
import { analyzePRStream } from '@/api'

export const useAnalysisStore = defineStore('analysis', () => {
  const result = ref<AnalysisResult | null>(null)
  const status = ref<AnalysisStatus | ''>('')
  const statusMessage = ref('')
  const aiStream = ref('')
  const loading = ref(false)
  const error = ref('')
  const prUrl = ref('')
  const sseStatusHistory = ref<Array<{ status: string; message: string; time: number }>>([])

  // Agent workflow state
  const agentEvents = ref<Array<AgentEvent & { time: number }>>([])
  const activeAgents = ref<string[]>([])
  const tokenUsage = ref<TokenUsage | null>(null)
  const confidence = ref<ConfidenceScores | null>(null)
  const agentLog = ref<string[]>([])
  const chunkProgress = ref<ChunkProgress | null>(null)

  // Multi-provider state
  const selectedProviders = ref<string[]>([])
  const providerResults = ref<Record<string, AnalysisResult>>({})
  const providerStreams = ref<Record<string, string>>({})
  const isMultiProvider = computed(() => selectedProviders.value.length > 1)
  const displayProvider = ref('')

  function switchProvider(name: string) {
    displayProvider.value = name
    const saved = providerResults.value[name]
    if (saved) {
      if (result.value) {
        result.value = {
          ...result.value,
          riskScore: saved.riskScore,
          aiRawOutput: saved.aiRawOutput,
          tokenUsage: saved.tokenUsage,
          confidenceScores: saved.confidenceScores,
          agentTimeline: saved.agentTimeline
        }
      } else {
        result.value = saved
      }
      if (saved.tokenUsage) tokenUsage.value = saved.tokenUsage
      if (saved.confidenceScores) confidence.value = saved.confidenceScores
      aiStream.value = saved.aiRawOutput || ''
    } else {
      aiStream.value = providerStreams.value[name] || ''
    }
  }

  // Computed agent workflow progress
  const completedAgents = computed(() =>
    agentEvents.value.filter(e => e.type === 'AGENT_COMPLETED').map(e => e.agentName)
  )
  const failedAgents = computed(() =>
    agentEvents.value.filter(e => e.type === 'AGENT_FAILED').map(e => e.agentName)
  )
  const currentStage = computed(() => {
    const stages = agentEvents.value.filter(e => e.type === 'PIPELINE_STAGE')
    return stages.length > 0 ? stages[stages.length - 1].agentName : ''
  })

  let abortController: AbortController | null = null

  function reset() {
    abortController?.abort()
    abortController = null
    result.value = null
    status.value = ''
    statusMessage.value = ''
    aiStream.value = ''
    loading.value = false
    error.value = ''
    prUrl.value = ''
    sseStatusHistory.value = []
    agentEvents.value = []
    activeAgents.value = []
    tokenUsage.value = null
    confidence.value = null
    agentLog.value = []
    chunkProgress.value = null
    selectedProviders.value = []
    providerResults.value = {}
    providerStreams.value = {}
    displayProvider.value = ''
  }

  function startStreaming(url: string, providerOrProviders?: string | string[]) {
    reset()
    prUrl.value = url
    loading.value = true
    status.value = 'PENDING'

    let provider: string | undefined
    let providers: string[] | undefined
    if (Array.isArray(providerOrProviders)) {
      providers = providerOrProviders
      selectedProviders.value = providers
    } else {
      provider = providerOrProviders
      if (provider) selectedProviders.value = [provider]
    }

    abortController = analyzePRStream(
      { prUrl: url, provider, providers },
      (event: SseEvent) => handleSseEvent(event),
      (err: Error) => {
        error.value = err.message || 'Connection failed'
        status.value = 'FAILED'
        loading.value = false
      },
      () => {
        loading.value = false
      }
    )
  }

  function handleSseEvent(event: SseEvent) {
    const etype = event.type as string



    // Legacy status events (from SseEmitterService for blocking flow)

    if (etype === 'status') {
      if (event.status) {
        status.value = event.status as AnalysisStatus
        sseStatusHistory.value.push({
          status: event.status,
          message: event.message || '',
          time: Date.now()
        })
      }
      if (event.message) {
        statusMessage.value = event.message
      }
      return
    }



    // Legacy token events

    if (etype === 'token' || etype === 'ai_token') {
      if (event.content) {
        aiStream.value += event.content
      }
      return
    }



    // Agent lifecycle events

    if (etype === 'AGENT_STARTED') {
      const name = event.agentName || ''
      if (!activeAgents.value.includes(name)) {
        activeAgents.value.push(name)
      }
      agentLog.value.push(`[${name}] STARTED: ${event.message || ''}`)
      pushAgentEvent(event, 'AGENT_STARTED')
      return
    }

    if (etype === 'AGENT_RUNNING') {
      const name = event.agentName || ''
      agentLog.value.push(`[${name}] RUNNING: ${event.message || ''}`)
      pushAgentEvent(event, 'AGENT_RUNNING')
      return
    }

    if (etype === 'AGENT_COMPLETED') {
      const name = event.agentName || ''
      activeAgents.value = activeAgents.value.filter(a => a !== name)
      agentLog.value.push(`[${name}] COMPLETED: ${event.message || ''}`)
      pushAgentEvent(event, 'AGENT_COMPLETED')
      return
    }

    if (etype === 'AGENT_FAILED') {
      const name = event.agentName || ''
      activeAgents.value = activeAgents.value.filter(a => a !== name)
      agentLog.value.push(`[${name}] FAILED: ${event.message || ''}`)
      pushAgentEvent(event, 'AGENT_FAILED')
      return
    }

    if (etype === 'AGENT_SKIPPED') {
      pushAgentEvent(event, 'AGENT_SKIPPED')
      return
    }

    if (etype === 'PIPELINE_STAGE') {
      pushAgentEvent(event, 'PIPELINE_STAGE')
      return
    }


    // Chunk progress events
    if (etype === 'CHUNK_PROGRESS') {
      if (event.metadata) {
        chunkProgress.value = {
          current: (event.metadata.chunkIndex ?? 0) + 1,
          total: event.metadata.totalChunks ?? 0,
          fileName: event.metadata.fileName,
          status: event.metadata.chunkStatus
        }
      }
      return
    }


    // AI token events
    if (etype === 'AI_TOKEN') {
      const pName = event.metadata?.providerName as string | undefined
      if (event.metadata?.content) {
        if (pName && selectedProviders.value.length > 1) {
          if (!providerStreams.value[pName]) providerStreams.value[pName] = ''
          providerStreams.value[pName] += event.metadata.content
          if (pName === displayProvider.value) {
            aiStream.value += event.metadata.content
          }
        } else {
          aiStream.value += event.metadata.content
        }
      }
      return
    }


    // Analysis complete
    if (etype === 'ANALYSIS_COMPLETE') {
      const pName = event.metadata?.providerName as string | undefined
      if (pName && selectedProviders.value.length > 1) {
        if (event.metadata?.result) {
          providerResults.value = { ...providerResults.value, [pName]: event.metadata.result as AnalysisResult }
        }
        if (!displayProvider.value) {
          switchProvider(pName)
        }
        return
      }
      if (event.metadata?.result) {
        result.value = event.metadata.result as AnalysisResult
        if (event.metadata.result.tokenUsage) {
          tokenUsage.value = event.metadata.result.tokenUsage
        }
        if (event.metadata.result.confidenceScores) {
          confidence.value = event.metadata.result.confidenceScores
        }
      }
      status.value = 'COMPLETED'
      loading.value = false
      return
    }



    // Error event

    if (etype === 'ERROR') {
      error.value = event.message || 'Unknown error'
      status.value = 'FAILED'
      loading.value = false
      return
    }


    if (etype === 'MULTI_PROVIDER_COMPLETE') {
      if (event.metadata?.result) {
        const multiResult = event.metadata.result as any
        if (multiResult.providerResults) {
          for (const [pName, pResult] of Object.entries(multiResult.providerResults)) {
            const pr = pResult as any
            providerResults.value = {
              ...providerResults.value,
              [pName]: {
                analysisId: multiResult.analysisId,
                prTitle: multiResult.prTitle,
                prUrl: multiResult.prUrl,
                prNumber: multiResult.prNumber,
                owner: multiResult.owner,
                repo: multiResult.repo,
                author: multiResult.author,
                changedFiles: multiResult.changedFiles,
                additions: multiResult.additions,
                deletions: multiResult.deletions,
                ruleResults: multiResult.ruleResults,
                fileAnalysis: multiResult.fileAnalysis,
                repositoryProfile: multiResult.repositoryProfile,
                riskScore: pr.riskScore,
                aiRawOutput: pr.aiRawOutput,
                tokenUsage: pr.tokenUsage,
                confidenceScores: pr.confidenceScores,
                agentTimeline: pr.agentTimeline
              } as AnalysisResult
            }
            providerStreams.value[pName] = pr.aiRawOutput || ''
          }
        }
        const firstProvider = Object.keys(multiResult.providerResults || {})[0]
        if (firstProvider && !displayProvider.value) {
          switchProvider(firstProvider)
        }
      }
      status.value = 'COMPLETED'
      loading.value = false
      return
    }

    // Legacy complete event (from SseEmitterService)
    if (etype === 'complete') {
      if (event.data) {
        result.value = event.data
        if (event.data.tokenUsage) {
          tokenUsage.value = event.data.tokenUsage
        }
        if (event.data.confidenceScores) {
          confidence.value = event.data.confidenceScores
        }
      }
      status.value = 'COMPLETED'
      loading.value = false
      return
    }



    // Legacy error event

    if (etype === 'error') {
      error.value = event.message || 'Unknown error'
      status.value = 'FAILED'
      loading.value = false
      return
    }
  }

  function pushAgentEvent(event: SseEvent, type: AgentEventType) {
    agentEvents.value.push({
      traceId: event.traceId || '',
      type,
      agentName: event.agentName || '',
      message: event.message || '',
      timestamp: event.timestamp || Date.now(),
      metadata: event.metadata,
      time: Date.now()
    })
  }

  return {
    result, status, statusMessage, aiStream, loading, error, prUrl,
    sseStatusHistory, agentEvents, activeAgents, tokenUsage, confidence,
    completedAgents, failedAgents, currentStage, agentLog, chunkProgress,

    selectedProviders, providerResults, providerStreams, isMultiProvider,
    displayProvider, switchProvider,


    reset, startStreaming, handleSseEvent
  }
})
