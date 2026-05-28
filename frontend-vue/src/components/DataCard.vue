<script setup lang="ts">
defineProps<{
  title?: string
  subtitle?: string
}>()
</script>

<template>
  <el-card class="data-card" shadow="never">
    <template v-if="title" #header>
      <div class="card-header">
        <div>
          <strong>{{ title }}</strong>
          <p v-if="subtitle">{{ subtitle }}</p>
        </div>
        <slot name="extra" />
      </div>
    </template>
    <slot />
  </el-card>
</template>

<style scoped>
.data-card {
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-card);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

strong {
  font-size: 16px;
}

p {
  margin: 6px 0 0;
  color: var(--color-muted);
  font-size: 13px;
}
</style>
