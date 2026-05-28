<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { ElMessage, type FormRules } from 'element-plus'
import { registerApi } from '../../api/auth'
import type { RegisterRequest } from '../../api/types'

const router = useRouter()
const loading = ref(false)
const form = reactive<RegisterRequest>({
  type: 'pt',
  idNumber: '',
  userName: '',
  password: '',
  department: '',
})

const rules: FormRules = {
  idNumber: [{ required: true, message: '请输入身份证号', trigger: 'blur' }],
  userName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  loading.value = true
  try {
    await registerApi({ ...form, department: form.type === 'dt' ? form.department : undefined })
    ElMessage.success('注册请求已提交')
    router.push('/login')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-card class="auth-card" shadow="never">
    <h2>注册账号</h2>
    <p>注册后可使用患者端或医生端对应功能。</p>
    <el-form :model="form" :rules="rules" label-position="top">
      <el-form-item label="账号类型">
        <el-segmented v-model="form.type" :options="[{ label: '患者', value: 'pt' }, { label: '医生', value: 'dt' }]" />
      </el-form-item>
      <el-form-item label="姓名" prop="userName">
        <el-input v-model="form.userName" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item label="身份证号" prop="idNumber">
        <el-input v-model="form.idNumber" placeholder="请输入身份证号" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" show-password placeholder="请输入密码" />
      </el-form-item>
      <el-form-item v-if="form.type === 'dt'" label="科室">
        <el-input v-model="form.department" placeholder="例如：内科" />
      </el-form-item>
      <el-button type="primary" size="large" :loading="loading" class="full-button" @click="submit">注册</el-button>
    </el-form>
    <div class="auth-footer">
      已有账号？<RouterLink to="/login">返回登录</RouterLink>
    </div>
  </el-card>
</template>

<style scoped>
.auth-card {
  width: 100%;
  max-width: 420px;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-card);
}

h2 {
  margin: 0 0 8px;
  font-size: 26px;
}

p {
  margin: 0 0 24px;
  color: var(--color-muted);
}

.full-button {
  width: 100%;
}

.auth-footer {
  margin-top: 18px;
  text-align: center;
  color: var(--color-muted);
}

a {
  color: var(--color-primary);
  font-weight: 700;
}
</style>
