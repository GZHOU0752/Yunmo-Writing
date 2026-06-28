<script setup>
const props = defineProps({
  icon: { type: String, default: '' },
  title: { type: String, required: true },
  description: { type: String, required: true },
  delay: { type: Number, default: 0 },
  variant: { type: String, default: 'default' }, // 'default' | 'accent' | 'compact'
})
</script>

<template>
  <div
    class="feature-card"
    :style="{ animationDelay: `${delay}ms` }"
  >
    <!-- variant: accent — 顶部色条 -->
    <div v-if="variant === 'accent'" class="w-12 h-1 rounded-full mb-4" style="background: var(--yunmo-accent)" />
    <!-- variant: default — SVG icon -->
    <div v-if="variant === 'default' && icon" class="mb-3" style="opacity:0.7" v-html="icon"></div>
    <h3 class="text-lg font-semibold mb-2">{{ title }}</h3>
    <p class="text-sm leading-relaxed" style="color:var(--yunmo-text-secondary)">{{ description }}</p>
  </div>
</template>
