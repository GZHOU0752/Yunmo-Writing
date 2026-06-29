<script setup>
import { computed } from 'vue'

const props = defineProps({
  novel: Object,
  index: { type: Number, default: 0 },
})
const emit = defineEmits(['click', 'detail'])

const genreLabels = {
  xuanhuan: '玄幻',
  qihuan: '奇幻',
  xianxia: '仙侠',
  dushi: '都市',
  xuanyi: '悬疑灵异',
  qingxiaoshuo: '轻小说',
  tongren: '同人',
  duanpian: '短篇',
}

// 字数进度（对标全书目标总字数，默认150万字）
const targetWords = computed(() => props.novel?.targetTotalWords || 1500000)
const wordProgress = computed(() => {
  const words = props.novel.wordCount || 0
  return Math.min((words / targetWords.value) * 100, 100)
})
</script>

<template>
  <div
    class="yunmo-card tilt-card p-5 cursor-pointer relative group overflow-hidden"
    :style="{ animationDelay: `${index * 60}ms` }"
    @click="emit('click')"
  >
    <!-- 宣纸纹理底纹 (hover时显现) -->
    <div class="absolute inset-0 opacity-0 group-hover:opacity-[0.025] transition-opacity duration-500 pointer-events-none card-noise" />

    <div class="relative z-[1]">
      <!-- 标题 -->
      <h3 class="text-lg font-semibold truncate transition-colors duration-300 group-hover:text-[var(--yunmo-accent)]">
        {{ novel.title }}
      </h3>

      <!-- 元信息行 -->
      <div class="flex items-center gap-2 text-xs mt-2 mb-3">
        <a-tag>{{ genreLabels[novel.genreId] || novel.genreId }}</a-tag>
        <span class="font-tabular" style="color:var(--yunmo-text-secondary)">
          {{ (novel.wordCount || 0).toLocaleString() }} 字
        </span>
        <span class="w-1 h-1 rounded-full bg-[var(--yunmo-border)]" />
        <span class="font-tabular" style="color:var(--yunmo-text-secondary)">
          {{ novel.totalChapters || 0 }} 章
        </span>
      </div>

      <!-- 字数进度条 -->
      <div class="mb-3">
        <div class="flex items-center justify-between text-xs mb-1">
          <span>进度</span>
          <span class="font-tabular">
            {{ Math.round(wordProgress) }}%
          </span>
        </div>
        <div class="novel-progress">
          <div
            class="novel-progress-bar"
            :style="{
              width: `${wordProgress}%`,
              background: wordProgress >= 100
                ? 'var(--yunmo-green)'
                : wordProgress >= 50
                  ? 'var(--yunmo-amber)'
                  : 'var(--yunmo-accent-light)',
            }"
          />
        </div>
      </div>

      <!-- 简介 -->
      <p
        v-if="novel.synopsis"
        class="text-xs leading-relaxed-cn line-clamp-2 text-pretty"
        style="color:var(--yunmo-text-caption);text-indent:2em"
      >{{ novel.synopsis }}</p>
      <p
        v-else
        class="text-xs italic"
        style="color:var(--yunmo-border);text-indent:2em"
      >暂无简介</p>
    </div>

    <!-- 详情按钮 (hover浮现) -->
    <div class="absolute bottom-3 right-3 opacity-0 group-hover:opacity-100 transition-fast translate-y-1 group-hover:translate-y-0 z-[1]">
      <a-button size="small" type="text" @click.stop="emit('detail')">
        <span class="text-sm tracking-wider">详情</span>
      </a-button>
    </div>
  </div>
</template>
