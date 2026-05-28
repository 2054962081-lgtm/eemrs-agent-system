<template>
  <main class="simple-page">
    <el-result icon="warning" title="403" sub-title="无权限访问">
      <template #extra>
        <el-button type="primary" @click="$router.push('/login')">返回登录</el-button>
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
