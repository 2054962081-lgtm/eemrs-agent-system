<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLogo from './AppLogo.vue'

export interface MenuItem {
  path: string
  label: string
  icon: string
  disabled?: boolean
}

const props = defineProps<{ items: MenuItem[] }>()
const route = useRoute()
const router = useRouter()

const active = computed(() => route.path)

function go(item: MenuItem) {
  if (!item.disabled) router.push(item.path)
}
</script>

<template>
  <aside class="app-sidebar">
    <AppLogo />
    <nav>
      <button
        v-for="item in props.items"
        :key="item.path"
        :class="{ active: active === item.path, disabled: item.disabled }"
        type="button"
        @click="go(item)"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </button>
    </nav>
  </aside>
</template>

<style scoped>
.app-sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  width: 232px;
  padding: 22px 16px;
  background: #fff;
  border-right: 1px solid var(--color-border);
  z-index: 10;
}

nav {
  display: grid;
  gap: 8px;
  margin-top: 28px;
}

button {
  width: 100%;
  height: 44px;
  padding: 0 12px;
  border: 0;
  border-radius: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #4e6078;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  text-align: left;
}

button.active,
button:hover {
  color: var(--color-primary);
  background: #edf6ff;
}

button.disabled {
  color: #a9b5c5;
  cursor: not-allowed;
}

@media (max-width: 860px) {
  .app-sidebar {
    position: static;
    width: 100%;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  nav {
    grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  }
}
</style>
