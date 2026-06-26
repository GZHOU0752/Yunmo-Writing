<script setup>
import { computed } from 'vue'

const props = defineProps({
  diagnosis: { type: Object, default: null },
  size: { type: String, default: 'default' }, // small | default | large
})

const scoreColor = computed(() => {
  if (!props.diagnosis) return 'var(--yunmo-border)'
  const score = props.diagnosis.aiScore || 0
  if (score < 20) return 'var(--yunmo-green)'
  if (score < 40) return 'var(--yunmo-amber)'
  if (score < 60) return 'var(--yunmo-gold)'
  return 'var(--yunmo-red)'
})

const severityLabel = computed(() => {
  if (!props.diagnosis) return 'N/A'
  const s = props.diagnosis.overallSeverity
  if (s === 'PASS') return '通过'
  if (s === 'WARN') return '警告'
  if (s === 'FAIL') return '严重'
  return s || 'N/A'
})

const severityBg = computed(() => {
  if (!props.diagnosis) return 'transparent'
  const s = props.diagnosis.overallSeverity
  if (s === 'PASS') return 'rgba(90,122,90,0.08)'
  if (s === 'WARN') return 'rgba(184,150,74,0.1)'
  return 'rgba(179,68,58,0.08)'
})

const gateSummary = computed(() => {
  if (!props.diagnosis) return []
  const gates = props.diagnosis.gateResults || []
  return gates.filter(g => g.severity !== 'PASS').slice(0, 3)
})

const sizeClass = computed(() => ({
  small: 'text-xs px-2 py-0.5',
  default: 'text-sm px-3 py-1',
  large: 'text-base px-4 py-2',
}[props.size]))
</script>

<template>
  <div v-if="diagnosis" class="yunmo-card p-3">
    <!-- 总分 -->
    <div class="flex items-center justify-between mb-2">
      <span class="text-xs font-medium" style="color: var(--yunmo-text-caption)">去AI味评分</span>
      <div class="flex items-center gap-2">
        <span class="font-bold font-tabular text-lg" :style="{ color: scoreColor }">
          {{ Math.round(diagnosis.aiScore || 0) }}
        </span>
        <span class="text-xs opacity-60" style="color: var(--yunmo-text-caption)">/100</span>
      </div>
    </div>

    <!-- 进度条 -->
    <div class="h-1.5 rounded-full mb-3 overflow-hidden" style="background: var(--yunmo-paper-dark)">
      <div
        class="h-full rounded-full transition-all duration-500"
        :style="{
          width: Math.min(100 - (diagnosis.aiScore || 0), 100) + '%',
          background: scoreColor,
        }"
      />
    </div>

    <!-- 综合判定 -->
    <div
      class="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium mb-2"
      :style="{ background: severityBg, color: scoreColor }"
    >
      <span class="w-1.5 h-1.5 rounded-full" :style="{ background: scoreColor }" />
      {{ severityLabel }}
    </div>

    <!-- Gate 明细 -->
    <div v-if="gateSummary.length" class="space-y-1 mt-2 pt-2 border-t" style="border-color: var(--yunmo-border)">
      <div
        v-for="gate in gateSummary" :key="gate.name"
        class="flex items-center justify-between text-xs"
      >
        <span style="color: var(--yunmo-text-caption)">{{ gate.name }}</span>
        <span
          class="font-medium"
          :style="{
            color: gate.severity === 'WARN' ? 'var(--yunmo-amber)' : 'var(--yunmo-red)',
          }"
        >{{ gate.severity === 'WARN' ? '⚠' : '✗' }} {{ gate.score }}</span>
      </div>
    </div>

    <!-- 6项指标 -->
    <div v-if="diagnosis.metrics" class="grid grid-cols-2 gap-1 mt-2 pt-2 border-t" style="border-color: var(--yunmo-border)">
      <div class="text-xs flex justify-between" style="color: var(--yunmo-text-caption)">
        <span>禁用词密度</span>
        <span :style="{ color: diagnosis.metrics.bannedWordDensity > 5 ? 'var(--yunmo-red)' : 'var(--yunmo-green)' }">
          {{ diagnosis.metrics.bannedWordDensity || 0 }}/千字
        </span>
      </div>
      <div class="text-xs flex justify-between" style="color: var(--yunmo-text-caption)">
        <span>段落CV值</span>
        <span :style="{ color: diagnosis.metrics.paragraphCV < 0.15 ? 'var(--yunmo-red)' : 'var(--yunmo-green)' }">
          {{ (diagnosis.metrics.paragraphCV || 0).toFixed(2) }}
        </span>
      </div>
      <div class="text-xs flex justify-between" style="color: var(--yunmo-text-caption)">
        <span>心理词占比</span>
        <span :style="{ color: diagnosis.metrics.psychWordRatio > 8 ? 'var(--yunmo-red)' : 'var(--yunmo-green)' }">
          {{ diagnosis.metrics.psychWordRatio || 0 }}/千字
        </span>
      </div>
      <div class="text-xs flex justify-between" style="color: var(--yunmo-text-caption)">
        <span>对话标签率</span>
        <span :style="{ color: diagnosis.metrics.dialogTagRatio > 50 ? 'var(--yunmo-red)' : 'var(--yunmo-green)' }">
          {{ diagnosis.metrics.dialogTagRatio || 0 }}%
        </span>
      </div>
    </div>
  </div>

  <!-- 无数据 -->
  <div v-else class="yunmo-card p-3 text-center">
    <div class="text-2xl mb-1 opacity-30">🔍</div>
    <p class="text-xs" style="color: var(--yunmo-text-caption)">生成后将自动检测AI痕迹</p>
  </div>
</template>
