<script setup lang="ts">
import { reactive, ref } from 'vue'
import PageContainer from '../../components/PageContainer.vue'
import DataCard from '../../components/DataCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import { queryMedicalRecords } from '../../api/medicalRecord'
import type { VisitInfo } from '../../api/types'

const loading = ref(false)
const records = ref<VisitInfo[]>([])
const detail = ref<VisitInfo | null>(null)
const visible = ref(false)
const query = reactive({ patientIdNumber: '', department: '', range: [] as string[], minAge: '', maxAge: '' })

function toMillis(value?: string) {
  return value ? new Date(value).getTime() : undefined
}

async function search() {
  loading.value = true
  try {
    records.value = await queryMedicalRecords({
      patientIdNumber: query.patientIdNumber || undefined,
      department: query.department || undefined,
      startTime: toMillis(query.range?.[0]),
      endTime: toMillis(query.range?.[1]),
      minAge: query.minAge || undefined,
      maxAge: query.maxAge || undefined,
    })
  } finally {
    loading.value = false
  }
}

function open(row: VisitInfo) {
  detail.value = row
  visible.value = true
}
</script>

<template>
  <PageContainer>
    <h2 class="page-title">病历查询</h2>
    <p class="page-subtitle">医生只能查询与自己相关的病历，后端会按登录医生强制限定范围。</p>
    <DataCard class="filter-card" title="查询条件">
      <el-form :model="query" inline>
        <el-form-item label="患者身份证号"><el-input v-model="query.patientIdNumber" clearable /></el-form-item>
        <el-form-item label="科室"><el-input v-model="query.department" clearable /></el-form-item>
        <el-form-item label="时间区间"><el-date-picker v-model="query.range" type="daterange" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="年龄"><el-input v-model="query.minAge" placeholder="最小" class="age-input" /> <el-input v-model="query.maxAge" placeholder="最大" class="age-input" /></el-form-item>
        <el-form-item><el-button type="primary" :loading="loading" @click="search">查询</el-button></el-form-item>
      </el-form>
    </DataCard>
    <div class="table-card">
      <el-table v-loading="loading" :data="records" empty-text="暂无记录">
        <el-table-column prop="patientName" label="患者" min-width="120" />
        <el-table-column prop="patientIdNumber" label="患者身份证号" min-width="180" />
        <el-table-column prop="department" label="科室" min-width="100" />
        <el-table-column prop="visitTime" label="就诊时间" min-width="150" />
        <el-table-column prop="conditionDescription" label="病情描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="medication" label="用药" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }"><el-button size="small" @click="open(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!loading && records.length === 0" />
    </div>
    <el-dialog v-model="visible" title="病历详情" width="560px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="患者">{{ detail.patientName }}</el-descriptions-item>
        <el-descriptions-item label="科室">{{ detail.department }}</el-descriptions-item>
        <el-descriptions-item label="医生">{{ detail.doctorName }}</el-descriptions-item>
        <el-descriptions-item label="病情描述">{{ detail.conditionDescription }}</el-descriptions-item>
        <el-descriptions-item label="用药">{{ detail.medication }}</el-descriptions-item>
        <el-descriptions-item label="签名">{{ detail.signature }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </PageContainer>
</template>

<style scoped>
.age-input {
  width: 86px;
  margin-right: 8px;
}
</style>
