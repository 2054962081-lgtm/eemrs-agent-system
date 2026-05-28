<script setup lang="ts">
defineProps<{ text: string; type?: 'success' | 'warning' | 'info' | 'danger' }>()
</script>

<template>
  <el-tag :type="type || 'info'" round>{{ text }}</el-tag>
</template>
