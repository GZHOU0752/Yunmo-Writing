<script setup>
import { computed } from 'vue'

const props = defineProps({
  card: { type: Object, default: () => null },
  loading: { type: Boolean, default: false },
})

const sections = computed(() => {
  if (!props.card) return []
  return [
    { label: '章节身份', value: props.card.chapterIdentity, icon: '🎯' },
    { label: '本章使命', value: props.card.mission, icon: '📋' },
    { label: '核心冲突', value: props.card.coreConflict, icon: '⚔️' },
    { label: '登场人物', value: props.card.characters, icon: '👥' },
    { label: '必须覆盖', value: props.card.mustCover?.join('、'), icon: '✅' },
    { label: '本章禁区', value: props.card.forbiddenZones?.join('、'), icon: '🚫' },
    { label: '伏笔操作', value: props.card.hookOps, icon: '🎣' },
    { label: '节奏要求', value: props.card.pacing, icon: '📈' },
    { label: '章尾钩子', value: props.card.closingHook, icon: '🪝' },
  ].filter(s => s.value)
})
</script>

<template>
  <div class="yunmo-card p-4">
    <div class="flex items-center gap-2 mb-3">
      <span class="text-lg">📄</span>
      <h4 class="font-semibold text-sm" style="color: var(--yunmo-ink)">章节控制卡</h4>
      <a-tag v-if="props.card" color="processing" size="small">AI生成</a-tag>
    </div>

    <a-spin :spinning="loading">
      <div v-if="card" class="space-y-2">
        <div
          v-for="section in sections" :key="section.label"
          class="flex gap-2 text-sm py-1 border-b border-dashed"
          style="border-color: var(--yunmo-border)"
        >
          <span class="shrink-0 text-xs w-16" style="color: var(--yunmo-text-caption)">{{ section.icon }} {{ section.label }}</span>
          <span class="flex-1" style="color: var(--yunmo-ink)">{{ section.value }}</span>
        </div>
      </div>
      <div v-else class="text-center py-6">
        <div class="text-3xl mb-2 opacity-30">📋</div>
        <p class="text-xs" style="color: var(--yunmo-text-caption)">
          点击"生成控制卡"为本章建立结构化写作约束
        </p>
      </div>
    </a-spin>
  </div>
</template>
