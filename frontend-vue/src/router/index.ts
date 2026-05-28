import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AuthLayout from '../layouts/AuthLayout.vue'
import PatientLayout from '../layouts/PatientLayout.vue'
import DoctorLayout from '../layouts/DoctorLayout.vue'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/login' },
  {
    path: '/',
    component: AuthLayout,
    children: [
      { path: 'login', component: () => import('../views/auth/Login.vue'), meta: { guestOnly: true } },
      { path: 'register', component: () => import('../views/auth/Register.vue'), meta: { guestOnly: true } },
    ],
  },
  {
    path: '/patient',
    component: PatientLayout,
    meta: { requiresAuth: true, role: 'PATIENT' },
    children: [
      { path: 'dashboard', component: () => import('../views/patient/PatientDashboard.vue') },
      { path: 'appointment', component: () => import('../views/patient/Appointment.vue') },
      { path: 'records', component: () => import('../views/patient/MedicalRecords.vue') },
      { path: 'profile', component: () => import('../views/patient/Profile.vue') },
      { path: 'pre-consultation', component: () => import('../views/patient/PreConsultation.vue') },
    ],
  },
  {
    path: '/doctor',
    component: DoctorLayout,
    meta: { requiresAuth: true, role: 'DOCTOR' },
    children: [
      { path: 'dashboard', component: () => import('../views/doctor/DoctorDashboard.vue') },
      { path: 'waiting-list', component: () => import('../views/doctor/WaitingList.vue') },
      { path: 'consultation/:patientId', component: () => import('../views/doctor/Consultation.vue') },
      { path: 'record-editor/:patientId', component: () => import('../views/doctor/MedicalRecordEditor.vue') },
      { path: 'records', component: () => import('../views/doctor/MedicalRecordSearch.vue') },
    ],
  },
  { path: '/403', component: () => import('../views/common/Forbidden.vue') },
  { path: '/:pathMatch(.*)*', component: () => import('../views/common/NotFound.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  auth.restoreFromStorage()

  if (to.meta.guestOnly && auth.isLoggedIn) {
    return auth.isDoctor ? '/doctor/dashboard' : '/patient/dashboard'
  }

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const requiredRole = to.meta.role
  if (requiredRole && auth.role !== requiredRole) {
    return '/403'
  }

  return true
})

export default router
