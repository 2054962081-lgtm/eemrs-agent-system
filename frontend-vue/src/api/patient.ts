import request from './request'
import type { PatientInfo } from './types'

export function updatePatientMe(data: PatientInfo) {
  return request.put<never, boolean>('/patients/me', data)
}
