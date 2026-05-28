<script setup lang="ts">
import AppHeader from '../components/AppHeader.vue'
import AppSidebar, { type MenuItem } from '../components/AppSidebar.vue'

const items: MenuItem[] = [
  { path: '/doctor/dashboard', label: '首页', icon: 'House' },
  { path: '/doctor/waiting-list', label: '候诊列表', icon: 'List' },
  { path: '/doctor/records', label: '病历查询', icon: 'Search' },
  { path: '/doctor/ai', label: 'AI 病历助手', icon: 'MagicStick', disabled: true },
]
</script>

<template>
  <div class="app-shell">
    <AppSidebar :items="items" />
    <main class="shell-main">
      <AppHeader title="医生端工作台" role-name="医生" />
      <div class="shell-content">
        <router-view />
      </div>
    </main>
  </div>
</template>
