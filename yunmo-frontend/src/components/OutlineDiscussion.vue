<script setup>
import { ref, nextTick, watch } from 'vue'
import { useApi } from '@/composables/useApi'

const props = defineProps({
  novelId: String,
  nodeId: { type: String, default: null },
  nodeTitle: { type: String, default: '' },
  open: Boolean,
})
const emit = defineEmits(['update:open', 'applySuggestion'])

const api = useApi()
const messages = ref([])
const inputText = ref('')
const streaming = ref(false)
const streamContent = ref('')
const chatContainer = ref(null)
let abortController = null

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || streaming.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  streaming.value = true
  streamContent.value = ''

  await nextTick()
  scrollToBottom()

  abortController = new AbortController()
  try {
    const res = await api.outline.discuss(props.novelId, text, props.nodeId)
    if (!res.ok || !res.body) throw new Error('SSE 连接失败')

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data:')) {
          try {
            const data = JSON.parse(line.slice(5).trim())
            if (data.token) streamContent.value += data.token
            if (data.error) streamContent.value = '[错误] ' + data.error
          } catch {}
        }
        if (line.startsWith('event: done')) {
          // end
        }
      }
    }
  } catch (e) {
    if (e.name !== 'AbortError') {
      streamContent.value = '[连接中断]'
    }
  } finally {
    if (streamContent.value) {
      messages.value.push({ role: 'ai', content: streamContent.value })
      streamContent.value = ''
    }
    streaming.value = false
    abortController = null
  }
}

function applyToOutline(content) {
  emit('applySuggestion', content)
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatContainer.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

watch(() => props.open, (v) => {
  if (v && messages.value.length === 0) {
    // 首次打开时自动发送上下文
    messages.value.push({
      role: 'ai',
      content: props.nodeTitle
        ? `我们来讨论「${props.nodeTitle}」的大纲吧。你有什么想法或问题？`
        : '我们来讨论大纲吧。你有什么想法或问题？'
    })
  }
})
</script>

<template>
  <a-drawer
    :open="open"
    @close="emit('update:open', false)"
    title="AI 剧情讨论"
    width="420px"
  >
    <!-- 聊天区 -->
    <div ref="chatContainer" class="space-y-3 mb-4 overflow-y-auto" style="max-height:calc(100vh - 200px)">
      <div
        v-for="(msg, i) in messages"
        :key="i"
        class="flex"
        :class="msg.role === 'user' ? 'justify-end' : 'justify-start'"
      >
        <div
          class="max-w-[85%] px-3 py-2 rounded-lg text-sm"
          :class="msg.role === 'user'
            ? 'bg-[var(--yunmo-accent)] text-white'
            : 'bg-[var(--yunmo-paper-dark)]'"
          :style="msg.role === 'ai' ? { color: 'var(--yunmo-text-primary)' } : {}"
        >
          <div class="whitespace-pre-wrap">{{ msg.content }}</div>
          <!-- AI 消息：应用到章纲按钮 -->
          <a-button
            v-if="msg.role === 'ai' && msg.content && msg.content.length > 10"
            size="small"
            type="link"
            class="mt-1 p-0 text-xs"
            @click="applyToOutline(msg.content)"
          >
            应用到全书大纲
          </a-button>
        </div>
      </div>

      <!-- 流式内容 -->
      <div v-if="streaming" class="flex justify-start">
        <div class="bg-[var(--yunmo-paper-dark)] px-3 py-2 rounded-lg max-w-[85%] text-sm"
             style="color:var(--yunmo-text-primary)">
          {{ streamContent }}<span class="cursor-blink">▍</span>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="flex gap-2">
      <a-textarea
        v-model:value="inputText"
        placeholder="提问大纲走向..."
        :rows="2"
        size="small"
        class="flex-1"
        @keydown.enter.exact.prevent="sendMessage"
      />
      <a-button type="primary" size="small" :loading="streaming" @click="sendMessage" class="self-end">
        发送
      </a-button>
    </div>
  </a-drawer>
</template>

<style scoped>
.cursor-blink {
  animation: blink 1s step-end infinite;
}
@keyframes blink {
  50% { opacity: 0; }
}
</style>
