import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from './types'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const raw = localStorage.getItem('eemrs-auth')
  if (raw) {
    const auth = JSON.parse(raw)
    if (auth.token) {
      config.headers.Authorization = `${auth.tokenType || 'Bearer'} ${auth.token}`
    }
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body && typeof body.success === 'boolean') {
      if (!body.success) {
        ElMessage.error(body.message || '请求处理失败')
        return Promise.reject(new Error(body.message || '请求处理失败'))
      }
      return body.data
    }
    return response.data
  },
  (error: AxiosError<ApiResponse<null>>) => {
    const status = error.response?.status
    const message = error.response?.data?.message

    if (status === 401) {
      localStorage.removeItem('eemrs-auth')
      ElMessage.warning('登录已过期，请重新登录')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }

    if (status === 403) {
      ElMessage.error('无权限访问')
      return Promise.reject(error)
    }

    ElMessage.error(message || error.message || '网络请求失败')
    return Promise.reject(error)
  },
)

export default request
