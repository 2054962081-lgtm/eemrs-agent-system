<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ChatDotRound, Connection, FirstAidKit, Refresh, Promotion } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageContainer from '../../components/PageContainer.vue'
import { askPreConsultation, getAgentHealth } from '../../api/agent'

const question = ref('')
const reply = ref('')
const model = ref('')
const loading = ref(false)
const checking = ref(false)
const healthText = ref('未检测')

async function checkHealth() {
  checking.value = true
  try {
    const health = await getAgentHealth()
    healthText.value = health.status === 'UP' ? '智能体服务正常' : '智能体服务异常'
  } catch {
    healthText.value = '智能体服务不可用'
  } finally {
    checking.value = false
  }
}

async function submit() {
  const value = question.value.trim()
  if (!value) {
    return
  }

  loading.value = true
  reply.value = ''
  model.value = ''
  try {
    const result = await askPreConsultation({ question: value })
    reply.value = result.reply || '本次未返回有效内容，请稍后重试。'
    model.value = result.model
  } catch (error) {
    const message = error instanceof Error ? error.message : ''
    const timeoutMessage = 'AI 回复生成时间较长，请稍后重试，或确认 Ollama 模型是否已加载完成。'
    reply.value = message.includes('timeout') ? timeoutMessage : 'AI 预问诊请求失败，请稍后重试。'
    ElMessage.warning(reply.value)
  } finally {
    loading.value = false
  }
}

onMounted(checkHealth)
</script>

<template>
  <PageContainer>
    <div class="toolbar">
      <div>
        <h2 class="page-title">AI 预问诊</h2>
        <p class="page-subtitle">输入症状或健康问题，系统会调用本地 Ollama 模型生成初步问诊建议。</p>
      </div>
      <el-button :icon="Refresh" :loading="checking" @click="checkHealth">检测服务</el-button>
    </div>

    <div class="consultation-layout">
      <section class="input-panel">
        <div class="panel-title">
          <el-icon><FirstAidKit /></el-icon>
          <span>症状描述</span>
        </div>
        <el-input
          v-model="question"
          type="textarea"
          :rows="10"
          maxlength="1000"
          show-word-limit
          placeholder="例如：最近三天咳嗽、喉咙痛，晚上有低烧，需要注意什么？"
        />
        <div class="panel-actions">
          <span class="health-state">
            <el-icon><Connection /></el-icon>
            {{ healthText }}
          </span>
          <el-button type="primary" :icon="Promotion" :loading="loading" @click="submit">
            发送
          </el-button>
        </div>
      </section>

      <section class="reply-panel">
        <div class="panel-title">
          <el-icon><ChatDotRound /></el-icon>
          <span>模型回复</span>
          <small v-if="model">{{ model }}</small>
        </div>
        <div v-loading="loading" class="reply-content">
          <el-empty v-if="!reply" description="等待输入后生成预问诊回复" />
          <p v-else>{{ reply }}</p>
        </div>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.consultation-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(360px, 1.1fr);
  gap: 16px;
}

.input-panel,
.reply-panel {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 18px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-weight: 700;
}

.panel-title small {
  margin-left: auto;
  color: var(--color-muted);
  font-weight: 500;
}

.panel-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
}

.health-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-muted);
  font-size: 13px;
}

.reply-content {
  min-height: 300px;
  white-space: pre-wrap;
  line-height: 1.8;
}

.reply-content p {
  margin: 0;
}

@media (max-width: 960px) {
  .consultation-layout {
    grid-template-columns: 1fr;
  }
}
</style>
