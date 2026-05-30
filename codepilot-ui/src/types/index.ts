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
  confidence?: number
  confidencePassed?: boolean
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

export interface FileAnalysis {
  filename: string
  language: string
  status: string
  additions: number
  deletions: number
  riskLevel: RiskLevel
  findings: RuleResult[]
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

export interface SseEvent {
  type: 'status' | 'token' | 'ai_token' | 'complete' | 'error'
  status?: string
  message?: string
  content?: string
  data?: AnalysisResult
  count?: number
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

// Feedback types
export interface FeedbackSubmitRequest {
  analysisId: string
  findingId: string
  ruleName: string
  category?: string
  fileName?: string
  lineNumber?: number
  feedbackType: 'CONFIRMED' | 'FALSE_POSITIVE' | 'MISSED_ISSUE'
  comment?: string
}

export interface RuleConfidenceResponse {
  ruleName: string
  totalHits: number
  confirmed: number
  falsePositives: number
  missedIssues: number
  precisionRate: number
  recallEstimate: number
  minConfidenceThreshold: number
  enabled: boolean
}

// History / Trends types
export interface HistoryItem {
  analysisId: string
  prNumber: number
  prTitle: string
  author: string
  riskScore: number
  totalFindings: number
  status: string
  createdAt: string
}

export interface TrendPoint {
  date: string
  avgRiskScore: number
  avgFindings: number
  maxRiskScore: number
  minRiskScore: number
  prCount: number
}

export interface TrendData {
  riskScoreTrend: TrendPoint[]
  findingsTrend: TrendPoint[]
  prCountTrend: TrendPoint[]
  overallAvgRiskScore: number
  overallAvgFindings: number
  totalPRsAnalyzed: number
  trendDays: number
}

export interface ComparisonResult {
  current: HistoryItem | null
  previous: HistoryItem | null
  riskScoreDelta: number
  findingsDelta: number
  resolvedFindings: ComparisonFinding[]
  newFindings: ComparisonFinding[]
}

export interface ComparisonFinding {
  ruleName: string
  category: string
  file: string
  message: string
  isNew: boolean
}

export interface HistoryPageData {
  items: HistoryItem[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface RepoInfo {
  repoKey: string
  owner: string
  repo: string
}

