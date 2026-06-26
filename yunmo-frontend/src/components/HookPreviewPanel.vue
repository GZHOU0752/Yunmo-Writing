<script setup>
import { computed } from 'vue'

const props = defineProps({
  hookSelection: { type: Object, default: null },
})

const openingLabel = computed(() => {
  if (!props.hookSelection?.openingHook) return null
  const h = props.hookSelection.openingHook
  return `${h.chineseName}（${h.formulaNumber}式）`
})

const closingLabel = computed(() => {
  if (!props.hookSelection?.closingHook) return null
  const h = props.hookSelection.closingHook
  return `${h.chineseName}（${h.formulaNumber}式）`
})

const intensityColor = computed(() => {
  const intensity = props.hookSelection?.suspenseIntensity || 1
  if (intensity >= 4) return 'var(--yunmo-red)'
  if (intensity >= 3) return 'var(--yunmo-gold)'
  return 'var(--yunmo-amber)'
})

const intensityLabel = computed(() => {
  const intensity = props.hookSelection?.suspenseIntensity || 1
  return ['', '好奇级', '关切级', '迫切级', '生存级', '终极级'][intensity] || '好奇级'
})

const arcContextLines = computed(() => {
  if (!props.hookSelection?.arcContext) return []
  return props.hookSelection.arcContext.split('\n').filter(Boolean).slice(0, 6)
})
</script>

<template>
  <div class="yunmo-card p-4">
    <div class="flex items-center gap-2 mb-3">
      <span class="text-lg">🪝</span>
      <h4 class="font-semibold text-sm" style="color: var(--yunmo-ink)">钩子编排</h4>
    </div>

    <div v-if="hookSelection" class="space-y-3">
      <!-- 章首引子 -->
      <div class="p-2 rounded" style="background: var(--yunmo-paper-dark)">
        <div class="text-xs font-medium mb-1" style="color: var(--yunmo-accent)">📖 章首引子</div>
        <div class="text-sm font-semibold" style="color: var(--yunmo-ink)">{{ openingLabel }}</div>
        <div class="text-xs mt-1 leading-relaxed" style="color: var(--yunmo-text-caption)">
          {{ hookSelection.openingHook?.techniqueDescription }}
        </div>
      </div>

      <!-- 章尾钩子 -->
      <div class="p-2 rounded" style="background: var(--yunmo-paper-dark)">
        <div class="text-xs font-medium mb-1" style="color: var(--yunmo-accent)">🏁 章尾钩子</div>
        <div class="text-sm font-semibold" style="color: var(--yunmo-ink)">{{ closingLabel }}</div>
        <div class="text-xs mt-1 leading-relaxed" style="color: var(--yunmo-text-caption)">
          {{ hookSelection.closingHook?.techniqueDescription }}
        </div>
      </div>

      <!-- 悬念强度 -->
      <div class="flex items-center gap-2">
        <span class="text-xs" style="color: var(--yunmo-text-caption)">悬念强度</span>
        <div class="flex gap-0.5">
          <span
            v-for="i in 5" :key="i"
            class="w-3 h-1.5 rounded"
            :style="{
              background: i <= (hookSelection.suspenseIntensity || 1) ? intensityColor : 'var(--yunmo-border)',
            }"
          />
        </div>
        <span class="text-xs font-medium" :style="{ color: intensityColor }">{{ intensityLabel }}</span>
      </div>

      <!-- 跨章悬念弧 -->
      <div v-if="arcContextLines.length" class="pt-2 border-t" style="border-color: var(--yunmo-border)">
        <div class="text-xs font-medium mb-1" style="color: var(--yunmo-text-caption)">📐 跨章悬念弧</div>
        <div class="text-xs space-y-0.5" style="color: var(--yunmo-text-caption)">
          <div v-for="(line, i) in arcContextLines" :key="i">· {{ line }}</div>
        </div>
      </div>
    </div>

    <!-- 无数据 -->
    <div v-else class="text-center py-6">
      <div class="text-3xl mb-2 opacity-30">🪝</div>
      <p class="text-xs" style="color: var(--yunmo-text-caption)">
        生成时将自动编排章首引子与章尾钩子
      </p>
    </div>
  </div>
</template>
