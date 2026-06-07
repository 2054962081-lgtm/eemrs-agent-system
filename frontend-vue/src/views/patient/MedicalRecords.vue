<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageContainer from '../../components/PageContainer.vue'
import DataCard from '../../components/DataCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import { queryMedicalRecords } from '../../api/medicalRecord'
import { queryLabReportsByDepartmentTime, type LabReport, type LabReportSearchResponse } from '../../api/labReport'
import type { VisitInfo } from '../../api/types'

const loading = ref(false)
const reportLoading = ref(false)
const records = ref<VisitInfo[]>([])
const reportResult = ref<LabReportSearchResponse>({ latestReport: null, historyReports: [] })
const selected = ref<VisitInfo | null>(null)
const selectedReport = ref<LabReport | null>(null)
const drawer = ref(false)
const reportDrawer = ref(false)
const query = reactive({ department: '', doctorName: '', range: [] as string[] })
const reportQuery = reactive({
  department: '',
  queryTime: new Date().toISOString().slice(0, 10),
})

function toMillis(value?: string) {
  return value ? new Date(value).getTime() : undefined
}

function formatTime(value?: string | number) {
  if (!value) {
    return ''
  }
  const date = new Date(Number(value))
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString()
}

async function search() {
  const department = query.department.trim()
  loading.value = true
  try {
    records.value = await queryMedicalRecords({
      department: department || undefined,
      doctorName: query.doctorName.trim() || undefined,
      startTime: toMillis(query.range?.[0]),
      endTime: toMillis(query.range?.[1]),
    })
  } finally {
    loading.value = false
  }
}

async function searchReports() {
  const department = reportQuery.department.trim()
  if (!department) {
    ElMessage.warning('请输入科室')
    return
  }
  reportLoading.value = true
  try {
    reportResult.value = await queryLabReportsByDepartmentTime({
      department,
      queryTime: reportQuery.queryTime || new Date().toISOString().slice(0, 10),
    })
  } finally {
    reportLoading.value = false
  }
}

function reset() {
  query.department = ''
  query.doctorName = ''
  query.range = []
  records.value = []
}

function resetReports() {
  reportQuery.department = ''
  reportQuery.queryTime = new Date().toISOString().slice(0, 10)
  reportResult.value = { latestReport: null, historyReports: [] }
}

function openDetail(row: VisitInfo) {
  selected.value = row
  drawer.value = true
}

function openReportDetail(row: LabReport) {
  selectedReport.value = row
  reportDrawer.value = true
}
</script>

<template>
  <PageContainer>
    <h2 class="page-title">病历查询</h2>
    <p class="page-subtitle">病历与化验报告分开查询，当前页面仅使用登录用户自己的数据范围。</p>

    <el-tabs>
      <el-tab-pane label="病历查询" name="records">
        <DataCard class="filter-card" title="病历查询条件">
          <el-form :model="query" inline>
            <el-form-item label="科室"><el-input v-model="query.department" clearable /></el-form-item>
            <el-form-item label="医生姓名"><el-input v-model="query.doctorName" clearable /></el-form-item>
            <el-form-item label="时间区间"><el-date-picker v-model="query.range" type="daterange" value-format="YYYY-MM-DD" /></el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="search">病历查询</el-button>
              <el-button @click="reset">重置</el-button>
            </el-form-item>
          </el-form>
        </DataCard>
        <div class="table-card">
          <el-table v-loading="loading" :data="records" empty-text="暂无病历">
            <el-table-column prop="department" label="科室" min-width="100" />
            <el-table-column prop="doctorName" label="医生" min-width="100" />
            <el-table-column label="就诊时间" min-width="150">
              <template #default="{ row }">{{ formatTime(row.visitTime) }}</template>
            </el-table-column>
            <el-table-column prop="conditionDescription" label="病情描述" min-width="180" show-overflow-tooltip />
            <el-table-column prop="medication" label="用药" min-width="140" show-overflow-tooltip />
            <el-table-column prop="cost" label="费用" width="100" />
            <el-table-column prop="age" label="年龄" width="90" />
            <el-table-column prop="gender" label="性别" width="90" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }"><el-button size="small" @click="openDetail(row)">详情</el-button></template>
            </el-table-column>
          </el-table>
          <EmptyState v-if="!loading && records.length === 0" title="暂无病历" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="报告查询" name="reports">
        <DataCard class="filter-card" title="报告查询条件">
          <el-form :model="reportQuery" inline>
            <el-form-item label="科室"><el-input v-model="reportQuery.department" clearable /></el-form-item>
            <el-form-item label="查询日期">
              <el-date-picker v-model="reportQuery.queryTime" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="reportLoading" @click="searchReports">报告查询</el-button>
              <el-button @click="resetReports">重置</el-button>
            </el-form-item>
          </el-form>
        </DataCard>

        <DataCard v-if="reportResult.latestReport" class="filter-card" title="最新报告">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="科室">{{ reportResult.latestReport.department }}</el-descriptions-item>
            <el-descriptions-item label="报告类型">{{ reportResult.latestReport.reportType }}</el-descriptions-item>
            <el-descriptions-item label="报告时间">{{ formatTime(reportResult.latestReport.reportTime) }}</el-descriptions-item>
          </el-descriptions>
        </DataCard>

        <div class="table-card">
          <el-table v-loading="reportLoading" :data="reportResult.historyReports" empty-text="暂无报告">
            <el-table-column prop="department" label="科室" min-width="100" />
            <el-table-column prop="reportType" label="报告类型" min-width="120" />
            <el-table-column label="报告时间" min-width="150">
              <template #default="{ row }">{{ formatTime(row.reportTime) }}</template>
            </el-table-column>
            <el-table-column prop="reportPayload" label="报告内容" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }"><el-button size="small" @click="openReportDetail(row)">详情</el-button></template>
            </el-table-column>
          </el-table>
          <EmptyState v-if="!reportLoading && reportResult.historyReports.length === 0" title="暂无报告" />
        </div>
      </el-tab-pane>
    </el-tabs>

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
    <el-drawer v-model="reportDrawer" title="报告详情" size="420px">
      <el-descriptions v-if="selectedReport" :column="1" border>
        <el-descriptions-item label="科室">{{ selectedReport.department }}</el-descriptions-item>
        <el-descriptions-item label="报告类型">{{ selectedReport.reportType }}</el-descriptions-item>
        <el-descriptions-item label="报告时间">{{ formatTime(selectedReport.reportTime) }}</el-descriptions-item>
        <el-descriptions-item label="报告内容">{{ selectedReport.reportPayload }}</el-descriptions-item>
        <el-descriptions-item v-if="selectedReport.imageCipherUrl" label="影像地址">{{ selectedReport.imageCipherUrl }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </PageContainer>
</template>
