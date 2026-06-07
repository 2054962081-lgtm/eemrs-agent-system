<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageContainer from '../../components/PageContainer.vue'
import DataCard from '../../components/DataCard.vue'
import MedicalRecordDraftViewer from '../../components/doctor/MedicalRecordDraftViewer.vue'
import { createMedicalRecord, signMedicalRecord } from '../../api/medicalRecord'
import type { PatientInfo } from '../../api/types'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const signing = ref(false)
const signatureDirty = ref(false)

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

const signatureSource = computed(() => [
  form.department,
  form.medication,
  form.conditionDescription,
  form.cost,
  form.visitTime,
  form.patientName,
  form.patientIdNumber,
  form.age,
  form.doctorName,
  form.doctorIdNumber,
  form.gender,
])

function optionalNumber(value: unknown) {
  const text = String(value || '').trim()
  return text ? Number(text) : undefined
}

function buildPayload() {
  return {
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
  }
}

async function generateSignature(showSuccess = true) {
  signing.value = true
  try {
    const result = await signMedicalRecord(buildPayload())
    const dPk = result.dPk || result.DPk || result.dpk || ''
    if (!dPk || !result.signature) {
      throw new Error('signature response missing dPk or signature')
    }
    form.dPk = dPk
    form.signature = result.signature
    signatureDirty.value = false
    if (showSuccess) {
      ElMessage.success('签名已生成')
    }
  } catch {
    ElMessage.error('签名生成失败，请确认已接诊该患者且病历内容完整')
    throw new Error('signature generation failed')
  } finally {
    signing.value = false
  }
}

async function submit() {
  loading.value = true
  try {
    if (!form.dPk.trim() || !form.signature.trim()) {
      await generateSignature(false)
    }
    const inserted = await createMedicalRecord(buildPayload())
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

watch(signatureSource, () => {
  if (form.dPk || form.signature) {
    form.dPk = ''
    form.signature = ''
    signatureDirty.value = true
  }
})
</script>

<template>
  <PageContainer>
    <div class="toolbar">
      <div>
        <h2 class="page-title">病历书写</h2>
        <p class="page-subtitle">预问诊病历草稿仅供接诊参考，不会自动填入或覆盖医生录入内容。</p>
      </div>
      <MedicalRecordDraftViewer :patient-id="patient.idNumber || String(route.params.patientId)" />
    </div>

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
            <el-alert
              v-if="signatureDirty"
              type="warning"
              show-icon
              :closable="false"
              title="病历内容已修改，请重新生成签名。"
            />
            <el-alert
              v-else
              type="info"
              show-icon
              :closable="false"
              title="签名由后端使用医生 SM2 私钥生成，前端不保存医生私钥。"
            />
            <el-form-item label="dPk"><el-input v-model="form.dPk" type="textarea" :rows="3" readonly /></el-form-item>
            <el-form-item label="signature"><el-input v-model="form.signature" type="textarea" :rows="3" readonly /></el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="signing" @click="generateSignature()">生成签名</el-button>
            </el-form-item>
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
