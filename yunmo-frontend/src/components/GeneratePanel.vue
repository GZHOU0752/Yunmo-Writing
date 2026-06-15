<script setup>
import QualityReportComp from './QualityReport.vue'

defineProps({
  novelId: String,
  chapterNumber: Number,
  sseStatus: String,
  streamedText: String,
  qualityReport: Object,
  focus: String,
})
const emit = defineEmits(['update:focus', 'generate'])

const statusLabels = {
  idle: { text: '就绪', color: 'default' },
  generating: { text: '写作中...', color: 'processing' },
  reviewing: { text: '审校中...', color: 'processing' },
  done: { text: '完成', color: 'success' },
}
</script>

<template>
  <div class="flex flex-col h-full">
    <h3 class="text-sm font-semibold mb-4" style="color:var(--yunmo-accent)">让 AI 写这一章</h3>

    <div class="mb-3">
      <label class="text-caption mb-1 block">你要 AI 怎么写</label>
      <a-textarea
        :value="focus"
        placeholder="侧重主角心理变化 / 推进感情线 / 增加战斗描写……"
        :rows="3"
        size="small"
        @input="emit('update:focus', $event.target.value)"
      />
    </div>

    <div class="mb-4">
      <a-button
        v-if="sseStatus === 'idle' || sseStatus === 'done'"
        type="primary"
        block
        @click="emit('generate')"
      >
        生成本章
      </a-button>
      <a-button
        v-else
        type="default"
        danger
        block
        @click="emit('generate')"
      >
        停止生成
      </a-button>
    </div>

    <a-tag v-if="sseStatus !== 'idle'" :color="statusLabels[sseStatus]?.color" class="mb-2">
      {{ statusLabels[sseStatus]?.text }}
    </a-tag>

    <div v-if="streamedText" class="mb-4 flex-1">
      <label class="text-caption mb-1 block">
        {{ streamedText.length }} 字
      </label>
      <div class="yunmo-card p-3 text-sm max-h-64 overflow-y-auto whitespace-pre-line" style="text-indent:2em; line-height:1.85">
        {{ streamedText }}
      </div>
    </div>

    <QualityReportComp
      v-if="qualityReport"
      :report="qualityReport"
    />
  </div>
</template>
