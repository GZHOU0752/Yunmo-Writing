<script setup>
const props = defineProps({
  icon: { type: String, default: '✦' },
  title: { type: String, required: true },
  description: { type: String, required: true },
  delay: { type: Number, default: 0 },
})
</script>

<template>
  <div
    class="feature-card"
    :style="{ animationDelay: `${delay}ms` }"
  >
    <div class="text-2xl mb-3" style="opacity:0.7">{{ icon }}</div>
    <h3 class="text-lg font-semibold mb-2" style="color:var(--yunmo-ink)">{{ title }}</h3>
    <p class="text-sm leading-relaxed" style="color:var(--yunmo-text-secondary)">{{ description }}</p>
  </div>
</template>
