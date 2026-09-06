import request from './request'
import type { AcceptAppointmentResponse, AgentAppointmentRequest, AgentAppointmentResponse, CreateAppointmentRequest } from './types'

export function createAppointment(data: CreateAppointmentRequest) {
  return request.post<never, boolean>('/appointments', data)
}

export function createAgentAppointment(data: AgentAppointmentRequest) {
  return request.post<AgentAppointmentRequest, AgentAppointmentResponse>('/appointments/agent', data)
}

export function acceptAppointment(idNumber: string) {
  return request.post<never, AcceptAppointmentResponse>(`/appointments/${encodeURIComponent(idNumber)}/accept`)
}
