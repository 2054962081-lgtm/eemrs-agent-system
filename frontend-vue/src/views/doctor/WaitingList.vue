<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageContainer from '../../components/PageContainer.vue'
import EmptyState from '../../components/EmptyState.vue'
import { acceptAppointment } from '../../api/appointment'
import { getWaitingList } from '../../api/doctor'
import type { WaitingPatient } from '../../api/types'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const accepting = ref('')
const list = ref<WaitingPatient[]>([])

function maskId(id: string) {
  return id ? `${id.slice(0, 4)}********${id.slice(-4)}` : ''
}

async function load() {
  loading.value = true
  try {
    list.value = await getWaitingList(auth.department, auth.idNumber)
  } finally {
    loading.value = false
  }
}

async function accept(row: WaitingPatient) {
  accepting.value = row.idNumber
  try {
    const data = await acceptAppointment(row.idNumber)
    ElMessage.success('接诊成功')
    sessionStorage.setItem('eemrs-current-patient', JSON.stringify(data.patientInfo || row))
    router.push({ path: `/doctor/consultation/${encodeURIComponent(row.idNumber)}` })
  } finally {
    accepting.value = ''
  }
}

onMounted(load)
</script>

<template>
  <PageContainer>
    <div class="toolbar">
      <div>
        <h2 class="page-title">候诊列表</h2>
        <p class="page-subtitle">仅查询当前医生所在科室和登录医生的候诊患者。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <div class="table-card">
      <el-table v-loading="loading" :data="list" empty-text="暂无候诊患者">
        <el-table-column prop="userName" label="患者姓名" min-width="140" />
        <el-table-column label="身份证号" min-width="180">
          <template #default="{ row }">{{ maskId(row.idNumber) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" :loading="accepting === row.idNumber" @click="accept(row)">接诊</el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!loading && list.length === 0" title="暂无候诊患者" />
    </div>
  </PageContainer>
</template>
