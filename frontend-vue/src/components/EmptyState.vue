<script setup lang="ts">
import { DocumentRemove } from '@element-plus/icons-vue'

defineProps<{
  title?: string
  description?: string
}>()
</script>

<template>
  <div class="empty-state">
    <el-icon><DocumentRemove /></el-icon>
    <strong>{{ title || '暂无数据' }}</strong>
    <p>{{ description || '当前没有可展示的记录。' }}</p>
  </div>
</template>

<style scoped>
.empty-state {
  min-height: 180px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: var(--color-muted);
  text-align: center;
}

.el-icon {
  font-size: 36px;
  color: #8fbce8;
}

strong {
  color: var(--color-text);
}

p {
  margin: 0;
}
</style>
