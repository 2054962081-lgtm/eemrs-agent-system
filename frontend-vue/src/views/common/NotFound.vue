<template>
  <main class="simple-page">
    <el-result icon="info" title="404" sub-title="页面不存在">
      <template #extra>
        <el-button type="primary" @click="$router.push('/login')">返回首页</el-button>
      </template>
    </el-result>
  </main>
</template>

<style scoped>
.simple-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: var(--color-bg);
}
</style>
