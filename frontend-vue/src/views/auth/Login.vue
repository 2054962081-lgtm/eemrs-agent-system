<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { ElMessage, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/auth'
import type { LoginRequest } from '../../api/types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)

const form = reactive<LoginRequest>({
  type: 'pt',
  idNumber: '',
  password: '',
  department: '',
})

const rules: FormRules = {
  idNumber: [{ required: true, message: '请输入身份证号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  department: [
    {
      validator: (_rule, value, callback) => {
        if (form.type === 'dt' && !value) callback(new Error('医生登录请填写科室'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function submit() {
  loading.value = true
  try {
    await auth.login({ ...form, department: form.type === 'dt' ? form.department : undefined })
    ElMessage.success('登录成功')
    const target = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    router.push(target || (auth.isDoctor ? '/doctor/dashboard' : '/patient/dashboard'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-card class="auth-card" shadow="never">
    <h2>登录系统</h2>
    <p>请选择身份后登录，系统会自动进入对应工作台。</p>
    <el-form :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
      <el-form-item label="登录身份">
        <el-segmented v-model="form.type" :options="[{ label: '患者', value: 'pt' }, { label: '医生', value: 'dt' }]" />
      </el-form-item>
      <el-form-item label="身份证号" prop="idNumber">
        <el-input v-model="form.idNumber" :prefix-icon="User" placeholder="请输入身份证号" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" :prefix-icon="Lock" placeholder="请输入密码" show-password />
      </el-form-item>
      <el-form-item v-if="form.type === 'dt'" label="科室" prop="department">
        <el-input v-model="form.department" placeholder="例如：内科" />
      </el-form-item>
      <el-button type="primary" size="large" :loading="loading" class="full-button" @click="submit">登录</el-button>
    </el-form>
    <div class="auth-footer">
      没有账号？<RouterLink to="/register">去注册</RouterLink>
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
