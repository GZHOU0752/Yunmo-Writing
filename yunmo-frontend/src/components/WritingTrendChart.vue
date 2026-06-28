<script setup>
import { ref, watch, computed } from 'vue'
import { useApi } from '@/composables/useApi'

const props = defineProps({ novelId: String })
const api = useApi()
const history = ref([])
const loading = ref(false)

const maxCount = computed(() => {
  if (history.value.length === 0) return 2000
  return Math.max(2000, ...history.value.map(h => h.wordCount))
})

// 7 天移动平均
const movingAvg = computed(() => {
  const arr = history.value
  if (arr.length < 7) return []
  const result = []
  for (let i = 6; i < arr.length; i++) {
    let sum = 0
    for (let j = i - 6; j <= i; j++) sum += arr[j].wordCount
    result.push({ date: arr[i].date, avg: Math.round(sum / 7) })
  }
  return result
})

async function fetchHistory() {
  if (!props.novelId) return
  loading.value = true
  try {
    history.value = await api.stats.history(props.novelId, 30) || []
  } catch (e) {
    console.error('加载写作历史失败:', e)
  } finally {
    loading.value = false
  }
}

function barHeight(wc) {
  if (maxCount.value <= 0) return 1
  return Math.max(2, Math.round((wc / maxCount.value) * 80))
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  return dateStr.substring(5) // "MM-DD"
}

watch(() => props.novelId, fetchHistory, { immediate: true })
</script>

<template>
  <div class="mt-3 pt-3" style="border-top:1px solid var(--yunmo-border)">
    <div class="flex items-center justify-between mb-2">
      <span class="text-xs font-semibold" style="color:var(--yunmo-accent)">30 天趋势</span>
      <span v-if="loading" class="text-[10px]">加载中...</span>
    </div>
    <div v-if="history.length > 0" class="flex items-end gap-px justify-center" style="height:90px">
      <div
        v-for="(h, i) in history"
        :key="i"
        class="flex flex-col items-center group"
        style="flex:1"
      >
        <!-- Bar -->
        <div class="flex-1 w-full flex items-end justify-center">
          <div
            class="w-3/4 transition-all duration-300"
            :style="{
              height: barHeight(h.wordCount) + 'px',
              background: h.wordCount >= h.targetWordCount
                ? 'var(--yunmo-accent)'
                : h.wordCount > 0
                  ? 'var(--yunmo-text-secondary)'
                  : 'var(--yunmo-border)',
              opacity: h.wordCount > 0 ? 0.85 : 0.3,
              borderRadius: '2px 2px 0 0',
            }"
            :title="h.date + '　' + h.wordCount.toLocaleString() + ' 字'"
          />
        </div>
        <!-- Date label (every 5th) -->
        <span
          v-if="i % 5 === 0"
          class="text-[8px] mt-1"
          style="color:var(--yunmo-text-caption); transform: rotate(-45deg); transform-origin: left top"
        >{{ formatDate(h.date) }}</span>
      </div>
    </div>
    <!-- 移动平均线 -->
    <div v-if="movingAvg.length > 0" class="mt-2 flex items-center gap-2 text-[10px]">
      <span>7日均线</span>
      <span class="font-tabular" style="color:var(--yunmo-accent)">
        {{ movingAvg[movingAvg.length - 1]?.avg?.toLocaleString() || 0 }} 字/天
      </span>
    </div>
    <div v-if="!loading && history.length === 0" class="text-xs text-caption py-4 text-center">
      暂无历史数据
    </div>
  </div>
</template>
