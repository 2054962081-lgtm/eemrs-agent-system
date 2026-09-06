<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ChatDotRound, Connection, FirstAidKit, Promotion, Refresh } from '@element-plus/icons-vue'
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
import { createAgentAppointment } from '../../api/appointment'
import { getDoctorsByDepartment } from '../../api/doctor'
import {
  appendShortTermQuestionAnswer,
  completeShortTermMemorySession,
  createShortTermMemorySession,
  getMemoryContext,
  type MemoryContext,
} from '../../api/memory'
import type { AgentAppointmentResponse, DoctorInfo } from '../../api/types'

type ConsultationMode = 'quick' | 'deep'
type ChatMessageType =
  | 'text'
  | 'triage_result'
  | 'doctor_list'
  | 'registration_success'
  | 'error'

interface ChatMessage extends AgentMessage {
  id?: string
  type?: ChatMessageType
  pending?: boolean
  department?: string
  doctors?: DoctorInfo[]
  selectedDoctor?: DoctorInfo
  appointment?: AgentAppointmentResponse
  handled?: boolean
  loadingDoctors?: boolean
}

interface SubmitOptions {
  displayUserMessage?: boolean
  displayAssistantReply?: boolean
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
const queryingDoctorMessageId = ref('')
const registeringDoctorId = ref('')
const registrationCompleted = ref(false)
const lastRecommendedDepartment = ref('')
const registrationEnding = ref(false)

const modeText = computed(() => (selectedMode.value === 'quick' ? '快速问诊' : '深度问诊'))
const statusText = computed(() => {
  if (selectedMode.value === 'quick') {
    return `第 ${Math.min(Math.max(round.value + 1, 1), 3)} / 3 轮`
  }
  return '结构化问诊中'
})
const canShowAgentRegistration = computed(() => (
  selectedMode.value === 'deep'
  && messages.value.some((message) => message.role === 'user' && message.content.trim())
  && !registrationCompleted.value
))

function createSessionId() {
  return `pre-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function createMessageId(prefix = 'msg') {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function getDoctorId(doctor: DoctorInfo) {
  return doctor.doctorId || doctor.idNumber
}

function getDoctorName(doctor?: DoctorInfo) {
  return doctor?.doctorName || doctor?.userName || '未提供'
}

function getDoctorTitle(doctor: DoctorInfo) {
  return doctor.title || doctor.position || '暂无职称信息'
}

function getDoctorSpecialty(doctor: DoctorInfo) {
  return doctor.specialty || doctor.introduction || '暂无简介'
}

function normalizeDepartment(department?: string) {
  return (department || '')
    .replace(/^优先建议[:：]?/, '')
    .replace(/^建议优先就诊/, '')
    .replace(/^[:：\s]+/, '')
    .replace(/[。；;，,].*$/, '')
    .trim()
}

function extractRecommendedDepartmentFromReply(reply?: string) {
  if (!reply) {
    return ''
  }
  const lines = reply.split(/\r?\n/)
  const headingIndex = lines.findIndex((line) => line.includes('推荐科室'))
  const candidates = headingIndex >= 0 ? lines.slice(headingIndex + 1, headingIndex + 5) : lines
  for (const line of candidates) {
    const normalized = normalizeDepartment(line)
    if (normalized && /科|门诊|医学/.test(normalized)) {
      return normalized
    }
  }
  const inline = reply.match(/(?:推荐科室|优先建议|建议优先就诊)[:：]?\s*([^\n。；;，,]+)/)
  return normalizeDepartment(inline?.[1])
}

function getLatestAssistantText() {
  return [...messages.value]
    .reverse()
    .find((message) => message.role === 'assistant' && (!message.type || message.type === 'text' || message.type === 'triage_result'))
    ?.content
    .trim() || ''
}

function getDraftRecommendedDepartment() {
  return normalizeDepartment(draftResult.value?.record?.visitInfo?.recommendedDepartment?.primary)
}

function formatAppointmentTime(value?: number | string) {
  if (!value) {
    return '已由系统记录'
  }
  const raw = String(value).trim()
  let date: Date
  if (/^\d{14}$/.test(raw)) {
    date = new Date(
      Number(raw.slice(0, 4)),
      Number(raw.slice(4, 6)) - 1,
      Number(raw.slice(6, 8)),
      Number(raw.slice(8, 10)),
      Number(raw.slice(10, 12)),
      Number(raw.slice(12, 14)),
    )
  } else if (/^\d+$/.test(raw)) {
    date = new Date(Number(raw))
  } else {
    date = new Date(raw)
  }
  if (Number.isNaN(date.getTime())) {
    return raw
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

function getDepartmentCandidates(department: string) {
  const normalized = normalizeDepartment(department)
  const candidates = [normalized]
  if (normalized && normalized !== '内科' && normalized.endsWith('内科')) {
    candidates.push('内科')
  }
  return [...new Set(candidates.filter(Boolean))]
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
  queryingDoctorMessageId.value = ''
  registeringDoctorId.value = ''
  registrationCompleted.value = false
  lastRecommendedDepartment.value = ''
  registrationEnding.value = false
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

async function submit(customQuestion?: string, options: SubmitOptions = {}) {
  if (!selectedMode.value || loading.value || finished.value) {
    return null
  }

  const value = (customQuestion ?? question.value).trim()
  if (!value) {
    return null
  }

  const displayUserMessage = options.displayUserMessage !== false
  const displayAssistantReply = options.displayAssistantReply !== false
  const isSummaryRequest = selectedMode.value === 'deep' && Boolean(customQuestion)
  const nextRound = selectedMode.value === 'quick' ? Math.min(round.value + 1, 3) : round.value + 1
  const history = messages.value
    .filter((message) => !message.pending && (!message.type || message.type === 'text' || message.type === 'triage_result'))
    .map(({ role, content }) => ({ role, content }))

  if (displayUserMessage) {
    messages.value.push({ role: 'user', content: value })
    question.value = ''
  }
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
    const recommendedDepartment = normalizeDepartment(result.recommendedDepartment)
      || extractRecommendedDepartmentFromReply(assistantReply)
    if (recommendedDepartment) {
      lastRecommendedDepartment.value = recommendedDepartment
    }
    if (displayAssistantReply) {
      messages.value.push({
        role: 'assistant',
        type: result.finished ? 'triage_result' : 'text',
        content: assistantReply,
      })
    }
    if ((isSummaryRequest || result.finished) && result.success) {
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
        department: recommendedDepartment || result.recommendedDepartment,
        sourceId: sessionId.value,
      }))
    }
    if (result.success && selectedMode.value === 'deep' && result.finished) {
      await generateDraft()
    }
    return result
  } catch (error) {
    const message = error instanceof Error ? error.message : ''
    errorText.value = message.includes('timeout')
      ? 'AI 回复生成时间较长，请稍后重试；如使用云端模型，请检查网络、模型额度和后端超时配置。'
      : 'AI 预问诊请求失败，请稍后重试。'
    messages.value.push({ role: 'assistant', type: 'error', content: errorText.value })
    ElMessage.warning(errorText.value)
    return null
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

async function startAgentRegistration() {
  if (
    selectedMode.value !== 'deep'
    || registrationEnding.value
    || loading.value
    || queryingDoctorMessageId.value
    || registeringDoctorId.value
    || registrationCompleted.value
  ) {
    return
  }

  registrationEnding.value = true
  try {
    if (!finished.value) {
      finished.value = true
      const latestConclusion = getLatestAssistantText()
      consultationConclusion.value = latestConclusion || '用户点击一键挂号结束深度问诊。'
      const department = normalizeDepartment(lastRecommendedDepartment.value)
        || extractRecommendedDepartmentFromReply(consultationConclusion.value)
      await runMemoryTask(() => completeShortTermMemorySession(sessionId.value, {
        summary: consultationConclusion.value,
        department,
        sourceId: sessionId.value,
      }))
    }
    await generateDraft()

    const department = normalizeDepartment(lastRecommendedDepartment.value)
      || extractRecommendedDepartmentFromReply(consultationConclusion.value)
      || getDraftRecommendedDepartment()
    if (!department) {
      ElMessage.warning('暂未识别到推荐科室，请补充症状后再试。')
      return
    }
    await queryDoctorsForRegistration({
      id: createMessageId('registration-action'),
      role: 'assistant',
      type: 'text',
      content: '',
      department,
    })
  } finally {
    registrationEnding.value = false
  }
}

async function queryDoctorsForRegistration(message: ChatMessage) {
  const department = normalizeDepartment(message.department)
  if (!department || message.handled || queryingDoctorMessageId.value) {
    return
  }

  message.handled = true
  message.loadingDoctors = true
  queryingDoctorMessageId.value = message.id || 'registration-confirm'
  messages.value.push({
    id: createMessageId('doctor-querying'),
    role: 'assistant',
    type: 'text',
    content: `正在为您查询 ${department} 医生...`,
    department,
  })
  scrollToBottom()

  try {
    let matchedDepartment = department
    let doctors: DoctorInfo[] = []
    for (const candidate of getDepartmentCandidates(department)) {
      doctors = await getDoctorsByDepartment(candidate)
      if (doctors.length) {
        matchedDepartment = candidate
        break
      }
    }
    if (!doctors.length) {
      messages.value.push({
        id: createMessageId('doctor-empty'),
        role: 'assistant',
        type: 'text',
        content: '当前推荐科室暂无可挂号医生，请稍后再试或前往挂号页面手动选择。',
        department,
      })
      return
    }
    messages.value.push({
      id: createMessageId('doctor-list'),
      role: 'assistant',
      type: 'doctor_list',
      content: `${matchedDepartment} 医生列表`,
      department: matchedDepartment,
      doctors,
    })
  } catch (error) {
    const messageText = error instanceof Error ? error.message : '医生列表查询失败'
    messages.value.push({
      id: createMessageId('doctor-query-error'),
      role: 'assistant',
      type: 'error',
      content: `医生列表查询失败：${messageText}`,
      department,
    })
  } finally {
    message.loadingDoctors = false
    queryingDoctorMessageId.value = ''
    scrollToBottom()
  }
}

async function registerDoctor(department: string | undefined, doctor: DoctorInfo) {
  const targetDepartment = normalizeDepartment(department)
  const doctorId = getDoctorId(doctor)
  if (!targetDepartment || !doctorId || registeringDoctorId.value || registrationCompleted.value) {
    return
  }

  registeringDoctorId.value = doctorId
  try {
    const appointment = await createAgentAppointment({
      department: targetDepartment,
      doctorId,
      source: 'DEEP_INQUIRY',
    })
    registrationCompleted.value = true
    messages.value.push({
      id: createMessageId('registration-success'),
      role: 'assistant',
      type: 'registration_success',
      content: '挂号成功',
      department: targetDepartment,
      selectedDoctor: doctor,
      appointment,
    })
  } catch (error) {
    const messageText = error instanceof Error ? error.message : '挂号失败，请稍后再试'
    messages.value.push({
      id: createMessageId('registration-error'),
      role: 'assistant',
      type: 'error',
      content: `挂号失败：${messageText}`,
      department: targetDepartment,
      selectedDoctor: doctor,
    })
  } finally {
    registeringDoctorId.value = ''
    scrollToBottom()
  }
}

async function generateDraft() {
  if (
    selectedMode.value !== 'deep'
    || !finished.value
    || !consultationConclusion.value
    || draftLoading.value
    || draftResult.value?.success
  ) {
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
            <template v-if="message.type === 'doctor_list'">
              <div class="doctor-list-title">{{ message.department }} 医生列表</div>
              <div class="doctor-card-list">
                <div v-for="doctor in message.doctors" :key="getDoctorId(doctor)" class="doctor-card">
                  <div class="doctor-card-main">
                    <strong>{{ getDoctorName(doctor) }}</strong>
                    <span>{{ doctor.department || message.department }}</span>
                    <span>{{ getDoctorTitle(doctor) }}</span>
                    <p>{{ getDoctorSpecialty(doctor) }}</p>
                  </div>
                  <el-button
                    type="primary"
                    size="small"
                    :loading="registeringDoctorId === getDoctorId(doctor)"
                    :disabled="registrationCompleted || Boolean(registeringDoctorId)"
                    @click="registerDoctor(message.department, doctor)"
                  >
                    选择并挂号
                  </el-button>
                </div>
              </div>
            </template>

            <template v-else-if="message.type === 'registration_success'">
              <div class="success-title">挂号成功</div>
              <el-descriptions :column="1" size="small" border>
                <el-descriptions-item label="推荐科室">{{ message.department || message.appointment?.department }}</el-descriptions-item>
                <el-descriptions-item label="已选择医生">
                  {{ message.appointment?.doctorName || getDoctorName(message.selectedDoctor) }}
                </el-descriptions-item>
                <el-descriptions-item label="挂号状态">{{ message.appointment?.status || '待就诊' }}</el-descriptions-item>
                <el-descriptions-item label="挂号时间">
                  {{ formatAppointmentTime(message.appointment?.visitTime) }}
                </el-descriptions-item>
              </el-descriptions>
            </template>

            <template v-else>
              {{ message.content }}
            </template>
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
        title="深度问诊已结束。"
      />

      <div v-if="selectedMode === 'deep'" class="deep-actions">
        <el-button
          v-if="canShowAgentRegistration"
          type="success"
          :loading="registrationEnding || draftLoading || Boolean(queryingDoctorMessageId)"
          :disabled="loading || Boolean(registeringDoctorId)"
          @click="startAgentRegistration"
        >
          一键挂号
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

  </PageContainer>
</template>

<style scoped>
.mode-panel,
.chat-panel {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: var(--shadow-card);
  padding: 18px;
}

.mode-heading,
.chat-header,
.panel-title,
.header-actions,
.health-state,
.composer,
.deep-actions {
  display: flex;
  align-items: center;
}

.mode-heading,
.chat-header {
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

.doctor-list-title,
.success-title {
  margin-bottom: 10px;
  font-weight: 700;
}

.doctor-card-list {
  display: grid;
  gap: 10px;
}

.doctor-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.doctor-card-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.doctor-card-main span,
.doctor-card-main p {
  margin: 0;
  color: var(--color-muted);
  font-size: 13px;
  line-height: 1.6;
}

.chat-alert,
.deep-actions,
.composer {
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

@media (max-width: 860px) {
  .mode-grid {
    grid-template-columns: 1fr;
  }

  .chat-header,
  .composer,
  .doctor-card {
    align-items: stretch;
    flex-direction: column;
  }

  .doctor-card {
    display: flex;
  }

  .header-actions,
  .deep-actions {
    justify-content: flex-start;
  }
}
</style>
