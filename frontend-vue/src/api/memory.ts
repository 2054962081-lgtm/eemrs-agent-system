import request from './request'

export interface ShortTermMemory {
  sessionId: string
  patientIdHash: string
  chiefComplaint?: string
  currentRound?: number
  askedQuestions?: string[]
  answers?: string[]
  pendingQuestions?: string[]
  ragContextIds?: string[]
  temporaryConclusion?: string
  completed?: boolean
  createdAt?: number
  updatedAt?: number
  expireAt?: number
}

export interface MemoryContext {
  longTermMemory: Record<string, unknown>
  mediumTermMemory: Record<string, unknown>[]
  shortTermMemory?: ShortTermMemory | null
  relatedUserMemory: Record<string, unknown>[]
}

export interface HealthProfile {
  id?: number
  patientIdHash?: string
  idNumberHash?: string
  gender?: string
  birthDate?: string
  heightCm?: number
  weightKg?: number
  bloodType?: string
  specialStatus?: string
  source?: string
  confirmed?: number
  active?: number
  createdAt?: string
  updatedAt?: string
}

export interface LongTermMemoryItem {
  id?: number
  patientIdHash?: string
  memoryType: string
  memoryKey?: string
  memoryValue: string
  severity?: string
  relation?: string
  evidence?: string
  source?: string
  confirmed?: number
  active?: number
  createdAt?: string
  updatedAt?: string
}

export interface LongTermMemoryCandidate {
  candidateId?: string
  sessionId?: string
  memoryType: string
  memoryKey?: string
  memoryValue: string
  severity?: string
  relation?: string
  evidence?: string
  confidence?: number
  needConfirm?: boolean
}

export function getMemoryContext(sessionId: string, query: string) {
  return request.get<MemoryContext, MemoryContext>('/memory/context', {
    params: { sessionId, query },
  })
}

export function getLongTermHealthProfile() {
  return request.get<HealthProfile, HealthProfile>('/memory/long/profile')
}

export function saveLongTermHealthProfile(data: HealthProfile) {
  return request.post<HealthProfile, HealthProfile>('/memory/long/profile', data)
}

export function getLongTermMemoryItems(memoryType?: string) {
  return request.get<LongTermMemoryItem[], LongTermMemoryItem[]>('/memory/long/items', {
    params: memoryType ? { memoryType } : undefined,
  })
}

export function addLongTermMemoryItem(data: LongTermMemoryItem) {
  return request.post<LongTermMemoryItem, LongTermMemoryItem>('/memory/long/items', data)
}

export function updateLongTermMemoryItem(id: number | string, data: LongTermMemoryItem) {
  return request.put<LongTermMemoryItem, LongTermMemoryItem>(`/memory/long/items/${encodeURIComponent(id)}`, data)
}

export function deleteLongTermMemoryItem(id: number | string) {
  return request.delete<boolean, boolean>(`/memory/long/items/${encodeURIComponent(id)}`)
}

export function getLongTermMemoryCandidates(sessionId: string) {
  return request.get<LongTermMemoryCandidate[], LongTermMemoryCandidate[]>('/memory/long/candidates', {
    params: { sessionId },
  })
}

export function saveLongTermMemoryCandidate(sessionId: string, data: LongTermMemoryCandidate) {
  return request.post<LongTermMemoryCandidate, LongTermMemoryCandidate>('/memory/long/candidates', data, {
    params: { sessionId },
  })
}

export function confirmLongTermMemoryCandidate(candidateId: string, sessionId: string, department?: string) {
  return request.post<LongTermMemoryItem, LongTermMemoryItem>(
    `/memory/long/candidates/${encodeURIComponent(candidateId)}/confirm`,
    { sessionId, department },
  )
}

export function rejectLongTermMemoryCandidate(candidateId: string, sessionId: string) {
  return request.post<boolean, boolean>(
    `/memory/long/candidates/${encodeURIComponent(candidateId)}/reject`,
    { sessionId },
  )
}

export function createShortTermMemorySession(sessionId: string) {
  return request.post<ShortTermMemory, ShortTermMemory>('/memory/short/session', { sessionId })
}

export function appendShortTermQuestionAnswer(
  sessionId: string,
  data: { question: string; answer: string; round?: number; temporaryConclusion?: string },
) {
  return request.post<ShortTermMemory, ShortTermMemory>(`/memory/short/session/${encodeURIComponent(sessionId)}/qa`, data)
}

export function completeShortTermMemorySession(
  sessionId: string,
  data: { summary?: string; department?: string; sourceId?: string },
) {
  return request.post<ShortTermMemory, ShortTermMemory>(
    `/memory/short/session/${encodeURIComponent(sessionId)}/complete`,
    data,
  )
}
