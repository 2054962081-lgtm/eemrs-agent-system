<script setup lang="ts">
import AppHeader from '../components/AppHeader.vue'
import AppSidebar, { type MenuItem } from '../components/AppSidebar.vue'

const items: MenuItem[] = [
  { path: '/patient/dashboard', label: '首页', icon: 'House' },
  { path: '/patient/appointment', label: '预约挂号', icon: 'Calendar' },
  { path: '/patient/records', label: '病历/报告', icon: 'Document' },
  { path: '/patient/pre-consultation', label: 'AI 预问诊', icon: 'ChatDotRound' },
  { path: '/patient/profile', label: '个人资料', icon: 'User' },
]
</script>

<template>
  <div class="app-shell">
    <AppSidebar :items="items" />
    <main class="shell-main">
      <AppHeader title="患者端工作台" role-name="患者" />
      <div class="shell-content">
        <router-view />
      </div>
    </main>
  </div>
</template>
