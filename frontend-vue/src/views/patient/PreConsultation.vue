<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ChatDotRound, Connection, Document, FirstAidKit, Promotion, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageContainer from '../../components/PageContainer.vue'
import { useAuthStore } from '../../stores/auth'
import {
  generateMedicalRecordDraft,
  getAgentHealth,
  sendPreConsultationMessage,
  type AgentMessage,
  type MedicalRecordDraftGenerateResponse,
  type PreConsultationResponse,
} from '../../api/agent'
import {
  appendShortTermQuestionAnswer,
  completeShortTermMemorySession,
  createShortTermMemorySession,
  getMemoryContext,
  type MemoryContext,
} from '../../api/memory'

type ConsultationMode = 'quick' | 'deep'

interface ChatMessage extends AgentMessage {
  pending?: boolean
}

const selectedMode = ref<ConsultationMode | ''>('')
const sessionId = ref('')
const question = ref('')
const messages = ref<ChatMessage[]>([])
const model = ref('')
const loading = ref(false)
const checking = ref(false)
const draftLoading = ref(false)
const healthText = ref('未检测')
const errorText = ref('')
const memoryText = ref('')
const draftErrorText = ref('')
const finished = ref(false)
const round = ref(0)
const consultationConclusion = ref('')
const draftResult = ref<MedicalRecordDraftGenerateResponse | null>(null)
const chatBodyRef = ref<HTMLElement | null>(null)
const authStore = useAuthStore()

const modeText = computed(() => (selectedMode.value === 'quick' ? '快速问诊' : '深度问诊'))
const statusText = computed(() => {
  if (selectedMode.value === 'quick') {
    return `第 ${Math.min(Math.max(round.value + 1, 1), 3)} / 3 轮`
  }
  return '结构化问诊中'
})
const canGenerateDraft = computed(() => (
  selectedMode.value === 'deep'
  && finished.value
  && Boolean(consultationConclusion.value)
  && messages.value.some((message) => message.role === 'user' && message.content.trim())
))
const draftRecord = computed(() => draftResult.value?.record)
const possibleDirections = computed(() => draftRecord.value?.preliminaryAssessment?.possibleDirections || [])
const redFlags = computed(() => draftRecord.value?.riskAssessment?.redFlags || [])
const missingInformation = computed(() => draftRecord.value?.doctorReviewTips?.missingInformation || [])
const generalAdvice = computed(() => draftRecord.value?.careAdvice?.generalAdvice || [])
const fullDraftJson = computed(() => (draftRecord.value ? JSON.stringify(draftRecord.value, null, 2) : ''))

function createSessionId() {
  return `pre-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function scrollToBottom() {
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  })
}

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

async function runMemoryTask(task: () => Promise<unknown>) {
  try {
    await task()
  } catch {
    memoryText.value = ''
  }
}

function selectMode(mode: ConsultationMode) {
  selectedMode.value = mode
  resetConversation(false)
}

function resetDraftState() {
  draftErrorText.value = ''
  consultationConclusion.value = ''
  draftResult.value = null
}

function resetConversation(keepMode = true) {
  const mode = keepMode ? selectedMode.value : selectedMode.value
  sessionId.value = createSessionId()
  question.value = ''
  messages.value = []
  model.value = ''
  errorText.value = ''
  memoryText.value = ''
  finished.value = false
  round.value = 0
  resetDraftState()
  selectedMode.value = mode
}

function switchMode() {
  selectedMode.value = ''
  resetConversation()
}

async function submit(customQuestion?: string) {
  if (!selectedMode.value || loading.value || finished.value) {
    return
  }

  const value = (customQuestion ?? question.value).trim()
  if (!value) {
    return
  }

  const isSummaryRequest = selectedMode.value === 'deep' && Boolean(customQuestion)
  const nextRound = selectedMode.value === 'quick' ? Math.min(round.value + 1, 3) : round.value + 1
  const history = messages.value
    .filter((message) => !message.pending)
    .map(({ role, content }) => ({ role, content }))

  messages.value.push({ role: 'user', content: value })
  question.value = ''
  loading.value = true
  errorText.value = ''
  draftErrorText.value = ''
  scrollToBottom()

  try {
    await runMemoryTask(() => createShortTermMemorySession(sessionId.value))
    let memoryContext: MemoryContext | undefined
    await runMemoryTask(async () => {
      memoryContext = await getMemoryContext(sessionId.value, value)
    })
    const result: PreConsultationResponse = await sendPreConsultationMessage({
      mode: selectedMode.value,
      sessionId: sessionId.value,
      question: value,
      round: nextRound,
      history,
      memoryContext,
    })

    model.value = result.model
    round.value = result.round || nextRound
    finished.value = Boolean(result.finished)
    if (!result.success) {
      errorText.value = result.error || result.reply || '智能体服务暂时不可用，请稍后再试。'
      ElMessage.warning(errorText.value)
    }
    const assistantReply = result.reply || '本次未返回有效内容，请稍后重试。'
    messages.value.push({
      role: 'assistant',
      content: assistantReply,
    })
    if (isSummaryRequest && result.success) {
      consultationConclusion.value = assistantReply
    }
    await runMemoryTask(async () => {
      await appendShortTermQuestionAnswer(sessionId.value, {
        question: value,
        answer: assistantReply,
        round: round.value,
        temporaryConclusion: isSummaryRequest ? assistantReply : undefined,
      })
      memoryText.value = '已参考近期记忆'
    })
    if (finished.value) {
      await runMemoryTask(() => completeShortTermMemorySession(sessionId.value, {
        summary: isSummaryRequest ? assistantReply : undefined,
        department: result.recommendedDepartment,
        sourceId: sessionId.value,
      }))
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : ''
    errorText.value = message.includes('timeout')
      ? 'AI 回复生成时间较长，请稍后重试；如使用云端模型，请检查网络、模型额度和后端超时配置。'
      : 'AI 预问诊请求失败，请稍后重试。'
    messages.value.push({ role: 'assistant', content: errorText.value })
    ElMessage.warning(errorText.value)
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function finishDeepConsultation() {
  submit('请根据以上信息生成深度问诊总结和科室建议')
}

async function generateDraft() {
  if (!canGenerateDraft.value || draftLoading.value) {
    return
  }

  draftLoading.value = true
  draftErrorText.value = ''
  draftResult.value = null
  try {
    const history = messages.value.map(({ role, content }) => ({ role, content }))
    const result = await generateMedicalRecordDraft({
      sessionId: sessionId.value,
      patientIdNumber: authStore.idNumber,
      mode: 'deep',
      consultationConclusion: consultationConclusion.value,
      history,
    })
    if (!result.success) {
      draftErrorText.value = result.error || result.message || '病历草稿生成失败，请稍后重试。'
      ElMessage.warning(draftErrorText.value)
      return
    }
    draftResult.value = result
    ElMessage.success('病历草稿生成成功')
  } catch (error) {
    const message = error instanceof Error ? error.message : ''
    draftErrorText.value = message.includes('timeout')
      ? '病历草稿生成时间较长，请稍后重试。'
      : '病历草稿生成失败，请稍后重试。'
    ElMessage.warning(draftErrorText.value)
  } finally {
    draftLoading.value = false
  }
}

async function copyDraftJson() {
  if (!fullDraftJson.value) {
    return
  }
  await navigator.clipboard.writeText(fullDraftJson.value)
  ElMessage.success('病历草稿 JSON 已复制')
}

onMounted(() => {
  sessionId.value = createSessionId()
  checkHealth()
})
</script>

<template>
  <PageContainer>
    <div class="toolbar">
      <div>
        <h2 class="page-title">AI 预问诊</h2>
        <p class="page-subtitle">选择问诊模式后，系统将调用后端配置的模型服务进行预问诊分诊建议。</p>
      </div>
      <el-button :icon="Refresh" :loading="checking" @click="checkHealth">检测服务</el-button>
    </div>

    <section v-if="!selectedMode" class="mode-panel">
      <div class="mode-heading">
        <h3>请选择问诊模式</h3>
        <span class="health-state">
          <el-icon><Connection /></el-icon>
          {{ healthText }}
        </span>
      </div>
      <div class="mode-grid">
        <button class="mode-card" type="button" @click="selectMode('quick')">
          <span class="mode-icon"><FirstAidKit /></span>
          <strong>快速问诊</strong>
          <span>适合快速描述症状，3 轮内给出初步推荐科室。</span>
        </button>
        <button class="mode-card" type="button" @click="selectMode('deep')">
          <span class="mode-icon"><ChatDotRound /></span>
          <strong>深度问诊</strong>
          <span>适合较复杂或不明确的情况，系统将更全面地了解病情并给出分析建议。</span>
        </button>
      </div>
    </section>

    <section v-else class="chat-panel">
      <div class="chat-header">
        <div>
          <div class="panel-title">
            <el-icon><ChatDotRound /></el-icon>
            <span>{{ modeText }}</span>
            <small v-if="model">{{ model }}</small>
          </div>
          <p>{{ statusText }}</p>
          <p v-if="memoryText">{{ memoryText }}</p>
        </div>
        <div class="header-actions">
          <el-button @click="resetConversation()">重新开始</el-button>
          <el-button @click="switchMode">切换模式</el-button>
        </div>
      </div>

      <div ref="chatBodyRef" v-loading="loading" class="chat-body">
        <el-empty v-if="messages.length === 0" description="请先描述最主要的不适、持续时间和希望解决的问题。" />
        <div
          v-for="(message, index) in messages"
          :key="`${message.role}-${index}`"
          class="message-row"
          :class="message.role"
        >
          <div class="message-bubble">
            {{ message.content }}
          </div>
        </div>
      </div>

      <el-alert v-if="errorText" class="chat-alert" type="warning" :closable="false" :title="errorText" />
      <el-alert
        v-if="selectedMode === 'quick' && finished"
        class="chat-alert"
        type="success"
        :closable="false"
        title="本次快速问诊已完成，可重新开始或切换深度问诊。"
      />
      <el-alert
        v-if="selectedMode === 'deep' && finished"
        class="chat-alert"
        type="info"
        :closable="false"
        title="深度问诊总结已生成。可继续生成预问诊病历草稿，该草稿需由医生审核确认。"
      />

      <div v-if="selectedMode === 'deep'" class="deep-actions">
        <el-button :disabled="loading || messages.length === 0 || finished" @click="finishDeepConsultation">
          结束并生成总结
        </el-button>
        <el-button
          type="primary"
          :icon="Document"
          :loading="draftLoading"
          :disabled="!canGenerateDraft"
          @click="generateDraft"
        >
          生成病历草稿
        </el-button>
      </div>

      <el-alert
        v-if="draftErrorText"
        class="chat-alert"
        type="warning"
        :closable="false"
        :title="draftErrorText"
      />

      <div class="composer">
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          :disabled="loading || finished"
          placeholder="请描述症状、持续时间、严重程度、伴随症状等信息"
          @keydown.ctrl.enter.prevent="submit()"
        />
        <el-button
          type="primary"
          :icon="Promotion"
          :loading="loading"
          :disabled="finished"
          @click="submit()"
        >
          发送
        </el-button>
      </div>
    </section>

    <section v-if="draftRecord" class="draft-panel">
      <div class="draft-header">
        <div class="panel-title">
          <el-icon><Document /></el-icon>
          <span>病历草稿</span>
          <small v-if="draftResult?.draftId">Draft ID: {{ draftResult.draftId }}</small>
        </div>
        <el-button @click="copyDraftJson">复制 JSON</el-button>
      </div>

      <el-alert
        class="draft-notice"
        type="warning"
        :closable="false"
        title="该内容为智能体根据预问诊信息生成的病历草稿，仅供医生参考，不能替代医生诊断，需由医生审核确认后方可作为正式病历。"
      />

      <el-descriptions :column="1" border>
        <el-descriptions-item label="主诉">
          {{ draftRecord.chiefComplaint?.text || '未提供' }}
        </el-descriptions-item>
        <el-descriptions-item label="现病史">
          {{ JSON.stringify(draftRecord.presentIllnessHistory || {}, null, 2) }}
        </el-descriptions-item>
        <el-descriptions-item label="推荐科室">
          {{ draftRecord.visitInfo?.recommendedDepartment?.primary || '未提供' }}
        </el-descriptions-item>
        <el-descriptions-item label="就诊优先级">
          {{ draftRecord.visitInfo?.urgency?.level || 'normal' }}
        </el-descriptions-item>
        <el-descriptions-item label="可能相关方向">
          <div v-if="possibleDirections.length" class="inline-list">
            <span v-for="(item, index) in possibleDirections" :key="index">
              {{ item.name || '未提供' }}：{{ item.basis || '依据未提供' }}
            </span>
          </div>
          <span v-else>未提供</span>
        </el-descriptions-item>
        <el-descriptions-item label="危险信号">
          <div v-if="redFlags.length" class="inline-list">
            <span v-for="(item, index) in redFlags" :key="index">{{ item }}</span>
          </div>
          <span v-else>未提供</span>
        </el-descriptions-item>
        <el-descriptions-item label="建议进一步确认的信息">
          <div v-if="missingInformation.length" class="inline-list">
            <span v-for="(item, index) in missingInformation" :key="index">{{ item }}</span>
          </div>
          <span v-else>未提供</span>
        </el-descriptions-item>
        <el-descriptions-item label="居家和就医建议">
          <div v-if="generalAdvice.length" class="inline-list">
            <span v-for="(item, index) in generalAdvice" :key="index">{{ item }}</span>
          </div>
          <span v-else>{{ draftRecord.careAdvice?.followUpAdvice || '未提供' }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-collapse class="draft-json">
        <el-collapse-item title="完整 JSON" name="json">
          <pre>{{ fullDraftJson }}</pre>
        </el-collapse-item>
      </el-collapse>
    </section>
  </PageContainer>
</template>

<style scoped>
.mode-panel,
.chat-panel,
.draft-panel {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: var(--shadow-card);
  padding: 18px;
}

.draft-panel {
  margin-top: 16px;
}

.mode-heading,
.chat-header,
.draft-header,
.panel-title,
.header-actions,
.health-state,
.composer,
.deep-actions {
  display: flex;
  align-items: center;
}

.mode-heading,
.chat-header,
.draft-header {
  justify-content: space-between;
  gap: 16px;
}

.mode-heading h3 {
  margin: 0;
}

.health-state,
.chat-header p,
.panel-title small {
  color: var(--color-muted);
  font-size: 13px;
}

.health-state {
  gap: 6px;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.mode-card {
  display: grid;
  gap: 10px;
  min-height: 150px;
  padding: 18px;
  text-align: left;
  background: #f8fafc;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: var(--color-text);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.mode-card:hover {
  border-color: var(--color-primary);
  box-shadow: 0 10px 24px rgb(15 23 42 / 10%);
  transform: translateY(-1px);
}

.mode-card strong {
  font-size: 18px;
}

.mode-card span:last-child {
  color: var(--color-muted);
  line-height: 1.7;
}

.mode-icon {
  width: 38px;
  height: 38px;
  color: var(--color-primary);
}

.panel-title {
  gap: 8px;
  font-weight: 700;
}

.panel-title small {
  margin-left: 8px;
  font-weight: 500;
}

.chat-header p {
  margin: 6px 0 0;
}

.header-actions {
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.chat-body {
  height: 430px;
  overflow-y: auto;
  margin-top: 16px;
  padding: 16px;
  background: #f8fafc;
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.message-row {
  display: flex;
  margin-bottom: 12px;
}

.message-row.user {
  justify-content: flex-end;
}

.message-bubble {
  max-width: min(760px, 86%);
  padding: 12px 14px;
  border-radius: 8px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-row.user .message-bubble {
  background: var(--color-primary);
  color: #fff;
}

.message-row.assistant .message-bubble {
  background: #fff;
  border: 1px solid var(--color-border);
  color: var(--color-text);
}

.chat-alert,
.deep-actions,
.composer,
.draft-notice,
.draft-json {
  margin-top: 12px;
}

.deep-actions {
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.composer {
  gap: 12px;
}

.composer .el-button {
  align-self: stretch;
  min-width: 96px;
}

.inline-list {
  display: grid;
  gap: 6px;
  white-space: pre-wrap;
}

.draft-json pre {
  max-height: 360px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  background: #0f172a;
  border-radius: 8px;
  color: #e2e8f0;
  line-height: 1.6;
}

@media (max-width: 860px) {
  .mode-grid {
    grid-template-columns: 1fr;
  }

  .chat-header,
  .draft-header,
  .composer {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions,
  .deep-actions {
    justify-content: flex-start;
  }
}
</style>
