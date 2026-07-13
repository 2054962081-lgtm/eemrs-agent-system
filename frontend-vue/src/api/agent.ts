import request from './request'
import type { MemoryContext } from './memory'

export interface AgentMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface PreConsultationRequest {
  mode: 'quick' | 'deep'
  sessionId?: string
  question: string
  round?: number
  history?: AgentMessage[]
  memoryContext?: MemoryContext
}

export interface PreConsultationResponse {
  success: boolean
  mode: 'quick' | 'deep'
  reply: string
  finished: boolean
  round: number
  recommendedDepartment: string
  urgency: 'emergency' | 'urgent' | 'normal' | 'observe'
  model: string
  error?: string
}

export interface ReportTrendAnalysisRequest {
  patientId: string | number
  sessionId?: string
  includePreconsultationContext?: boolean
  includeLongTermHealthContext?: boolean
  reportType?: 'LAB' | string
  startDate?: string
  endDate?: string
  targetItems?: string[]
  outputMode?: 'DOCTOR_AND_PATIENT' | string
}

export interface ReportTrendContextLink {
  type?: string
  symptoms?: string[]
  indicators?: string[]
  note?: string
}

export interface ReportTrendIndicatorItem {
  rawName?: string
  standardCode?: string
  standardName?: string
  value?: number | string
  unit?: string
  referenceLow?: number | string
  referenceHigh?: number | string
  abnormalFlag?: 'LOW' | 'NORMAL' | 'HIGH' | 'UNKNOWN' | string
}

export interface ReportTrendItem {
  code: string
  name: string
  latestValue?: number | string
  previousValue?: number | string
  minValue?: number | string
  maxValue?: number | string
  changeAbsolute?: number | string
  changePercent?: number | string
  trendDirection?: 'INCREASING' | 'DECREASING' | 'STABLE' | 'FLUCTUATING' | 'INSUFFICIENT_DATA' | string
  latestAbnormalFlag?: 'LOW' | 'NORMAL' | 'HIGH' | 'UNKNOWN' | string
  abnormalCount?: number
  consecutiveAbnormalCount?: number
  firstAbnormalDate?: string
  latestAbnormalDate?: string
}

export interface ReportTrendRecommendation {
  suggestedDepartment?: string
  suggestedAction?: string
}

export interface ReportTrendContextUsed {
  preconsultation: boolean
  longTermHealth: boolean
  triage: boolean
}

export interface ReportTrendAnalysisResponse {
  analysisId: string
  traceRunId: string
  status: 'SUCCESS' | 'FAILED' | string
  doctorSummary?: string
  patientExplanation?: string
  contextualInterpretation?: string
  contextLinks?: ReportTrendContextLink[]
  abnormalItems?: ReportTrendIndicatorItem[]
  trendItems?: ReportTrendItem[]
  followUpQuestions?: string[]
  recommendation?: ReportTrendRecommendation
  contextUsed?: ReportTrendContextUsed
  errorCode?: string
  errorMessage?: string
}

export interface MedicalRecordDraftGenerateRequest {
  sessionId?: string
  patientId?: number | string
  patientIdNumber?: string
  mode: 'deep'
  consultationConclusion: string
  history: AgentMessage[]
}

export interface MedicalRecordDraftGenerateResponse {
  success: boolean
  draftId?: number
  message?: string
  record?: any
  error?: string
}

export interface MedicalRecordDraftDetail {
  id: number
  patientId?: number
  patientIdNumber?: string
  doctorIdNumber?: string
  appointmentId?: string
  sessionId?: string
  consultationMode?: string
  sourceType?: string
  chiefComplaint?: string
  presentIllnessHistory?: string
  recommendedDepartment?: string
  urgency?: string
  consultationSummary?: string
  aiRecordJson?: any
  editedRecordJson?: any
  recordJson?: any
  status?: string
  modelName?: string
  promptVersion?: string
  traceId?: string
  createdAt?: string
  updatedAt?: string
  firstReviewedAt?: string
  completedAt?: string
  appliedAt?: string
  parseError?: boolean
}

export interface MedicalRecordDraftAuditLog {
  id: number
  draftId: number
  doctorIdNumber?: string
  action: string
  beforeJson?: string
  afterJson?: string
  rejectReason?: string
  comment?: string
  actionTime?: string
  traceId?: string
}

export interface MedicalRecordDraftActionResponse {
  success: boolean
  message: string
  draft: MedicalRecordDraftDetail
  idempotent: boolean
}

export interface MedicalRecordDraftHistoryResponse {
  draftId: number
  logs: MedicalRecordDraftAuditLog[]
}

export interface LatestMedicalRecordDraftResponse {
  success: boolean
  hasDraft: boolean
  draft?: MedicalRecordDraftDetail | null
  message?: string
  error?: string
}

export function sendPreConsultationMessage(data: PreConsultationRequest) {
  return request.post<PreConsultationResponse, PreConsultationResponse>('/agent/pre-consultation', data, {
    timeout: 180000,
  })
}

export const askPreConsultation = sendPreConsultationMessage

export function generateMedicalRecordDraft(data: MedicalRecordDraftGenerateRequest) {
  return request.post<MedicalRecordDraftGenerateResponse, MedicalRecordDraftGenerateResponse>(
    '/agent/medical-record-drafts/generate',
    data,
    {
      timeout: 120000,
    },
  )
}

export function getLatestMedicalRecordDraft(patientId: number | string) {
  return request.get<LatestMedicalRecordDraftResponse, LatestMedicalRecordDraftResponse>(
    '/agent/medical-record-drafts/latest',
    {
      params: { patientId },
      timeout: 30000,
    },
  )
}

export function getMedicalRecordDraftById(draftId: number | string) {
  return request.get<LatestMedicalRecordDraftResponse, LatestMedicalRecordDraftResponse>(
    `/agent/medical-record-drafts/${draftId}`,
    {
      timeout: 30000,
    },
  )
}

export function saveMedicalRecordDraftEdit(draftId: number | string, editedRecordJson: any, comment?: string) {
  return request.post<MedicalRecordDraftActionResponse, MedicalRecordDraftActionResponse>(
    `/agent/medical-record-drafts/${draftId}/edits`,
    { editedRecordJson, comment },
    { timeout: 30000 },
  )
}

export function acceptMedicalRecordDraft(draftId: number | string, editedRecordJson?: any, comment?: string) {
  return request.post<MedicalRecordDraftActionResponse, MedicalRecordDraftActionResponse>(
    `/agent/medical-record-drafts/${draftId}/accept`,
    { editedRecordJson, comment },
    { timeout: 30000 },
  )
}

export function partialAcceptMedicalRecordDraft(draftId: number | string, editedRecordJson: any, comment?: string) {
  return request.post<MedicalRecordDraftActionResponse, MedicalRecordDraftActionResponse>(
    `/agent/medical-record-drafts/${draftId}/partial-accept`,
    { editedRecordJson, comment },
    { timeout: 30000 },
  )
}

export function rejectMedicalRecordDraft(draftId: number | string, rejectReason: string, editedRecordJson?: any, comment?: string) {
  return request.post<MedicalRecordDraftActionResponse, MedicalRecordDraftActionResponse>(
    `/agent/medical-record-drafts/${draftId}/reject`,
    { editedRecordJson, rejectReason, comment },
    { timeout: 30000 },
  )
}

export function applyMedicalRecordDraft(draftId: number | string, payload: Record<string, unknown> = {}) {
  return request.post<MedicalRecordDraftActionResponse, MedicalRecordDraftActionResponse>(
    `/agent/medical-record-drafts/${draftId}/apply`,
    payload,
    { timeout: 60000 },
  )
}

export function getMedicalRecordDraftHistory(draftId: number | string) {
  return request.get<MedicalRecordDraftHistoryResponse, MedicalRecordDraftHistoryResponse>(
    `/agent/medical-record-drafts/${draftId}/history`,
    { timeout: 30000 },
  )
}

export function getAgentHealth() {
  return request.get<Record<string, unknown>, Record<string, unknown>>('/agent/health')
}

export function analyzeReportTrend(data: ReportTrendAnalysisRequest) {
  return request.post<ReportTrendAnalysisResponse, ReportTrendAnalysisResponse>('/agent/report-trend/analyze', data, {
    timeout: 180000,
  })
}

export function getReportTrendAnalysis(analysisId: string) {
  return request.get<ReportTrendAnalysisResponse, ReportTrendAnalysisResponse>(`/agent/report-trend/${analysisId}`, {
    timeout: 30000,
  })
}
