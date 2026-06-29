<script setup>
import { ref, nextTick, watch } from 'vue'
import { useApi } from '@/composables/useApi'

const props = defineProps({
  novelId: String,
  chapterContent: { type: String, default: '' },
  chapterNumber: { type: Number, default: 1 },
})
const emit = defineEmits(['insert-text'])

const api = useApi()
const panelOpen = ref(false)
const messages = ref([])
const inputText = ref('')
const streaming = ref(false)
const streamContent = ref('')
const chatContainer = ref(null)
let abortController = null

const quickActions = [
  { label: '润色这段', prompt: '请润色以下文本，使其更加生动自然，消除AI痕迹：' },
  { label: '续写', prompt: '请根据以下内容的风格和情节走向，续写 300 字：' },
  { label: '分析人物', prompt: '请分析以下文本中的人物性格特点和行为动机：' },
  { label: '检查逻辑', prompt: '请检查以下文本的逻辑连贯性，指出可能的问题：' },
]

/** 获取选中的文本或当前章节内容作为上下文 */
function getContext() {
  const selection = window.getSelection()
  const selectedText = selection?.toString()?.trim() || ''

  if (selectedText) return selectedText
  return props.chapterContent?.substring(0, 500) || ''
}

/** 上一次使用的快捷操作 prompt */
const lastQuickAction = ref('')

/** 构建对话历史（多轮对话），过滤系统消息 */
function buildHistory() {
  const systemErrors = ['[连接中断]', '[已停止]', '[错误]']
  return messages.value
    .filter(m => {
      if (!m.content) return false
      if (m.role !== 'user' && m.role !== 'ai') return false
      // 过滤系统错误/状态消息
      for (const err of systemErrors) {
        if (m.content.startsWith(err)) return false
      }
      return true
    })
    .slice(-6)
    .map(m => ({
      role: m.role === 'user' ? 'user' : 'assistant',
      content: m.content.substring(0, 500),
    }))
}

async function sendMessage(customPrompt) {
  const text = customPrompt || inputText.value.trim()
  if ((!text && !customPrompt) || streaming.value) return

  // 记录快捷操作，用于"重新生成"
  if (customPrompt) lastQuickAction.value = customPrompt

  const context = getContext()
  const fullMessage = customPrompt
    ? (context ? `${customPrompt}\n\n${context}` : customPrompt)
    : text

  const displayContent = customPrompt
    ? `${quickActions.find(a => a.prompt === customPrompt)?.label || '快捷操作'}\n\n${context ? context.substring(0, 100) + (context.length > 100 ? '...' : '') : text}`
    : text

  messages.value.push({ role: 'user', content: displayContent })
  inputText.value = ''
  streaming.value = true
  streamContent.value = ''

  await nextTick()
  scrollToBottom()

  abortController = new AbortController()
  try {
    const history = buildHistory()
    const res = await api.novels.chat(props.novelId, fullMessage, props.chapterNumber, history, abortController.signal)

    if (!res.ok || !res.body) throw new Error('连接失败')

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

function acceptResponse() {
  const lastAiMsg = [...messages.value].reverse().find(m => m.role === 'ai')
  if (lastAiMsg?.content) {
    emit('insert-text', lastAiMsg.content)
  }
}

function rejectResponse() {
  const lastIdx = messages.value.length - 1
  if (messages.value[lastIdx]?.role === 'ai') {
    messages.value.splice(lastIdx, 1)
  }
}

function stopStreaming() {
  abortController?.abort()
  streaming.value = false
  if (streamContent.value) {
    messages.value.push({ role: 'ai', content: streamContent.value + '\n\n[已停止]' })
    streamContent.value = ''
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatContainer.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

watch(() => panelOpen.value, (v) => {
  if (v && messages.value.length === 0) {
    messages.value.push({
      role: 'ai',
      content: `你好！我是 AI 写作助手。我可以帮你：\n• 润色文字——使语言更生动自然\n• 续写情节——延续当前风格\n• 分析人物——深度解析角色性格\n• 检查逻辑——发现情节矛盾\n\n你可以选择文本后使用快捷按钮，或直接输入问题。`,
    })
  }
})
</script>

<template>
  <div>
    <!-- 入口按钮 -->
    <button
      class="collapsible-header w-full text-left"
      :aria-expanded="panelOpen"
      aria-controls="chat-panel-body"
      @click="panelOpen = !panelOpen"
    >
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
           :style="{ transform: panelOpen ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
        <path d="M6 9l6 6 6-6" />
      </svg>
      <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">AI 写作助手</span>
      <span v-if="streaming" class="w-1.5 h-1.5 rounded-full bg-[var(--yunmo-gold)] status-pulse ml-auto" />
    </button>

    <div v-if="panelOpen" id="chat-panel-body" class="mt-2">
      <!-- 快捷按钮 -->
      <div class="flex flex-wrap gap-1.5 mb-3">
        <button
          v-for="action in quickActions"
          :key="action.label"
          class="text-[11px] px-3 py-1.5 rounded-full border transition-fast"
          style="border-color:var(--yunmo-border);color:var(--yunmo-text-secondary);min-height:32px"
          :disabled="streaming"
          @click="sendMessage(action.prompt)"
        >
          {{ action.label }}
        </button>
      </div>

      <!-- 聊天区 -->
      <div
        ref="chatContainer"
        class="space-y-2.5 overflow-y-auto mb-3"
        style="max-height:300px;min-height:80px"
      >
        <div
          v-for="(msg, i) in messages"
          :key="i"
          class="flex"
          :class="msg.role === 'user' ? 'justify-end' : 'justify-start'"
        >
          <div
            class="max-w-[90%] px-3 py-2 rounded-lg text-xs"
            :class="msg.role === 'user'
              ? 'bg-[var(--yunmo-accent)] text-[var(--yunmo-paper-light)]'
              : 'bg-[var(--yunmo-paper-dark)]'"
            :style="msg.role === 'ai' ? { color: 'var(--yunmo-ink)' } : {}"
          >
            <div class="whitespace-pre-wrap leading-relaxed">{{ msg.content }}</div>

            <!-- AI 消息操作按钮 -->
            <div v-if="msg.role === 'ai' && msg.content && msg.content.length > 10 && i === messages.length - 1 && !streaming"
                 class="flex gap-2 mt-2 pt-2" style="border-top:1px solid var(--yunmo-border)">
              <button
                class="text-xs px-3 py-1.5 rounded"
                style="background:var(--yunmo-accent);color:var(--yunmo-paper-light);min-width:44px;min-height:32px"
                @click="acceptResponse"
              >采纳</button>
              <button
                class="text-xs px-3 py-1.5 rounded"
                style="background:var(--yunmo-paper-light);color:var(--yunmo-text-caption);border:1px solid var(--yunmo-border);min-width:44px;min-height:32px"
                @click="rejectResponse"
              >拒绝</button>
              <button
                class="text-xs px-3 py-1.5 rounded"
                style="background:var(--yunmo-paper-light);color:var(--yunmo-text-caption);border:1px solid var(--yunmo-border);min-width:44px;min-height:32px"
                @click="sendMessage(lastQuickAction || quickActions[0].prompt)"
              >重新生成</button>
            </div>
          </div>
        </div>

        <!-- 流式内容 -->
        <div v-if="streaming" class="flex justify-start">
          <div class="bg-[var(--yunmo-paper-dark)] px-3 py-2 rounded-lg max-w-[90%] text-xs"
               style="color:var(--yunmo-ink);min-width:60px">
            {{ streamContent }}<span class="inline-block w-1.5 h-3 ml-0.5 align-middle" style="background:var(--yunmo-accent);animation:blink 1s step-end infinite" />
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="flex gap-2">
        <a-textarea
          v-model:value="inputText"
          placeholder="输入你的问题..."
          :rows="2"
          size="small"
          class="flex-1"
          :disabled="streaming"
          @keydown.enter.exact.prevent="sendMessage()"
        />
        <div class="flex flex-col gap-1 self-end">
          <a-button
            v-if="!streaming"
            type="primary"
            size="small"
            :disabled="!inputText.trim()"
            @click="sendMessage()"
          >
            发送
          </a-button>
          <a-button
            v-else
            size="small"
            danger
            @click="stopStreaming"
          >
            停止
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes blink {
  50% { opacity: 0; }
}
</style>
