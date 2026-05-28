import request from './request'
import type { DoctorInfo, WaitingPatient } from './types'

export function getDoctorsByDepartment(department: string) {
  return request.get<never, DoctorInfo[]>('/doctors', { params: { department } })
}

export function getDoctorMe() {
  return request.get<never, DoctorInfo>('/doctors/me')
}

export function getWaitingList(department: string, doctorIdNumber?: string) {
  return request.get<never, WaitingPatient[]>('/doctors/me/waiting-list', {
    params: { department, doctorIdNumber },
  })
}
