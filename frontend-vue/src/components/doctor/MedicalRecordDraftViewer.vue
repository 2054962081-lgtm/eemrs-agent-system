<script setup lang="ts">
import { computed, ref } from 'vue'
import { Document, Refresh, CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  getLatestMedicalRecordDraft,
  type MedicalRecordDraftDetail,
} from '../../api/agent'

const props = defineProps<{
  patientId?: number | string
}>()

const visible = ref(false)
const loading = ref(false)
const draft = ref<MedicalRecordDraftDetail | null>(null)
const emptyText = ref('')
const errorText = ref('')

const canQuery = computed(() => props.patientId !== undefined && String(props.patientId).trim() !== '')
const recordJson = computed(() => draft.value?.recordJson)
const fullJson = computed(() => (recordJson.value ? JSON.stringify(recordJson.value, null, 2) : ''))
const possibleDirections = computed(() => recordJson.value?.preliminaryAssessment?.possibleDirections || [])
const suggestedExaminations = computed(() => recordJson.value?.suggestedExaminations || [])
const redFlags = computed(() => recordJson.value?.riskAssessment?.redFlags || [])
const generalAdvice = computed(() => recordJson.value?.careAdvice?.generalAdvice || [])
const missingInformation = computed(() => recordJson.value?.doctorReviewTips?.missingInformation || [])

async function loadDraft() {
  if (!canQuery.value) {
    ElMessage.warning('未选择患者')
    return
  }

  loading.value = true
  emptyText.value = ''
  errorText.value = ''
  draft.value = null
  try {
    const result = await getLatestMedicalRecordDraft(props.patientId!)
    if (!result.success) {
      errorText.value = result.message || '预问诊病历草稿查询失败，请稍后重试。'
      visible.value = true
      return
    }
    if (!result.hasDraft || !result.draft) {
      emptyText.value = result.message || '暂无预问诊病历草稿'
      visible.value = true
      return
    }
    draft.value = result.draft
    visible.value = true
  } catch {
    errorText.value = '预问诊病历草稿查询失败，请稍后重试。'
    visible.value = true
  } finally {
    loading.value = false
  }
}

async function copyJson() {
  if (!fullJson.value) {
    return
  }
  await navigator.clipboard.writeText(fullJson.value)
  ElMessage.success('完整 JSON 已复制')
}

function stringify(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '未提供'
  }
  if (Array.isArray(value)) {
    return value.length ? value.join('、') : '未提供'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value, null, 2)
  }
  return String(value)
}
</script>

<template>
  <span>
    <el-button :icon="Document" :loading="loading" :disabled="!canQuery" @click="loadDraft">
      查看预问诊病历草稿
    </el-button>
    <el-drawer v-model="visible" title="AI 预问诊草稿" size="58%">
      <el-alert
        class="notice"
        type="warning"
        show-icon
        :closable="false"
        title="该内容由智能体根据患者深度预问诊信息自动生成，仅供医生接诊参考，不能替代医生诊断，需由医生审核后方可采纳。"
      />

      <div class="drawer-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadDraft">刷新</el-button>
        <el-button :icon="CopyDocument" :disabled="!fullJson" @click="copyJson">复制完整 JSON</el-button>
      </div>

      <el-empty v-if="emptyText" :description="emptyText" />
      <el-alert v-if="errorText" type="warning" :closable="false" :title="errorText" />

      <div v-if="draft" class="draft-content">
        <el-descriptions title="基础信息" :column="2" border>
          <el-descriptions-item label="草稿 ID">{{ draft.id }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ draft.createdAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ draft.status || 'DRAFT' }}</el-descriptions-item>
          <el-descriptions-item label="来源">深度预问诊</el-descriptions-item>
          <el-descriptions-item label="会话 ID">{{ draft.sessionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="就诊优先级">{{ draft.urgency || 'normal' }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="核心病历" :column="1" border>
          <el-descriptions-item label="主诉">{{ draft.chiefComplaint || '未提供' }}</el-descriptions-item>
          <el-descriptions-item label="现病史">{{ draft.presentIllnessHistory || '未提供' }}</el-descriptions-item>
          <el-descriptions-item label="推荐科室">{{ draft.recommendedDepartment || '未提供' }}</el-descriptions-item>
          <el-descriptions-item label="深度问诊总结">{{ draft.consultationSummary || '未提供' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="draft.parseError"
          class="notice"
          type="warning"
          :closable="false"
          title="结构化 JSON 解析失败，以下仅展示数据库中保存的基础字段和原始 record_json。"
        />

        <el-collapse v-if="recordJson" class="sections">
          <el-collapse-item title="患者基础信息" name="patient">
            <pre>{{ stringify(recordJson.patientBasicInfo) }}</pre>
          </el-collapse-item>
          <el-collapse-item title="主诉与现病史" name="illness">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="主诉">{{ stringify(recordJson.chiefComplaint) }}</el-descriptions-item>
              <el-descriptions-item label="现病史">{{ stringify(recordJson.presentIllnessHistory) }}</el-descriptions-item>
            </el-descriptions>
          </el-collapse-item>
          <el-collapse-item title="既往史、用药史、过敏史" name="history">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="既往史">{{ stringify(recordJson.pastHistory) }}</el-descriptions-item>
              <el-descriptions-item label="用药史">{{ stringify(recordJson.medicationHistory) }}</el-descriptions-item>
              <el-descriptions-item label="过敏史">{{ stringify(recordJson.allergyHistory) }}</el-descriptions-item>
            </el-descriptions>
          </el-collapse-item>
          <el-collapse-item title="风险评估" name="risk">
            <div class="tag-list">
              <el-tag v-for="(item, index) in redFlags" :key="index" type="danger">{{ item }}</el-tag>
              <span v-if="!redFlags.length">未提供危险信号</span>
            </div>
            <p>{{ stringify(recordJson.riskAssessment?.emergencyAdvice) }}</p>
          </el-collapse-item>
          <el-collapse-item title="可能相关方向" name="directions">
            <div v-if="possibleDirections.length" class="item-list">
              <div v-for="(item, index) in possibleDirections" :key="index">
                <strong>{{ item.name || '未提供' }}</strong>
                <span>：{{ item.basis || '依据未提供' }}</span>
              </div>
            </div>
            <span v-else>未提供</span>
          </el-collapse-item>
          <el-collapse-item title="建议检查" name="exam">
            <div class="tag-list">
              <el-tag v-for="(item, index) in suggestedExaminations" :key="index">{{ stringify(item) }}</el-tag>
              <span v-if="!suggestedExaminations.length">未提供</span>
            </div>
          </el-collapse-item>
          <el-collapse-item title="居家和就医建议" name="care">
            <div v-if="generalAdvice.length" class="item-list">
              <div v-for="(item, index) in generalAdvice" :key="index">{{ item }}</div>
            </div>
            <p>{{ stringify(recordJson.careAdvice?.followUpAdvice) }}</p>
          </el-collapse-item>
          <el-collapse-item title="医生复核提示" name="review">
            <div v-if="missingInformation.length" class="item-list">
              <div v-for="(item, index) in missingInformation" :key="index">{{ item }}</div>
            </div>
            <span v-else>未提供</span>
          </el-collapse-item>
          <el-collapse-item title="完整 JSON" name="json">
            <pre>{{ fullJson }}</pre>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-drawer>
  </span>
</template>

<style scoped>
.notice,
.drawer-actions,
.draft-content,
.sections {
  margin-top: 12px;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.draft-content {
  display: grid;
  gap: 16px;
}

.tag-list,
.item-list {
  display: grid;
  gap: 8px;
}

.tag-list {
  grid-template-columns: repeat(auto-fit, minmax(120px, max-content));
}

pre {
  max-height: 320px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  background: #0f172a;
  border-radius: 8px;
  color: #e2e8f0;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
