<script setup>
import { ref, watch } from 'vue'
import { useApi } from '@/composables/useApi'

const props = defineProps({ novelId: String })
const api = useApi()
const stats = ref(null)
const loading = ref(false)
let abortController = null

async function fetchStats() {
  if (!props.novelId) return
  if (abortController) abortController.abort()
  abortController = new AbortController()
  loading.value = true
  try { stats.value = await api.stats.overview(props.novelId) } catch {}
  finally { loading.value = false; abortController = null }
}

const todayWordCount = () => stats.value?.today?.wordCount ?? 0
const todayTarget = () => stats.value?.today?.targetWordCount ?? 2000
const progressPct = () => {
  const target = todayTarget()
  if (target <= 0) return 0
  return Math.min(100, Math.round((todayWordCount() / target) * 100))
}

const weekDays = ['一', '二', '三', '四', '五', '六', '日']

/** 墨色深浅 — 根据字数与目标比例 */
function inkDepth(dayIndex) {
  const wc = stats.value?.weekDetail?.[dayIndex]?.wordCount || 0
  const target = stats.value?.weekDetail?.[dayIndex]?.targetWordCount || 2000
  if (wc === 0) return 'transparent'
  const ratio = Math.min(1, wc / target)
  if (ratio >= 1) return 'var(--yunmo-ink)'
  if (ratio >= 0.6) return 'var(--yunmo-text-secondary)'
  if (ratio >= 0.3) return 'var(--yunmo-accent-light)'
  return 'var(--yunmo-border)'
}

watch(() => props.novelId, fetchStats, { immediate: true })
</script>

<template>
  <div v-if="stats" class="p-3 text-sm" style="background:var(--yunmo-paper-dark);border-radius:4px">
    <div class="flex items-center justify-between mb-3">
      <span class="text-xs font-semibold tracking-wider" style="color:var(--yunmo-accent)">今日墨迹</span>
      <span class="text-[10px] cursor-pointer" style="color:var(--yunmo-text-caption)" @click="fetchStats">刷新</span>
    </div>

    <!-- 墨条进度 -->
    <div class="mb-3">
      <div class="flex justify-between items-baseline mb-1">
        <span class="text-lg font-semibold font-brush" style="color:var(--yunmo-ink)">{{ todayWordCount().toLocaleString() }}</span>
        <span class="text-xs" style="color:var(--yunmo-text-caption)">/ {{ todayTarget().toLocaleString() }} 字</span>
      </div>
      <div class="h-1.5" style="background:var(--yunmo-paper)">
        <div
          class="h-1.5 transition-all duration-700 ease-out"
          :style="{
            width: progressPct() + '%',
            background: progressPct() >= 100
              ? 'linear-gradient(90deg, var(--yunmo-accent), var(--yunmo-gold))'
              : 'var(--yunmo-ink)'
          }"
        />
      </div>
    </div>

    <!-- 七日墨染 -->
    <div class="flex gap-1 justify-between">
      <div v-for="(day, i) in weekDays" :key="i" class="flex flex-col items-center flex-1">
        <span class="text-[10px] mb-1" style="color:var(--yunmo-text-caption)">{{ day }}</span>
        <div
          class="w-5 h-5 transition-colors duration-300"
          :style="{
            background: inkDepth(i),
            borderRadius: '50%',
            opacity: inkDepth(i) === 'transparent' ? 0 : 1,
            border: inkDepth(i) === 'transparent' ? '1px dashed var(--yunmo-border)' : 'none',
          }"
          :title="((stats.weekDetail?.[i]?.wordCount || 0) / 1000).toFixed(1) + 'k'"
        />
      </div>
    </div>

    <div class="flex justify-between mt-3 pt-2 text-[10px] tracking-wider"
         style="border-top:1px solid var(--yunmo-border);color:var(--yunmo-text-caption)">
      <span>本周 {{ (stats.weekTotal || 0).toLocaleString() }} 字</span>
      <span>本月 {{ (stats.monthTotal || 0).toLocaleString() }} 字</span>
    </div>
  </div>
</template>
