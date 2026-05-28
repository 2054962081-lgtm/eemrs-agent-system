import request from './request'

export interface PreConsultationRequest {
  question: string
}

export interface PreConsultationResponse {
  reply: string
  model: string
}

export function askPreConsultation(data: PreConsultationRequest) {
  return request.post<PreConsultationResponse, PreConsultationResponse>('/agent/pre-consultation', data, {
    timeout: 120000,
  })
}

export function getAgentHealth() {
  return request.get<Record<string, unknown>, Record<string, unknown>>('/agent/health')
}
