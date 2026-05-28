import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { loginApi } from '../api/auth'
import type { LoginRequest, LoginResponse, UserRole, UserType } from '../api/types'

const STORAGE_KEY = 'eemrs-auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref('')
  const tokenType = ref('Bearer')
  const idNumber = ref('')
  const type = ref<UserType | ''>('')
  const role = ref<UserRole | ''>('')
  const department = ref('')

  const isLoggedIn = computed(() => Boolean(token.value))
  const isPatient = computed(() => role.value === 'PATIENT')
  const isDoctor = computed(() => role.value === 'DOCTOR')

  function saveToStorage() {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        token: token.value,
        tokenType: tokenType.value,
        idNumber: idNumber.value,
        type: type.value,
        role: role.value,
        department: department.value,
      }),
    )
  }

  function applyLogin(data: LoginResponse) {
    token.value = data.token
    tokenType.value = data.tokenType || 'Bearer'
    idNumber.value = data.idNumber
    type.value = data.type
    role.value = data.role
    department.value = data.department || ''
    saveToStorage()
  }

  async function login(payload: LoginRequest) {
    const data = await loginApi(payload)
    applyLogin(data)
    return data
  }

  function logout() {
    token.value = ''
    tokenType.value = 'Bearer'
    idNumber.value = ''
    type.value = ''
    role.value = ''
    department.value = ''
    localStorage.removeItem(STORAGE_KEY)
  }

  function restoreFromStorage() {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return
    try {
      const data = JSON.parse(raw)
      token.value = data.token || ''
      tokenType.value = data.tokenType || 'Bearer'
      idNumber.value = data.idNumber || ''
      type.value = data.type || ''
      role.value = data.role || ''
      department.value = data.department || ''
    } catch {
      logout()
    }
  }

  return {
    token,
    tokenType,
    idNumber,
    type,
    role,
    department,
    isLoggedIn,
    isPatient,
    isDoctor,
    login,
    logout,
    restoreFromStorage,
    saveToStorage,
  }
})
