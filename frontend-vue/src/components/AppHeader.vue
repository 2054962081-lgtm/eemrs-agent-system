<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const props = defineProps<{
  title: string
  roleName: string
}>()

const router = useRouter()
const auth = useAuthStore()

const userText = computed(() => `${props.roleName} ${auth.idNumber || '未识别用户'}`)

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <header class="app-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="auth.department">科室：{{ auth.department }}</p>
    </div>
    <div class="header-user">
      <span>{{ userText }}</span>
      <el-button :icon="SwitchButton" @click="handleLogout">退出登录</el-button>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  height: 72px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--color-border);
}

h1 {
  margin: 0;
  font-size: 18px;
}

p {
  margin: 4px 0 0;
  color: var(--color-muted);
  font-size: 13px;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--color-muted);
}

@media (max-width: 640px) {
  .app-header {
    height: auto;
    padding: 14px 16px;
    align-items: flex-start;
    gap: 12px;
    flex-direction: column;
  }
}
</style>
