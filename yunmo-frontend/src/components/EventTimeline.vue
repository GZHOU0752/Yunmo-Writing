<script setup>
import { ref, onMounted } from 'vue'
import { useApi } from '@/composables/useApi'

const props = defineProps({
  novelId: String,
  chapters: { type: Array, default: () => [] },
  currentChapter: { type: Number, default: 1 },
})
const emit = defineEmits(['select-chapter'])

const api = useApi()
const loading = ref(false)

/** 章节状态颜色映射 */
function chapterColor(status) {
  switch (status) {
    case 'GENERATED':
    case 'FINALIZED':
      return 'var(--yunmo-green)'
    case 'DRAFT':
    case 'IN_PROGRESS':
      return 'var(--yunmo-amber)'
    case 'REVIEWING':
      return 'var(--yunmo-accent-light)'
    default:
      return 'var(--yunmo-border)'
  }
}

function chapterStatusLabel(status) {
  switch (status) {
    case 'GENERATED':
    case 'FINALIZED':
      return '已完成'
    case 'DRAFT':
      return '草稿'
    case 'IN_PROGRESS':
      return '写作中'
    case 'REVIEWING':
      return '审校中'
    default:
      return '未开始'
  }
}

function goToChapter(cn) {
  emit('select-chapter', cn)
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-3">
      <span class="text-xs font-semibold" style="color:var(--yunmo-accent)">章节时间线</span>
      <span class="text-[10px]" style="color:var(--yunmo-text-caption)">{{ chapters.length }} 章</span>
    </div>

    <a-spin :spinning="loading" size="small">
      <div v-if="chapters.length === 0" class="text-center py-4">
        <span class="text-xs" style="color:var(--yunmo-text-caption)">暂无章节</span>
      </div>

      <div v-else class="chapter-timeline overflow-y-auto" style="max-height:400px">
        <div
          v-for="ch in chapters"
          :key="ch.chapterNumber"
          class="chapter-timeline-item mb-1"
          :class="{ 'active': currentChapter === ch.chapterNumber }"
          @click="goToChapter(ch.chapterNumber)"
        >
          <div
            class="px-3 py-2 rounded-md cursor-pointer text-xs transition-spring group"
            :class="currentChapter === ch.chapterNumber
              ? 'bg-[var(--yunmo-accent)] text-[var(--yunmo-paper-light)]'
              : 'hover:bg-[var(--yunmo-paper-dark)]'"
          >
            <div class="flex items-center gap-2">
              <!-- 状态圆点 -->
              <span
                class="w-2 h-2 rounded-full shrink-0"
                :style="{ background: chapterColor(ch.status) }"
              />
              <span class="truncate flex-1 font-medium">
                {{ ch.title || '第' + ch.chapterNumber + '章' }}
              </span>
              <span class="text-[10px] font-tabular opacity-60">
                {{ (ch.wordCount || 0).toLocaleString() }}字
              </span>
            </div>
            <!-- 因果句 -->
            <div
              v-if="ch.causalSentence"
              class="text-[10px] mt-1 truncate"
              :class="currentChapter === ch.chapterNumber ? 'opacity-70' : ''"
              style="opacity:0.55"
            >
              {{ ch.causalSentence }}
            </div>
            <!-- 爽点标记 -->
            <div
              v-if="ch.isHighlight"
              class="flex items-center gap-1 mt-1"
              :class="currentChapter === ch.chapterNumber ? 'text-[var(--yunmo-gold)]' : ''"
            >
              <span class="text-[10px]">★</span>
              <span class="text-[10px]" :style="{ color: 'var(--yunmo-gold)' }">爽点</span>
            </div>
          </div>
        </div>
      </div>
    </a-spin>

    <!-- 图例 -->
    <div class="flex flex-wrap gap-3 mt-3 pt-2 border-t border-[var(--yunmo-border)]">
      <div class="flex items-center gap-1">
        <span class="w-2 h-2 rounded-full" style="background:var(--yunmo-green)" />
        <span class="text-[10px]" style="color:var(--yunmo-text-caption)">已完成</span>
      </div>
      <div class="flex items-center gap-1">
        <span class="w-2 h-2 rounded-full" style="background:var(--yunmo-amber)" />
        <span class="text-[10px]" style="color:var(--yunmo-text-caption)">草稿/写作中</span>
      </div>
      <div class="flex items-center gap-1">
        <span class="w-2 h-2 rounded-full" style="background:var(--yunmo-border)" />
        <span class="text-[10px]" style="color:var(--yunmo-text-caption)">未开始</span>
      </div>
      <div class="flex items-center gap-1">
        <span class="text-[10px]" style="color:var(--yunmo-gold)">★</span>
        <span class="text-[10px]" style="color:var(--yunmo-text-caption)">爽点</span>
      </div>
    </div>
  </div>
</template>
