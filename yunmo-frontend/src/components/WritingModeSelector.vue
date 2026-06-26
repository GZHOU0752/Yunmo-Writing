<script setup>
import { ref, computed } from 'vue'

const emit = defineEmits(['select'])
const props = defineProps({
  modelValue: { type: String, default: 'serial' },
  disabled: { type: Boolean, default: false },
})

const modes = [
  {
    key: 'serial',
    icon: '📝',
    title: '逐章串行',
    desc: 'AI 逐章顺序创作，每章完成后自动进入下一章。适合短中篇，质量最稳定。',
    speed: '🐢 较慢',
    quality: '⭐⭐⭐ 最高',
    recommended: true,
  },
  {
    key: 'parallel',
    icon: '⚡',
    title: '批次并行',
    desc: '5-8章为一个批次，批次内串行、批次间并行。适合中长篇，兼顾速度与质量。',
    speed: '🐇 较快',
    quality: '⭐⭐ 高',
  },
  {
    key: 'teams',
    icon: '👥',
    title: 'Agent Teams',
    desc: '3-5个 AI Agent 自主认领章节协作。适合大型长篇，速度最快但需人工复核。',
    speed: '🚀 最快',
    quality: '⭐ 需复核',
    experimental: true,
  },
]

const selected = ref(props.modelValue)

function select(mode) {
  if (props.disabled) return
  selected.value = mode.key
  emit('select', mode.key)
}
</script>

<template>
  <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
    <div
      v-for="mode in modes" :key="mode.key"
      class="yunmo-card p-4 cursor-pointer transition-all duration-300 relative overflow-hidden"
      :class="{
        'ring-2': selected === mode.key,
        'opacity-60 hover:opacity-80': selected !== mode.key && !disabled,
      }"
      :style="selected === mode.key
        ? { borderColor: 'var(--yunmo-accent)', boxShadow: '0 0 0 2px rgba(139,58,58,0.15)' }
        : {}"
      @click="select(mode)"
    >
      <!-- 推荐/实验标签 -->
      <div v-if="mode.recommended" class="absolute top-2 right-2 px-1.5 py-0.5 rounded text-xs font-medium"
        style="background: rgba(139,58,58,0.08); color: var(--yunmo-accent)">
        推荐
      </div>
      <div v-if="mode.experimental" class="absolute top-2 right-2 px-1.5 py-0.5 rounded text-xs font-medium"
        style="background: rgba(184,150,74,0.1); color: var(--yunmo-gold)">
        实验
      </div>

      <div class="text-2xl mb-2">{{ mode.icon }}</div>
      <h4 class="font-semibold text-sm mb-1" style="color: var(--yunmo-ink)">{{ mode.title }}</h4>
      <p class="text-xs leading-relaxed mb-2" style="color: var(--yunmo-text-caption)">{{ mode.desc }}</p>
      <div class="flex items-center gap-3 text-xs">
        <span style="color: var(--yunmo-text-caption)">{{ mode.speed }}</span>
        <span style="color: var(--yunmo-text-caption)">{{ mode.quality }}</span>
      </div>
    </div>
  </div>
</template>
