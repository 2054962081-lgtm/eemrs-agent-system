<script setup lang="ts">
import { useRouter } from 'vue-router'
import { List, Search, MagicStick } from '@element-plus/icons-vue'
import PageContainer from '../../components/PageContainer.vue'
import DataCard from '../../components/DataCard.vue'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
</script>

<template>
  <PageContainer>
    <h2 class="page-title">医生工作台</h2>
    <p class="page-subtitle">当前科室：{{ auth.department || '未设置' }}</p>
    <div class="action-grid">
      <DataCard title="候诊列表" subtitle="查看当前医生待接诊患者">
        <el-button type="primary" :icon="List" @click="router.push('/doctor/waiting-list')">进入候诊列表</el-button>
      </DataCard>
      <DataCard title="病历查询" subtitle="查询与自己相关的病历">
        <el-button :icon="Search" @click="router.push('/doctor/records')">查询病历</el-button>
      </DataCard>
      <DataCard title="AI 病历助手" subtitle="下一阶段启用病历草稿能力">
        <el-button :icon="MagicStick" disabled>暂未启用</el-button>
      </DataCard>
    </div>
    <div class="ai-placeholder">AI 功能将在下一阶段接入 Spring AI + Ollama 后启用。</div>
  </PageContainer>
</template>

<style scoped>
.action-grid {
  margin-bottom: 16px;
}
</style>
