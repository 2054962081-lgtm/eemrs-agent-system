<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { DataAnalysis, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  analyzeReportTrend,
  type ReportTrendAnalysisResponse,
  type ReportTrendItem,
} from '../api/agent'

const props = withDefaults(defineProps<{
  patientId: string | number
  role: 'patient' | 'doctor'
  sessionId?: string
}>(), {
  sessionId: '',
})

const loading = ref(false)
const result = ref<ReportTrendAnalysisResponse | null>(null)
const friendlyError = ref('')
const form = reactive({
  dateRange: [
    new Date(new Date().setMonth(new Date().getMonth() - 6)).toISOString().slice(0, 10),
    new Date().toISOString().slice(0, 10),
  ] as string[],
  targetItems: 'WBC,CRP,NEUT_PERCENT,ALT,AST,CREA,GLU,HBA1C',
  includePreconsultationContext: true,
  includeLongTermHealthContext: true,
})

const isDoctor = computed(() => props.role === 'doctor')
const trendItems = computed(() => result.value?.trendItems || [])
const abnormalItems = computed(() => result.value?.abnormalItems || [])
const contextLinks = computed(() => result.value?.contextLinks || [])
const followUpQuestions = computed(() => result.value?.followUpQuestions || [])

function mapError(response?: ReportTrendAnalysisResponse | null) {
  const code = response?.errorCode
  if (code === 'REPORT_NOT_FOUND' || code === 'INSUFFICIENT_REPORT_DATA') {
    return '报告数据不足，暂无法生成趋势分析'
  }
  if (code === 'REPORT_PARSE_FAILED' || code === 'REPORT_DECRYPT_FAILED') {
    return '报告解析失败，请联系医生查看原始报告'
  }
  if (code === 'CLOUD_PAYLOAD_PRIVACY_VIOLATION') {
    return '上下文隐私检查未通过，系统已停止生成分析'
  }
  if (code === 'CLOUD_MODEL_FAILED' || code === 'CLOUD_RESPONSE_INVALID') {
    return '系统暂时无法生成分析，请稍后重试'
  }
  return '系统暂时无法生成分析，请稍后重试'
}

function splitTargets() {
  return form.targetItems
    .split(',')
    .map((item) => item.trim().toUpperCase())
    .filter(Boolean)
}

async function runAnalysis() {
  if (!props.patientId || loading.value) {
    return
  }
  loading.value = true
  friendlyError.value = ''
  result.value = null
  try {
    const response = await analyzeReportTrend({
      patientId: props.patientId,
      sessionId: props.sessionId || undefined,
      includePreconsultationContext: form.includePreconsultationContext,
      includeLongTermHealthContext: form.includeLongTermHealthContext,
      reportType: 'LAB',
      startDate: form.dateRange?.[0],
      endDate: form.dateRange?.[1],
      targetItems: splitTargets(),
      outputMode: 'DOCTOR_AND_PATIENT',
    })
    result.value = response
    if (response.status !== 'SUCCESS') {
      friendlyError.value = mapError(response)
      if (isDoctor.value && response.errorCode) {
        friendlyError.value += `（${response.errorCode}）`
      }
      ElMessage.warning(friendlyError.value)
      return
    }
    ElMessage.success('报告纵向分析已生成')
  } catch (error) {
    friendlyError.value = error instanceof Error && error.message.includes('timeout')
      ? '分析生成时间较长，请稍后重试'
      : '系统暂时无法生成分析，请稍后重试'
    ElMessage.warning(friendlyError.value)
  } finally {
    loading.value = false
  }
}

function trendText(item: ReportTrendItem) {
  const previous = item.previousValue ?? '-'
  const latest = item.latestValue ?? '-'
  const direction = item.trendDirection || 'UNKNOWN'
  const flag = item.latestAbnormalFlag || 'UNKNOWN'
  return `${item.name || item.code}：${previous} → ${latest}，趋势 ${direction}，本次 ${flag}`
}
</script>

<template>
  <section class="report-trend-panel">
    <div class="panel-head">
      <div>
        <h3>
          <el-icon><DataAnalysis /></el-icon>
          报告纵向分析
        </h3>
        <p>{{ isDoctor ? '结合检验趋势与脱敏预问诊上下文，辅助接诊前快速浏览。' : '结合近期报告趋势生成通俗解释，结果仅供就医沟通参考。' }}</p>
      </div>
      <el-button type="primary" :icon="Refresh" :loading="loading" @click="runAnalysis">生成纵向分析</el-button>
    </div>

    <el-form class="trend-form" :model="form" inline>
      <el-form-item label="时间范围">
        <el-date-picker v-model="form.dateRange" type="daterange" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item label="指标">
        <el-input v-model="form.targetItems" class="target-input" />
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="form.includePreconsultationContext">预问诊上下文</el-checkbox>
        <el-checkbox v-model="form.includeLongTermHealthContext">长期健康档案</el-checkbox>
      </el-form-item>
    </el-form>

    <el-alert v-if="friendlyError" type="warning" :closable="false" :title="friendlyError" />

    <div v-if="result?.status === 'SUCCESS'" class="analysis-content">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="分析编号">{{ result.analysisId }}</el-descriptions-item>
        <el-descriptions-item label="反馈编号">{{ result.traceRunId }}</el-descriptions-item>
        <el-descriptions-item label="建议科室">{{ result.recommendation?.suggestedDepartment || '-' }}</el-descriptions-item>
        <el-descriptions-item label="上下文使用">
          预问诊 {{ result.contextUsed?.preconsultation ? '已使用' : '未使用' }}，
          长期档案 {{ result.contextUsed?.longTermHealth ? '已使用' : '未使用' }}，
          分诊 {{ result.contextUsed?.triage ? '已使用' : '未使用' }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="isDoctor && result.doctorSummary" class="summary-block">
        <h4>医生端摘要</h4>
        <p>{{ result.doctorSummary }}</p>
      </div>

      <div v-if="result.patientExplanation" class="summary-block">
        <h4>患者端解释</h4>
        <p>{{ result.patientExplanation }}</p>
      </div>

      <div v-if="result.contextualInterpretation" class="summary-block">
        <h4>结合症状提示</h4>
        <p>{{ result.contextualInterpretation }}</p>
      </div>

      <div class="list-grid">
        <div>
          <h4>异常指标</h4>
          <el-empty v-if="!abnormalItems.length" description="暂无异常指标" />
          <el-tag v-for="item in abnormalItems" v-else :key="item.standardCode || item.rawName" class="tag-item" type="warning">
            {{ item.standardName || item.rawName }} {{ item.abnormalFlag }}
          </el-tag>
        </div>

        <div>
          <h4>趋势变化</h4>
          <el-empty v-if="!trendItems.length" description="暂无趋势指标" />
          <ul v-else class="compact-list">
            <li v-for="item in trendItems" :key="item.code">{{ trendText(item) }}</li>
          </ul>
        </div>
      </div>

      <div v-if="contextLinks.length" class="summary-block">
        <h4>报告-症状关联提示</h4>
        <ul class="compact-list">
          <li v-for="(link, index) in contextLinks" :key="index">
            {{ link.note || '已生成关联提示' }}
          </li>
        </ul>
      </div>

      <div v-if="followUpQuestions.length" class="summary-block">
        <h4>建议进一步询问的问题</h4>
        <ul class="compact-list">
          <li v-for="question in followUpQuestions" :key="question">{{ question }}</li>
        </ul>
      </div>

      <div v-if="result.recommendation?.suggestedAction" class="summary-block">
        <h4>建议行动</h4>
        <p>{{ result.recommendation.suggestedAction }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.report-trend-panel {
  margin-top: 16px;
  padding: 16px;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: var(--shadow-card);
}

.panel-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.panel-head h3 {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 0;
  font-size: 18px;
}

.panel-head p {
  margin: 6px 0 0;
  color: var(--color-muted);
  line-height: 1.6;
}

.trend-form {
  margin-top: 14px;
}

.target-input {
  width: 320px;
}

.analysis-content {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.summary-block h4,
.list-grid h4 {
  margin: 0 0 8px;
  font-size: 15px;
}

.summary-block p {
  margin: 0;
  line-height: 1.8;
}

.list-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr);
  gap: 14px;
}

.tag-item {
  margin: 0 8px 8px 0;
}

.compact-list {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 18px;
  line-height: 1.7;
}

@media (max-width: 860px) {
  .panel-head,
  .list-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .target-input {
    width: 100%;
  }
}
</style>
