export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export type UserType = 'pt' | 'dt'
export type UserRole = 'PATIENT' | 'DOCTOR'

export interface LoginRequest {
  type: UserType
  idNumber: string
  password: string
  department?: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  idNumber: string
  type: UserType
  role: UserRole
  department?: string
  expiresIn: number
}

export interface RegisterRequest {
  type: UserType
  idNumber: string
  userName: string
  password: string
  department?: string
}

export interface DoctorInfo {
  idNumber: string
  userName: string
  idHashCode?: string
  gender?: string
  department?: string
}

export interface PatientInfo {
  userName?: string
  gender?: string
  age?: string
  birthDay?: string
  idNumber?: string
  password?: string
  medicareCard?: string
  nation?: string
  telephone?: string
  address?: string
  mail?: string
}

export interface WaitingPatient {
  idNumber: string
  userName: string
}

export interface AcceptAppointmentResponse {
  idNumber: string
  patientInfo: PatientInfo
}

export interface CreateAppointmentRequest {
  department: string
  idNumber: string
  userName: string
  doctorIdNumber: string
}

export interface MedicalRecordRequest {
  department: string
  medication: string
  conditionDescription: string
  cost?: string
  visitTime: number | string
  patientName: string
  patientIdNumber: string
  age?: number | string
  doctorName: string
  doctorIdNumber: string
  gender?: string
  dPk?: string
  signature?: string
}

export interface MedicalRecordQuery {
  startTime?: number | string
  endTime?: number | string
  minAge?: number | string
  maxAge?: number | string
  patientIdNumber?: string
  doctorIdNumber?: string
  doctorName?: string
  department?: string
}

export interface VisitInfo extends MedicalRecordRequest {
  patientIdHashCode?: string
  doctorIdHashCode?: string
}

export interface MedicalRecordSignatureResponse {
  dPk?: string
  DPk?: string
  dpk?: string
  signature: string
}
