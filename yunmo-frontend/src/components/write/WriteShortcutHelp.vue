<script setup>
/**
 * 快捷键帮助面板
 * 按 F1 或 Ctrl+? 弹出，按分类分组显示所有快捷键
 */
import { computed } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  shortcuts: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:open'])

/** 按分类分组 */
const grouped = computed(() => {
  const groups = {}
  for (const s of props.shortcuts) {
    if (!groups[s.category]) groups[s.category] = []
    groups[s.category].push(s)
  }
  return groups
})

const categoryOrder = ['编辑', '导航', 'AI', '布局', '通用']

const sortedCategories = computed(() => {
  return categoryOrder.filter(c => grouped.value[c])
})
</script>

<template>
  <a-modal
    :open="open"
    @update:open="emit('update:open', $event)"
    :footer="null"
    width="480px"
    :closable="true"
    :mask-closable="true"
  >
    <template #title>
      <div class="flex items-center gap-2">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="2">
          <rect x="2" y="4" width="20" height="16" rx="2" />
          <path d="M6 8h.01M10 8h.01M14 8h.01M18 8h.01M6 12h.01M10 12h.01M14 12h.01M18 12h.01M8 16h8" />
        </svg>
        <span>快捷键</span>
      </div>
    </template>

    <div class="space-y-5">
      <div v-for="category in sortedCategories" :key="category">
        <h4 class="text-xs font-semibold tracking-wider mb-2 pb-1 border-b"
            style="color:var(--yunmo-accent); border-color:var(--yunmo-border)">
          {{ category }}
        </h4>
        <div class="space-y-1.5">
          <div
            v-for="shortcut in grouped[category]"
            :key="shortcut.key"
            class="flex items-center justify-between py-1"
          >
            <span class="text-sm" style="color:var(--yunmo-text-secondary)">{{ shortcut.label }}</span>
            <div class="flex items-center gap-1">
              <kbd
                v-for="(part, idx) in shortcut.displayKey.split(/(\s*\+\s*)/)"
                :key="idx"
              >
                <template v-if="part.trim() === '+'">
                  <span class="text-xs mx-0.5" style="color:var(--yunmo-text-caption)">+</span>
                </template>
                <template v-else-if="part">
                  <span class="inline-block px-1.5 py-0.5 text-[11px] font-mono rounded border"
                        style="background:var(--yunmo-paper-dark); border-color:var(--yunmo-border); color:var(--yunmo-text-primary)">
                    {{ part }}
                  </span>
                </template>
              </kbd>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="mt-4 pt-3 border-t text-center" style="border-color:var(--yunmo-border)">
      <span class="text-xs" style="color:var(--yunmo-text-caption)">按 Esc 关闭 · 输入框中不会触发导航快捷键</span>
    </div>
  </a-modal>
</template>
