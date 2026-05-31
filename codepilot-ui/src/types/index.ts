export interface PrAnalyzeRequest {
  prUrl: string
  provider?: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface RiskFinding {
  file: string
  line: number | null
  level: RiskLevel
  category: string
  description: string
  suggestion: string
  // Explainability fields (Phase 7)
  why?: string
  evidence?: string
  impact?: string
  confidenceScore?: number
}

export interface ReviewSuggestion {
  category: string
  title: string
  description: string
  file: string | null
  line: number | null
  codeSnippet: string
  suggestedFix: string
  priority: string
  // Explainability fields (Phase 7)
  why?: string
  evidence?: string
  impact?: string
}

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface RuleResult {
  ruleName: string
  category: string
  riskLevel: RiskLevel
  file: string | null
  line: number | null
  message: string
  suggestion: string
  codeSnippet: string
  matched: boolean
}

export interface RiskScore {
  totalScore: number
  maxScore: number
  riskLevel: RiskLevel
  fileCountScore: number
  ruleFindingsScore: number
  moduleScore: number
  volumeScore: number
  aiScore: number
  ruleBreakdown: Record<RiskLevel, number>
  fileCount: number
  totalAdditions: number
  totalDeletions: number
}

export interface TokenUsage {
  totalTokens: number
  promptTokens: number
  completionTokens: number
  totalChunks: number
  successfulChunks: number
}

export interface ConfidenceScores {
  overallConfidence: number
  confidenceLevel: 'HIGH' | 'MEDIUM' | 'LOW'
  factors: Record<string, number>
  summary: string
}

export interface RepositoryProfile {
  projectType: string
  languages: string[]
  frameworks: string[]
}

export interface FileAnalysis {
  filename: string
  language: string
  status: string
  additions: number
  deletions: number
  riskLevel: RiskLevel
  findings: RuleResult[]
  aiFindings?: RiskFinding[]
}

export interface AnalysisResult {
  analysisId: string
  prTitle: string
  prUrl: string
  prNumber: number
  owner: string
  repo: string
  author: string
  changedFiles: number
  additions: number
  deletions: number
  ruleResults: RuleResult[]
  riskScore: RiskScore
  aiRawOutput: string
  status: AnalysisStatus
  fileAnalysis: FileAnalysis[]
  // Phase 2+ fields
  repositoryProfile?: RepositoryProfile
  confidenceScores?: ConfidenceScores
  agentTimeline?: string[]
  tokenUsage?: TokenUsage
}

export type AnalysisStatus =
  | 'PENDING'
  | 'FETCHING_PR'
  | 'ANALYZING_DIFF'
  | 'BUILDING_CONTEXT'
  | 'RUNNING_RULES'
  | 'AI_REVIEWING'
  | 'CALCULATING_SCORE'
  | 'COMPLETED'
  | 'FAILED'

export type AgentEventType =
  | 'AGENT_STARTED' | 'AGENT_RUNNING' | 'AGENT_COMPLETED'
  | 'AGENT_FAILED' | 'AGENT_SKIPPED' | 'PIPELINE_STAGE'
  | 'CHUNK_PROGRESS' | 'AI_TOKEN' | 'ANALYSIS_COMPLETE' | 'ERROR'

export interface AgentEvent {
  traceId: string
  type: AgentEventType
  agentName: string
  message: string
  timestamp: number
  metadata?: Record<string, any>
}

export interface SseEvent {
  type: 'status' | 'token' | 'ai_token' | 'complete' | 'error'
    | AgentEventType
  status?: string
  message?: string
  content?: string
  data?: AnalysisResult
  count?: number
  agentName?: string
  traceId?: string
  timestamp?: number
  metadata?: Record<string, any>
}

export interface ChunkProgress {
  current: number
  total: number
  fileName?: string
  status?: string
}

export interface PrDetail {
  title: string
  author: string
  files: any[]
  commits: any[]
  additions: number
  deletions: number
  changedFiles: number
}
