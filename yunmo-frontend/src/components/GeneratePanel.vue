<script setup>
import QualityReportComp from './QualityReport.vue'

defineProps({
  novelId: String,
  chapterNumber: Number,
  sseStatus: String,
  streamedText: String,
  qualityReport: Object,
  checkpoint: Object,
  writingStyle: { type: String, default: '' },
})
const emit = defineEmits(['generate', 'clearCheckpoint'])

const statusLabels = {
  idle: { text: '就绪', color: 'default' },
  generating: { text: '写作中...', color: 'processing' },
  reviewing: { text: '审校中...', color: 'processing' },
  done: { text: '完成', color: 'success' },
}
</script>

<template>
  <div class="glass-panel p-4">
    <!-- 标题行 -->
    <div class="flex items-center gap-2 mb-4">
      <div
        class="w-2 h-2 rounded-full transition-all duration-500"
        :class="{
          'bg-[var(--yunmo-gold)] status-pulse': sseStatus === 'generating' || sseStatus === 'reviewing',
          'bg-[var(--yunmo-green)]': sseStatus === 'done',
          'bg-[var(--yunmo-border)]': sseStatus === 'idle',
        }"
      />
      <h3 class="text-sm font-semibold" style="color:var(--yunmo-accent)">AI 写作</h3>
      <a-tag v-if="sseStatus !== 'idle'" :color="statusLabels[sseStatus]?.color" size="small">
        {{ statusLabels[sseStatus]?.text }}
      </a-tag>
      <!-- 文风标签 -->
      <a-tag v-if="writingStyle" color="processing" size="small" class="ml-auto">
        文风已设置
      </a-tag>
    </div>
    <!-- 生成阶段提示 -->
    <div
      v-if="sseStatus === 'generating' || sseStatus === 'reviewing'"
      class="text-xs mb-3 px-2 py-1 rounded"
      style="background:var(--yunmo-paper-dark); color:var(--yunmo-text-caption)"
    >
      {{ sseStatus === 'generating' ? '上下文组装 → 爽点设计 → 正文写作中...' : '润色审校 → 对抗编辑 → 最终判定...' }}
    </div>

    <!-- 断点续写提示 -->
    <div v-if="checkpoint && (sseStatus === 'idle' || sseStatus === 'done')"
      class="mb-3 px-2 py-1.5 rounded text-xs flex items-center gap-2"
      style="background:rgba(184,150,74,0.1);border:1px solid var(--yunmo-gold);color:var(--yunmo-amber)"
    >
      <span>检测到未完成的生成（{{ checkpoint.lastStage }}）</span>
      <span class="flex-1" />
      <a-button size="small" type="text" @click="$emit('clearCheckpoint')" class="text-xs" style="color:var(--yunmo-amber)">忽略</a-button>
    </div>

    <!-- 章节字数范围提示 -->
    <div class="text-xs mb-2 text-center">
      每章字数：<strong style="color:var(--yunmo-accent)">2,300 - 2,799 字</strong>
    </div>

    <!-- 生成/停止按钮 -->
    <div class="mb-4">
      <button
        v-if="sseStatus === 'idle' || sseStatus === 'done'"
        class="seal-btn w-full"
        @click="emit('generate')"
      >
        生 成 本 章
      </button>
      <button
        v-else
        class="yunmo-btn-outline w-full"
        @click="emit('generate')"
      >
        停止生成
      </button>
    </div>

    <!-- 流式输出预览 -->
    <div
      v-if="streamedText && (sseStatus === 'done' || sseStatus === 'reviewing')"
      class="ink-reveal"
    >
      <div class="flex items-center justify-between mb-1.5">
        <label class="text-xs font-semibold" style="color:var(--yunmo-accent)">生成预览</label>
        <span class="text-[11px] font-tabular">
          {{ streamedText.length.toLocaleString() }} 字
        </span>
      </div>
      <div
        class="rounded-lg p-3.5 text-sm max-h-56 overflow-y-auto leading-relaxed-cn"
        style="background:var(--yunmo-paper-dark);text-indent:2em;border:1px solid var(--yunmo-border)"
      >
        {{ streamedText }}
      </div>
    </div>

    <!-- 质量报告 -->
    <QualityReportComp
      v-if="qualityReport"
      :report="qualityReport"
    />
  </div>
</template>
