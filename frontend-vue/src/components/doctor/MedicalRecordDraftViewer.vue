<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, Close, CopyDocument, Document, Edit, Refresh, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  acceptMedicalRecordDraft,
  applyMedicalRecordDraft,
  getLatestMedicalRecordDraft,
  getMedicalRecordDraftHistory,
  partialAcceptMedicalRecordDraft,
  rejectMedicalRecordDraft,
  saveMedicalRecordDraftEdit,
  type MedicalRecordDraftAuditLog,
  type MedicalRecordDraftDetail,
} from '../../api/agent'

const props = defineProps<{
  patientId?: number | string
}>()

const visible = ref(false)
const loading = ref(false)
const actionLoading = ref(false)
const draft = ref<MedicalRecordDraftDetail | null>(null)
const history = ref<MedicalRecordDraftAuditLog[]>([])
const editorText = ref('')
const rejectReason = ref('')
const comment = ref('')
const emptyText = ref('')
const errorText = ref('')

const canQuery = computed(() => props.patientId !== undefined && String(props.patientId).trim() !== '')
const aiRecordJson = computed(() => draft.value?.aiRecordJson || draft.value?.recordJson || {})
const aiJsonText = computed(() => JSON.stringify(aiRecordJson.value, null, 2))
const currentStatus = computed(() => draft.value?.status || 'GENERATED')
const editable = computed(() => ['GENERATED', 'REVIEWING'].includes(currentStatus.value))
const canApply = computed(() => ['ACCEPTED', 'PARTIALLY_ACCEPTED'].includes(currentStatus.value))
const locked = computed(() => ['REJECTED', 'APPLIED'].includes(currentStatus.value))
const diffItems = computed(() => diffJson(aiRecordJson.value, parsedEditor.value))
const parsedEditor = computed(() => {
  try {
    return editorText.value ? JSON.parse(editorText.value) : {}
  } catch {
    return null
  }
})

async function loadDraft() {
  if (!canQuery.value) {
    ElMessage.warning('未选择患者')
    return
  }
  loading.value = true
  emptyText.value = ''
  errorText.value = ''
  draft.value = null
  history.value = []
  try {
    const result = await getLatestMedicalRecordDraft(props.patientId!)
    if (!result.success) {
      errorText.value = result.message || '预问诊病历草稿查询失败'
      visible.value = true
      return
    }
    if (!result.hasDraft || !result.draft) {
      emptyText.value = result.message || '暂无预问诊病历草稿'
      visible.value = true
      return
    }
    setDraft(result.draft)
    await loadHistory()
    visible.value = true
  } catch {
    errorText.value = '预问诊病历草稿查询失败，请确认登录医生有权限查看该患者'
    visible.value = true
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  if (!draft.value?.id) return
  try {
    const result = await getMedicalRecordDraftHistory(draft.value.id)
    history.value = result.logs || []
  } catch {
    history.value = []
  }
}

function setDraft(next: MedicalRecordDraftDetail) {
  draft.value = next
  editorText.value = JSON.stringify(next.editedRecordJson || next.recordJson || {}, null, 2)
}

async function copyJson() {
  await navigator.clipboard.writeText(editorText.value)
  ElMessage.success('当前编辑稿 JSON 已复制')
}

async function saveEdit() {
  const payload = requireValidEditor()
  if (!payload || !draft.value) return
  actionLoading.value = true
  try {
    const result = await saveMedicalRecordDraftEdit(draft.value.id, payload, comment.value)
    setDraft(result.draft)
    await loadHistory()
    ElMessage.success('修改已保存')
  } finally {
    actionLoading.value = false
  }
}

async function acceptDraft() {
  if (!draft.value) return
  const payload = requireValidEditor()
  if (!payload) return
  actionLoading.value = true
  try {
    const result = await acceptMedicalRecordDraft(draft.value.id, payload, comment.value)
    setDraft(result.draft)
    await loadHistory()
    ElMessage.success(result.idempotent ? '草稿已采纳' : '已完全采纳')
  } finally {
    actionLoading.value = false
  }
}

async function partialAcceptDraft() {
  if (!draft.value) return
  const payload = requireValidEditor()
  if (!payload) return
  actionLoading.value = true
  try {
    const result = await partialAcceptMedicalRecordDraft(draft.value.id, payload, comment.value)
    setDraft(result.draft)
    await loadHistory()
    ElMessage.success('已修改后采纳')
  } finally {
    actionLoading.value = false
  }
}

async function rejectDraft() {
  if (!draft.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('拒绝原因必填')
    return
  }
  actionLoading.value = true
  try {
    const payload = parsedEditor.value || undefined
    const result = await rejectMedicalRecordDraft(draft.value.id, rejectReason.value.trim(), payload, comment.value)
    setDraft(result.draft)
    await loadHistory()
    ElMessage.success('已拒绝草稿')
  } finally {
    actionLoading.value = false
  }
}

async function applyDraft() {
  if (!draft.value) return
  await ElMessageBox.confirm('确认将已采纳草稿写入正式病历？该操作会调用正式病历接口。', '写入正式病历', {
    type: 'warning',
  })
  actionLoading.value = true
  try {
    const result = await applyMedicalRecordDraft(draft.value.id, {})
    setDraft(result.draft)
    await loadHistory()
    ElMessage.success(result.idempotent ? '该草稿此前已写入' : '已写入正式病历')
  } finally {
    actionLoading.value = false
  }
}

function requireValidEditor() {
  if (!editable.value) {
    ElMessage.warning('当前状态不允许编辑')
    return null
  }
  if (!parsedEditor.value) {
    ElMessage.error('编辑稿不是合法 JSON')
    return null
  }
  return parsedEditor.value
}

function diffJson(before: unknown, after: unknown) {
  if (!after || typeof after !== 'object') {
    return []
  }
  const beforeFlat = flatten(before)
  const afterFlat = flatten(after)
  const keys = Array.from(new Set([...Object.keys(beforeFlat), ...Object.keys(afterFlat)])).sort()
  return keys
    .filter((key) => JSON.stringify(beforeFlat[key]) !== JSON.stringify(afterFlat[key]))
    .map((key) => ({ key, before: stringify(beforeFlat[key]), after: stringify(afterFlat[key]) }))
}

function flatten(value: unknown, prefix = '', output: Record<string, unknown> = {}) {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    Object.entries(value as Record<string, unknown>).forEach(([key, item]) => {
      flatten(item, prefix ? `${prefix}.${key}` : key, output)
    })
    return output
  }
  output[prefix] = value
  return output
}

function stringify(value: unknown) {
  if (value === null || value === undefined || value === '') return '未提供'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
</script>

<template>
  <span>
    <el-button :icon="Document" :loading="loading" :disabled="!canQuery" @click="loadDraft">
      查看预问诊病历草稿
    </el-button>
    <el-drawer v-model="visible" title="AI 病历草稿审核" size="72%">
      <el-alert
        class="block"
        type="warning"
        show-icon
        :closable="false"
        title="AI 草稿仅供接诊参考，采纳或写入正式病历前必须由医生审核确认。"
      />

      <el-empty v-if="emptyText" :description="emptyText" />
      <el-alert v-if="errorText" type="warning" :closable="false" :title="errorText" />

      <div v-if="draft" class="draft-shell">
        <el-descriptions title="审核状态" :column="3" border>
          <el-descriptions-item label="草稿 ID">{{ draft.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag>{{ currentStatus }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="模型">{{ draft.modelName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ draft.createdAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="首次审核">{{ draft.firstReviewedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="写入时间">{{ draft.appliedAt || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="toolbar">
          <el-button :icon="Refresh" :loading="loading" @click="loadDraft">刷新</el-button>
          <el-button :icon="CopyDocument" @click="copyJson">复制编辑稿</el-button>
          <el-button :icon="Edit" :disabled="!editable" :loading="actionLoading" @click="saveEdit">保存修改</el-button>
          <el-button type="success" :icon="Check" :disabled="!editable" :loading="actionLoading" @click="acceptDraft">
            完全采纳
          </el-button>
          <el-button type="primary" :disabled="!editable" :loading="actionLoading" @click="partialAcceptDraft">
            修改后采纳
          </el-button>
          <el-button type="danger" :icon="Close" :disabled="!editable" :loading="actionLoading" @click="rejectDraft">
            拒绝
          </el-button>
          <el-button type="warning" :icon="Upload" :disabled="!canApply" :loading="actionLoading" @click="applyDraft">
            写入正式病历
          </el-button>
        </div>

        <el-alert v-if="locked" class="block" type="info" :closable="false" title="该草稿已结束审核，当前状态不允许继续编辑。" />

        <div class="review-grid">
          <section>
            <h3>AI 原始草稿</h3>
            <pre>{{ aiJsonText }}</pre>
          </section>
          <section>
            <h3>医生编辑稿</h3>
            <el-input
              v-model="editorText"
              type="textarea"
              :rows="24"
              :disabled="!editable"
              spellcheck="false"
            />
          </section>
        </div>

        <el-form label-width="92px">
          <el-form-item label="审核备注">
            <el-input v-model="comment" :disabled="locked" placeholder="可选" />
          </el-form-item>
          <el-form-item label="拒绝原因">
            <el-input v-model="rejectReason" :disabled="!editable" placeholder="拒绝时必填" />
          </el-form-item>
        </el-form>

        <el-descriptions title="修改差异" :column="1" border>
          <el-descriptions-item label="修改字段数">{{ diffItems.length }}</el-descriptions-item>
          <el-descriptions-item label="字段名称">
            <el-tag v-for="item in diffItems" :key="item.key" class="tag">{{ item.key }}</el-tag>
            <span v-if="!diffItems.length">无修改</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-table v-if="diffItems.length" :data="diffItems" size="small">
          <el-table-column prop="key" label="字段" min-width="180" />
          <el-table-column prop="before" label="原值" min-width="220" show-overflow-tooltip />
          <el-table-column prop="after" label="新值" min-width="220" show-overflow-tooltip />
        </el-table>

        <h3>审核历史</h3>
        <el-timeline>
          <el-timeline-item v-for="item in history" :key="item.id" :timestamp="item.actionTime || ''">
            <strong>{{ item.action }}</strong>
            <span v-if="item.rejectReason">：{{ item.rejectReason }}</span>
            <span v-else-if="item.comment">：{{ item.comment }}</span>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </span>
</template>

<style scoped>
.block,
.toolbar,
.draft-shell,
.review-grid,
.tag {
  margin-top: 12px;
}

.draft-shell {
  display: grid;
  gap: 16px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.review-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}

h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

pre {
  height: 540px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  background: #111827;
  border-radius: 6px;
  color: #e5e7eb;
  line-height: 1.5;
  white-space: pre-wrap;
}

@media (max-width: 960px) {
  .review-grid {
    grid-template-columns: 1fr;
  }
}
</style>
