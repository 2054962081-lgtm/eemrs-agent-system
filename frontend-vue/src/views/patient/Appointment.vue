<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageContainer from '../../components/PageContainer.vue'
import DataCard from '../../components/DataCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import { createAppointment } from '../../api/appointment'
import { getDoctorsByDepartment } from '../../api/doctor'
import type { DoctorInfo } from '../../api/types'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const submitting = ref(false)
const doctors = ref<DoctorInfo[]>([])
const form = reactive({ department: '', userName: '' })

async function searchDoctors() {
  if (!form.department) {
    ElMessage.warning('请先选择或输入科室')
    return
  }
  loading.value = true
  try {
    doctors.value = await getDoctorsByDepartment(form.department)
  } finally {
    loading.value = false
  }
}

async function appoint(row: DoctorInfo) {
  submitting.value = true
  try {
    await createAppointment({
      department: form.department,
      idNumber: auth.idNumber,
      userName: form.userName || '患者',
      doctorIdNumber: row.idNumber,
    })
    ElMessage.success('挂号成功')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <PageContainer>
    <h2 class="page-title">预约挂号</h2>
    <p class="page-subtitle">患者身份证号来自登录态，不需要手动填写。</p>
    <DataCard class="filter-card" title="查询医生">
      <el-form :model="form" inline>
        <el-form-item label="科室">
          <el-input v-model="form.department" placeholder="例如：内科" clearable />
        </el-form-item>
        <el-form-item label="患者姓名">
          <el-input v-model="form.userName" placeholder="用于挂号记录" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="searchDoctors">查询医生</el-button>
        </el-form-item>
      </el-form>
    </DataCard>
    <div class="table-card">
      <el-table v-loading="loading" :data="doctors" empty-text="暂无医生">
        <el-table-column prop="userName" label="医生姓名" min-width="120" />
        <el-table-column prop="department" label="科室" min-width="120" />
        <el-table-column prop="gender" label="性别" width="90" />
        <el-table-column prop="idNumber" label="医生身份证号" min-width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" :loading="submitting" @click="appoint(row)">挂号</el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!loading && doctors.length === 0" title="暂无医生数据" description="输入科室后点击查询医生。" />
    </div>
  </PageContainer>
</template>
