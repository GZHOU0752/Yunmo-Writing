<script setup>
import { computed } from 'vue'

const props = defineProps({
  diagnosis: { type: Object, default: null },
  size: { type: String, default: 'default' },
})

const scoreColor = computed(() => {
  if (!props.diagnosis) return 'var(--yunmo-border)'
  const score = props.diagnosis.aiScore || 0
  if (score < 20) return 'var(--yunmo-green)'
  if (score < 40) return 'var(--yunmo-amber)'
  if (score < 60) return 'var(--yunmo-gold)'
  return 'var(--yunmo-red)'
})

/** 温和化的判定标签 */
const severityLabel = computed(() => {
  if (!props.diagnosis) return '等待检测'
  const s = props.diagnosis.overallSeverity
  if (s === 'PASS') return '自然流畅'
  if (s === 'WARN') return '略有痕迹'
  if (s === 'FAIL') return '痕迹明显'
  return s || '等待检测'
})

const severityBg = computed(() => {
  if (!props.diagnosis) return 'transparent'
  const s = props.diagnosis.overallSeverity
  if (s === 'PASS') return 'rgba(90,122,90,0.08)'
  if (s === 'WARN') return 'rgba(184,150,74,0.1)'
  return 'rgba(179,68,58,0.08)'
})

/** Gate名称通俗化映射 */
const gateNameMap = {
  'Gate A - 禁用词检测': 'AI高频词',
  'Gate B - 句式套路检测': '句式套路',
  'Gate C - 心理外化检测': '心理描写',
  'Gate D - 节奏检测': '段落节奏',
  'Gate E - 对话检测': '对话自然度',
  'Gate F - 结尾检测': '结尾处理',
  'Gate G - 解释腔检测': '解释腔调',
}

function friendlyGateName(name) {
  return gateNameMap[name] || name
}

/** 有问题的Gate（非PASS），最多展示5项 */
const gateSummary = computed(() => {
  if (!props.diagnosis) return []
  const gates = props.diagnosis.gateResults || []
  return gates.filter(g => g.severity !== 'PASS').slice(0, 5)
})

/** 得分等级描述 */
const scoreLevel = computed(() => {
  if (!props.diagnosis) return ''
  const s = props.diagnosis.aiScore || 0
  if (s < 15) return '文字很有个人风格'
  if (s < 30) return '整体自然，偶有AI腔'
  if (s < 50) return '部分段落需润色'
  if (s < 70) return 'AI痕迹较明显'
  return '建议重点修改'
})
</script>

<template>
  <div v-if="diagnosis" class="yunmo-card p-3">
    <!-- 标题行 -->
    <div class="flex items-center justify-between mb-2">
      <span class="text-xs font-medium" style="color: var(--yunmo-text-caption)">AI检测</span>
      <span class="text-[10px]" style="color: var(--yunmo-text-caption)">越低越自然</span>
    </div>

    <!-- 总分 -->
    <div class="flex items-center justify-between mb-1">
      <span class="font-bold font-tabular text-lg" :style="{ color: scoreColor }">
        {{ Math.round(diagnosis.aiScore || 0) }}
      </span>
      <span class="text-xs opacity-60" style="color: var(--yunmo-text-caption)">/100 分</span>
    </div>

    <!-- 进度条 -->
    <div class="h-1.5 rounded-full mb-2 overflow-hidden" style="background: var(--yunmo-paper-dark)">
      <div
        class="h-full rounded-full transition-all duration-500"
        :style="{
          width: Math.min(diagnosis.aiScore || 0, 100) + '%',
          background: scoreColor,
        }"
      />
    </div>

    <!-- 综合判定 + 一句话描述 -->
    <div class="flex items-center gap-2 mb-3">
      <div
        class="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium"
        :style="{ background: severityBg, color: scoreColor }"
      >
        <span class="w-1.5 h-1.5 rounded-full" :style="{ background: scoreColor }" />
        {{ severityLabel }}
      </div>
      <span class="text-[11px]" style="color: var(--yunmo-text-caption)">{{ scoreLevel }}</span>
    </div>

    <!-- 检测项明细 -->
    <div v-if="gateSummary.length" class="space-y-1.5 mt-2 pt-2 border-t" style="border-color: var(--yunmo-border)">
      <div class="text-[10px] mb-1" style="color: var(--yunmo-text-caption)">以下方面可改进：</div>
      <div
        v-for="gate in gateSummary" :key="gate.name"
        class="flex items-center justify-between text-xs"
      >
        <span style="color: var(--yunmo-text-caption)">{{ friendlyGateName(gate.name) }}</span>
        <span
          class="font-medium text-[11px]"
          :style="{
            color: gate.severity === 'WARN' ? 'var(--yunmo-amber)' : 'var(--yunmo-red)',
          }"
        >
          {{ gate.severity === 'WARN' ? '注意' : '需改' }}
        </span>
      </div>
    </div>

    <!-- 客观指标 -->
    <div v-if="diagnosis.metrics" class="grid grid-cols-2 gap-1.5 mt-2 pt-2 border-t" style="border-color: var(--yunmo-border)">
      <div class="text-[11px] flex justify-between" style="color: var(--yunmo-text-caption)">
        <span>AI高频词</span>
        <span :style="{ color: (diagnosis.metrics.level1Density || 0) > 5 ? 'var(--yunmo-red)' : 'var(--yunmo-green)' }">
          {{ (diagnosis.metrics.level1Density || 0).toFixed(1) }}/千字
        </span>
      </div>
      <div class="text-[11px] flex justify-between" style="color: var(--yunmo-text-caption)">
        <span>句式套路</span>
        <span :style="{ color: (diagnosis.metrics.fatalSentenceHits || 0) > 3 ? 'var(--yunmo-red)' : 'var(--yunmo-green)' }">
          {{ diagnosis.metrics.fatalSentenceHits || 0 }}处
        </span>
      </div>
      <div class="text-[11px] flex justify-between" style="color: var(--yunmo-text-caption)">
        <span>心理描写</span>
        <span :style="{ color: (diagnosis.metrics.psychDensity || 0) > 8 ? 'var(--yunmo-red)' : 'var(--yunmo-green)' }">
          {{ (diagnosis.metrics.psychDensity || 0).toFixed(1) }}/千字
        </span>
      </div>
      <div class="text-[11px] flex justify-between" style="color: var(--yunmo-text-caption)">
        <span>段落节奏</span>
        <span :style="{ color: (diagnosis.metrics.paragraphCV || 0) < 0.15 ? 'var(--yunmo-red)' : 'var(--yunmo-green)' }">
          {{ (diagnosis.metrics.paragraphCV || 0).toFixed(2) }}
        </span>
      </div>
      <div class="text-[11px] flex justify-between" style="color: var(--yunmo-text-caption)">
        <span>对话标签</span>
        <span :style="{ color: (diagnosis.metrics.dialogTagRatio || 0) > 50 ? 'var(--yunmo-red)' : 'var(--yunmo-green)' }">
          {{ (diagnosis.metrics.dialogTagRatio || 0).toFixed(0) }}%
        </span>
      </div>
      <div class="text-[11px] flex justify-between" style="color: var(--yunmo-text-caption)">
        <span>解释腔调</span>
        <span :style="{ color: (diagnosis.metrics.explanationDensity || 0) > 6 ? 'var(--yunmo-red)' : 'var(--yunmo-green)' }">
          {{ (diagnosis.metrics.explanationDensity || 0).toFixed(1) }}/千字
        </span>
      </div>
    </div>
  </div>

  <!-- 无数据 -->
  <div v-else class="yunmo-card p-3 text-center">
    <div class="mb-1 opacity-30 flex justify-center">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><circle cx="11" cy="11" r="7"/><path d="M16.5 16.5L21 21"/></svg>
    </div>
    <p class="text-xs" style="color: var(--yunmo-text-caption)">生成章节后将自动进行AI检测</p>
  </div>
</template>
