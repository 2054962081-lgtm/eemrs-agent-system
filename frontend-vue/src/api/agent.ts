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
  sessionId?: string
  consultationMode?: string
  sourceType?: string
  chiefComplaint?: string
  presentIllnessHistory?: string
  recommendedDepartment?: string
  urgency?: string
  consultationSummary?: string
  recordJson?: any
  status?: string
  createdAt?: string
  updatedAt?: string
  parseError?: boolean
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

export function getAgentHealth() {
  return request.get<Record<string, unknown>, Record<string, unknown>>('/agent/health')
}
