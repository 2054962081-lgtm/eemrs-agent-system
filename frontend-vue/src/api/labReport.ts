import request from './request'

export interface LabReportQuery {
  department: string
  queryTime: string
  patientIdNumber?: string
}

export interface LabReport {
  id?: number
  patientIdHashCode?: string
  reportToken?: string
  reportPayloadCipher?: string
  departmentCipher?: string
  reportTimeOpe?: string
  reportTypeCipher?: string
  imageCipherUrl?: string
  reportPayload?: string
  department?: string
  reportTime?: string
  reportType?: string
}

export interface LabReportSearchResponse {
  latestReport: LabReport | null
  historyReports: LabReport[]
}

export function queryLabReportsByDepartmentTime(params: LabReportQuery) {
  return request.get<never, LabReportSearchResponse>('/lab-reports/search-by-dept-time', { params })
}
