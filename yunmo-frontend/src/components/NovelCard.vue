<script setup>
defineProps({ novel: Object })
const emit = defineEmits(['click', 'detail'])

const genreLabels = {
  xuanhuan: '玄幻',
  qihuan: '奇幻',
  xianxia: '仙侠',
  dushi: '都市',
  xuanyi: '悬疑灵异',
  qingxiaoshuo: '轻小说',
}

const statusLabels = {
  OUTLINE: '规划中',
  DRAFT: '草稿',
  GENERATING: '生成中',
  GENERATED: '已生成',
  IN_PROGRESS: '写作中',
  REVIEWING: '审校中',
  COMPLETED: '已完本',
  FINALIZED: '已定稿',
}
</script>

<template>
  <div class="yunmo-card p-5 cursor-pointer hover:border-[var(--yunmo-accent)] transition-colors relative group"
       @click="emit('click')">
    <div class="flex-1">
      <h3 class="text-lg font-semibold mb-1">{{ novel.title }}</h3>
      <div class="flex items-center gap-3 text-sm text-caption">
        <a-tag>{{ genreLabels[novel.genreId] || novel.genreId }}</a-tag>
        <span>{{ novel.wordCount?.toLocaleString() || 0 }} 字</span>
        <span>{{ novel.totalChapters || 0 }} 章</span>
      </div>
      <p v-if="novel.synopsis" class="text-xs mt-2 leading-relaxed line-clamp-2" style="color:var(--yunmo-text-caption)">{{ novel.synopsis }}</p>
      <div class="mt-2">
        <a-badge :status="novel.status === 'FINALIZED' || novel.status === 'COMPLETED' ? 'success' : 'processing'"
                 :text="statusLabels[novel.status] || novel.status" />
      </div>
    </div>
    <!-- 右下角详情按钮 -->
    <div class="absolute bottom-3 right-3 opacity-0 group-hover:opacity-100 transition-opacity">
      <a-button size="small" type="text" @click.stop="emit('detail')">
        <span class="text-lg leading-none tracking-wider" style="color:var(--yunmo-text-caption)">···</span>
      </a-button>
    </div>
  </div>
</template>
