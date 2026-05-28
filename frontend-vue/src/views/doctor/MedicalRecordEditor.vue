<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageContainer from '../../components/PageContainer.vue'
import DataCard from '../../components/DataCard.vue'
import { createMedicalRecord } from '../../api/medicalRecord'
import type { PatientInfo } from '../../api/types'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)

const patient = computed<PatientInfo>(() => {
  const raw = sessionStorage.getItem('eemrs-current-patient')
  return raw ? JSON.parse(raw) : { idNumber: String(route.params.patientId) }
})

const form = reactive({
  department: auth.department || '',
  medication: '',
  conditionDescription: '',
  cost: '',
  visitTime: Date.now(),
  patientName: patient.value.userName || '',
  patientIdNumber: patient.value.idNumber || String(route.params.patientId),
  age: patient.value.age || '',
  doctorName: '',
  doctorIdNumber: auth.idNumber,
  gender: patient.value.gender || '',
  dPk: '',
  signature: '',
})

function optionalNumber(value: unknown) {
  const text = String(value || '').trim()
  return text ? Number(text) : undefined
}

async function submit() {
  loading.value = true
  try {
    const inserted = await createMedicalRecord({
      department: form.department.trim(),
      medication: form.medication.trim(),
      conditionDescription: form.conditionDescription.trim(),
      cost: form.cost.trim(),
      visitTime: Number(form.visitTime),
      patientName: form.patientName.trim(),
      patientIdNumber: form.patientIdNumber.trim(),
      age: optionalNumber(form.age),
      doctorName: form.doctorName.trim(),
      doctorIdNumber: form.doctorIdNumber.trim(),
      gender: form.gender.trim(),
      dPk: form.dPk.trim(),
      signature: form.signature.trim(),
    })
    if (!inserted) {
      ElMessage.error('病历写入失败，请检查患者状态和 SM2 签名信息')
      return
    }
    ElMessage.success('写病历成功')
    router.push('/doctor/records')
  } catch {
    ElMessage.error('提交病历失败，请查看后端返回的错误信息')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <PageContainer>
    <h2 class="page-title">病历书写</h2>
    <p class="page-subtitle">医生身份证号和科室优先来自登录态，SM2 签名信息保留为高级输入区。</p>
    <DataCard title="病历信息">
      <el-form :model="form" label-width="130px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="科室"><el-input v-model="form.department" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="就诊时间"><el-input-number v-model="form.visitTime" :min="0" class="wide" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="患者姓名"><el-input v-model="form.patientName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="患者身份证号"><el-input v-model="form.patientIdNumber" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="年龄"><el-input v-model="form.age" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="性别"><el-input v-model="form.gender" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="医生姓名"><el-input v-model="form.doctorName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="医生身份证号"><el-input v-model="form.doctorIdNumber" disabled /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="病情描述"><el-input v-model="form.conditionDescription" type="textarea" :rows="4" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="用药"><el-input v-model="form.medication" type="textarea" :rows="3" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="费用"><el-input v-model="form.cost" /></el-form-item></el-col>
        </el-row>
        <el-collapse>
          <el-collapse-item title="SM2 签名信息" name="sm2">
            <el-alert type="info" show-icon :closable="false" title="请填入后端验签所需 dPk 与 signature，前端不保存医生私钥。" />
            <el-form-item label="dPk"><el-input v-model="form.dPk" type="textarea" :rows="3" /></el-form-item>
            <el-form-item label="signature"><el-input v-model="form.signature" type="textarea" :rows="3" /></el-form-item>
          </el-collapse-item>
        </el-collapse>
        <el-form-item class="submit-row">
          <el-button type="primary" :loading="loading" @click="submit">提交病历</el-button>
          <el-button @click="router.push('/doctor/waiting-list')">返回候诊列表</el-button>
        </el-form-item>
      </el-form>
    </DataCard>
  </PageContainer>
</template>

<style scoped>
.wide {
  width: 100%;
}

.submit-row {
  margin-top: 18px;
}
</style>
