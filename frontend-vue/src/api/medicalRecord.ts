import request from './request'
import type { MedicalRecordQuery, MedicalRecordRequest, VisitInfo } from './types'

export function createMedicalRecord(data: MedicalRecordRequest) {
  return request.post<never, boolean>('/medical-records', data)
}

export function queryMedicalRecords(params: MedicalRecordQuery) {
  return request.get<never, VisitInfo[]>('/medical-records', { params })
}
