<script setup lang="ts">
import { reactive, ref } from 'vue'
import PageContainer from '../../components/PageContainer.vue'
import DataCard from '../../components/DataCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import { queryMedicalRecords } from '../../api/medicalRecord'
import type { VisitInfo } from '../../api/types'

const loading = ref(false)
const records = ref<VisitInfo[]>([])
const selected = ref<VisitInfo | null>(null)
const drawer = ref(false)
const query = reactive({ department: '', doctorName: '', range: [] as string[] })

function toMillis(value?: string) {
  return value ? new Date(value).getTime() : undefined
}

async function search() {
  loading.value = true
  try {
    records.value = await queryMedicalRecords({
      department: query.department || undefined,
      doctorName: query.doctorName || undefined,
      startTime: toMillis(query.range?.[0]),
      endTime: toMillis(query.range?.[1]),
    })
  } finally {
    loading.value = false
  }
}

function reset() {
  query.department = ''
  query.doctorName = ''
  query.range = []
  records.value = []
}

function openDetail(row: VisitInfo) {
  selected.value = row
  drawer.value = true
}
</script>

<template>
  <PageContainer>
    <h2 class="page-title">病历/报告查询</h2>
    <p class="page-subtitle">仅展示当前患者自己的病历与报告。</p>
    <DataCard class="filter-card" title="查询条件">
      <el-form :model="query" inline>
        <el-form-item label="科室"><el-input v-model="query.department" clearable /></el-form-item>
        <el-form-item label="医生姓名"><el-input v-model="query.doctorName" clearable /></el-form-item>
        <el-form-item label="时间区间"><el-date-picker v-model="query.range" type="daterange" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </DataCard>
    <div class="table-card">
      <el-table v-loading="loading" :data="records" empty-text="暂无记录">
        <el-table-column prop="department" label="科室" min-width="100" />
        <el-table-column prop="doctorName" label="医生" min-width="100" />
        <el-table-column prop="visitTime" label="就诊时间" min-width="150" />
        <el-table-column prop="conditionDescription" label="病情描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="medication" label="用药" min-width="140" show-overflow-tooltip />
        <el-table-column prop="cost" label="费用" width="100" />
        <el-table-column prop="age" label="年龄" width="90" />
        <el-table-column prop="gender" label="性别" width="90" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }"><el-button size="small" @click="openDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!loading && records.length === 0" />
    </div>
    <el-drawer v-model="drawer" title="病历详情" size="420px">
      <el-descriptions v-if="selected" :column="1" border>
        <el-descriptions-item label="患者">{{ selected.patientName }}</el-descriptions-item>
        <el-descriptions-item label="科室">{{ selected.department }}</el-descriptions-item>
        <el-descriptions-item label="医生">{{ selected.doctorName }}</el-descriptions-item>
        <el-descriptions-item label="病情描述">{{ selected.conditionDescription }}</el-descriptions-item>
        <el-descriptions-item label="用药">{{ selected.medication }}</el-descriptions-item>
        <el-descriptions-item label="费用">{{ selected.cost }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </PageContainer>
</template>
