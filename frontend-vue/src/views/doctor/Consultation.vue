<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageContainer from '../../components/PageContainer.vue'
import DataCard from '../../components/DataCard.vue'
import type { PatientInfo } from '../../api/types'

const route = useRoute()
const router = useRouter()
const patient = computed<PatientInfo>(() => {
  const raw = sessionStorage.getItem('eemrs-current-patient')
  return raw ? JSON.parse(raw) : { idNumber: String(route.params.patientId) }
})
</script>

<template>
  <PageContainer>
    <h2 class="page-title">接诊患者</h2>
    <p class="page-subtitle">确认患者基础信息后进入病历书写。</p>
    <DataCard title="患者信息">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="姓名">{{ patient.userName || '未返回' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ patient.idNumber || route.params.patientId }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ patient.gender || '-' }}</el-descriptions-item>
        <el-descriptions-item label="年龄">{{ patient.age || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ patient.telephone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地址">{{ patient.address || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="actions">
        <el-button type="primary" @click="router.push(`/doctor/record-editor/${route.params.patientId}`)">进入病历书写</el-button>
      </div>
    </DataCard>
  </PageContainer>
</template>

<style scoped>
.actions {
  margin-top: 18px;
}
</style>
