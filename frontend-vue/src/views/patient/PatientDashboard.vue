<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Calendar, ChatDotRound, Document } from '@element-plus/icons-vue'
import PageContainer from '../../components/PageContainer.vue'
import DataCard from '../../components/DataCard.vue'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
</script>

<template>
  <PageContainer>
    <div class="toolbar">
      <div>
        <h2 class="page-title">您好，欢迎使用患者端</h2>
        <p class="page-subtitle">当前登录身份证号：{{ auth.idNumber }}</p>
      </div>
    </div>
    <div class="action-grid">
      <DataCard title="预约挂号" subtitle="按科室查询医生并提交挂号">
        <el-button type="primary" :icon="Calendar" @click="router.push('/patient/appointment')">进入预约</el-button>
      </DataCard>
      <DataCard title="病历/报告" subtitle="查询自己的就诊记录与报告信息">
        <el-button :icon="Document" @click="router.push('/patient/records')">查询记录</el-button>
      </DataCard>
      <DataCard title="AI 预问诊" subtitle="选择快速或深度问诊，获取本地大模型预问诊回复">
        <el-button :icon="ChatDotRound" @click="router.push('/patient/pre-consultation')">开始问诊</el-button>
      </DataCard>
    </div>
    <el-card class="info-card" shadow="never">
      <h3>系统说明</h3>
      <p>本系统为密文电子医疗系统 Web 端，前端通过 JWT 调用后端 REST API，病历写入仍由后端完成 SM2 验签与密文处理。</p>
    </el-card>
  </PageContainer>
</template>

<style scoped>
.info-card {
  margin: 16px 0;
  border: 1px solid var(--color-border);
}

h3 {
  margin: 0 0 8px;
}

p {
  margin: 0;
  color: var(--color-muted);
  line-height: 1.7;
}
</style>
