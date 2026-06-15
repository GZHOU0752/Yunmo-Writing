<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'

const props = defineProps({
  open: Boolean,
  parentNode: Object,
})
const emit = defineEmits(['update:open', 'done'])

const childLevel = ref((props.parentNode?.level || 1) + 1)
const count = ref(3)
const generating = ref(false)
const streamedText = ref('')
const completed = ref(false)
let reader = null
let aborter = null

const levelNames = { 0: '总纲', 1: '卷', 2: '章', 3: '节' }
function levelName(l) { return levelNames[l] || '节点' }

watch(() => props.open, (val) => {
  if (val) {
    childLevel.value = Math.min(3, (props.parentNode?.level || 1) + 1)
    count.value = 3
    streamedText.value = ''
    completed.value = false
  }
})

async function startCompletion() {
  if (!props.parentNode?.id) return
  generating.value = true
  completed.value = false
  streamedText.value = ''
  try {
    aborter = new AbortController()
    const token = localStorage.getItem('yunmo_token')
    const resp = await fetch(
      `/api/v1/novels/${props.parentNode.novelId}/outline/${props.parentNode.id}/complete`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ childLevel: childLevel.value, count: count.value }),
        signal: aborter.signal,
      }
    )
    reader = resp.body.getReader()
    const decoder = new TextDecoder()
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const chunk = decoder.decode(value, { stream: true })
      for (const line of chunk.split('\n')) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data:') && !trimmed.includes('"status"')) {
          streamedText.value += trimmed.slice(5).trim()
        }
        if (trimmed.startsWith('event: done')) completed.value = true
      }
    }
  } catch (e) {
    if (e.name !== 'AbortError') console.error('AI补全失败:', e)
  } finally {
    generating.value = false
  }
}

function stopCompletion() {
  aborter?.abort()
  generating.value = false
}

function handleDone() {
  emit('done')
  emit('update:open', false)
}

// 组件卸载时确保清理 reader 和 aborter
onBeforeUnmount(() => {
  aborter?.abort()
  if (reader) {
    try { reader.cancel() } catch {}
    reader = null
  }
})
</script>

<template>
  <a-modal
    :open="open"
    title="AI 自动补全大纲"
    :footer="null"
    @cancel="$emit('update:open', false)"
    width="560px"
  >
    <a-form layout="vertical" class="mb-4">
      <a-form-item label="目标层级">
        <a-select v-model:value="childLevel" :disabled="generating">
          <a-select-option :value="1">卷</a-select-option>
          <a-select-option :value="2">章</a-select-option>
          <a-select-option :value="3">节</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="生成数量">
        <a-input-number v-model:value="count" :min="1" :max="10" :disabled="generating" style="width:100%" />
      </a-form-item>
      <a-form-item>
        <a-button
          v-if="!generating"
          type="primary"
          @click="startCompletion"
        >
          开始生成 {{ levelName(childLevel) }} 大纲
        </a-button>
        <a-button v-else danger @click="stopCompletion">停止生成</a-button>
        <a-button
          v-if="completed && streamedText"
          type="primary"
          ghost
          class="ml-2"
          @click="handleDone"
        >
          确认并刷新
        </a-button>
      </a-form-item>
    </a-form>

    <div
      v-if="streamedText"
      class="bg-[var(--yunmo-paper-dark)] p-4 rounded text-sm whitespace-pre-wrap"
      style="font-family: 'Noto Serif SC', serif; max-height: 300px; overflow-y: auto"
    >
      {{ streamedText }}
    </div>

    <div v-if="generating" class="text-center py-4 text-caption">
      <a-spin size="small" /> AI 正在思考大纲结构...
    </div>
  </a-modal>
</template>
