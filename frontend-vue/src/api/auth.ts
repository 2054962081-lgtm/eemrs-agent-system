import request from './request'
import type { LoginRequest, LoginResponse, RegisterRequest } from './types'

export function loginApi(data: LoginRequest) {
  return request.post<never, LoginResponse>('/auth/login', data)
}

export function registerApi(data: RegisterRequest) {
  return request.post<never, boolean>('/auth/register', data)
}
