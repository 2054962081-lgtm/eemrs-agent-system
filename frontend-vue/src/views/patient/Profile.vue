<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageContainer from '../../components/PageContainer.vue'
import DataCard from '../../components/DataCard.vue'
import { updatePatientMe } from '../../api/patient'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const form = reactive({
  idNumber: auth.idNumber,
  telephone: '',
  address: '',
  mail: '',
  medicareCard: '',
  nation: '',
})

async function submit() {
  loading.value = true
  try {
    await updatePatientMe(form)
    ElMessage.success('个人资料已提交')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <PageContainer>
    <h2 class="page-title">个人资料</h2>
    <p class="page-subtitle">可修改手机号、地址、邮箱等基础信息。</p>
    <DataCard title="资料编辑">
      <el-form :model="form" label-width="100px">
        <el-form-item label="身份证号"><el-input v-model="form.idNumber" disabled /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.telephone" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.mail" /></el-form-item>
        <el-form-item label="医保卡"><el-input v-model="form.medicareCard" /></el-form-item>
        <el-form-item label="民族"><el-input v-model="form.nation" /></el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="submit">保存修改</el-button>
        </el-form-item>
      </el-form>
    </DataCard>
  </PageContainer>
</template>
