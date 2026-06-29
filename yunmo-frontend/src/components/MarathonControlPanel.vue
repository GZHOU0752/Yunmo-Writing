<script setup>
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useApi } from '@/composables/useApi'
import { message } from 'ant-design-vue'

const props = defineProps({
  novelId: { type: String, required: true },
  currentChapter: { type: Number, default: 1 },
})

const api = useApi()
const status = ref(null)
const loading = ref(false)
const actionLoading = ref(false)
const targetChapterCount = ref(null) // 用户选择生成多少章
let pollTimer = null

const stateLabel = computed(() => ({
  IDLE: '空闲',
  RUNNING: '运行中',
  PAUSED: '已暂停',
  COMPLETED: '已完成',
  FAILED: '已失败',
}[status.value?.state] || '未知'))

const stateColor = computed(() => ({
  IDLE: 'var(--yunmo-text-caption)',
  RUNNING: 'var(--yunmo-green)',
  PAUSED: 'var(--yunmo-amber)',
  COMPLETED: 'var(--yunmo-accent)',
  FAILED: 'var(--yunmo-red)',
}[status.value?.state] || 'var(--yunmo-text-caption)'))

async function fetchStatus() {
  try {
    const res = await api.get(`/novels/${props.novelId}/marathon/status`)
    status.value = res
  } catch {
    status.value = { state: 'IDLE' }
  }
}

async function startMarathon() {
  actionLoading.value = true
  try {
    // 优先用马拉松当前进度，首次启动则用用户选中的章节
    const startChapter = (status.value?.state !== 'IDLE' && status.value?.currentChapter)
      ? status.value.currentChapter
      : props.currentChapter
    const target = targetChapterCount.value ? Number(targetChapterCount.value) : 0
    await api.post(`/novels/${props.novelId}/marathon/start`, {
      startChapter,
      targetChapters: target > 0 ? target : 0,
    })
    await fetchStatus()
    message.success(target > 0 ? `开始批量生成 ${target} 章` : '批量生成已启动')
  } catch (e) {
    message.error('启动失败: ' + (e?.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

async function pauseMarathon() {
  actionLoading.value = true
  try {
    await api.post(`/novels/${props.novelId}/marathon/pause`, { reason: '手动暂停' })
    await fetchStatus()
    message.success('已暂停')
  } catch (e) {
    message.error('暂停失败')
  } finally {
    actionLoading.value = false
  }
}

async function resumeMarathon() {
  actionLoading.value = true
  try {
    await api.post(`/novels/${props.novelId}/marathon/resume`)
    await fetchStatus()
    message.success('已恢复')
  } catch (e) {
    message.error('恢复失败')
  } finally {
    actionLoading.value = false
  }
}

async function stopMarathon() {
  actionLoading.value = true
  try {
    await api.post(`/novels/${props.novelId}/marathon/stop`)
    status.value = { state: 'IDLE' }
    message.success('已停止')
  } catch (e) {
    message.error('停止失败')
  } finally {
    actionLoading.value = false
  }
}

onMounted(() => {
  fetchStatus()
  // 运行时 15s 轮询，空闲时 60s 轮询
  pollTimer = setInterval(() => {
    fetchStatus()
  }, 15000)
})

// 状态变化时调整轮询频率
watch(() => status.value?.state, (state) => {
  clearInterval(pollTimer)
  const interval = state === 'RUNNING' ? 15000 : 60000
  pollTimer = setInterval(fetchStatus, interval)
})

onBeforeUnmount(() => {
  clearInterval(pollTimer)
})
</script>

<template>
  <div class="yunmo-card p-4">
    <div class="flex items-center justify-between mb-3">
      <div class="flex items-center gap-2">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><circle cx="12" cy="12" r="3"/><path d="M12 1v4m0 14v4M4.22 4.22l2.83 2.83m9.9 9.9l2.83 2.83M1 12h4m14 0h4M4.22 19.78l2.83-2.83m9.9-9.9l2.83-2.83"/></svg>
        <h4 class="font-semibold text-sm" style="color: var(--yunmo-ink)">批量生成</h4>
      </div>
      <div class="flex items-center gap-1.5">
        <span class="w-2 h-2 rounded-full" :style="{ background: stateColor }"
          :class="{ 'status-pulse': status?.state === 'RUNNING' }"
        />
        <span class="text-xs font-medium" :style="{ color: stateColor }">{{ stateLabel }}</span>
      </div>
    </div>

    <!-- 进度信息 -->
    <div v-if="status?.state !== 'IDLE'" class="grid grid-cols-2 gap-2 mb-3">
      <div class="text-center p-2 rounded" style="background: var(--yunmo-paper-dark)">
        <div class="text-lg font-bold font-tabular" style="color: var(--yunmo-accent)">{{ status?.currentChapter || 0 }}</div>
        <div class="text-xs" style="color: var(--yunmo-text-caption)">
          当前章节
          <span v-if="status?.targetChapters > 0" class="opacity-60">/{{ status.targetChapters }}</span>
        </div>
      </div>
      <div class="text-center p-2 rounded" style="background: var(--yunmo-paper-dark)">
        <div class="text-lg font-bold font-tabular" style="color: var(--yunmo-green)">{{ status?.totalWritten || 0 }}</div>
        <div class="text-xs" style="color: var(--yunmo-text-caption)">已完成</div>
      </div>
    </div>

    <!-- 失败信息 -->
    <div v-if="status?.consecutiveFailures > 0" class="text-xs mb-3 p-2 rounded"
      style="background: rgba(179,68,58,0.06); color: var(--yunmo-red)">
      连续失败 {{ status.consecutiveFailures }} 次
      <span v-if="status?.totalFailed">（共 {{ status.totalFailed }} 次）</span>
    </div>

    <!-- 最近事件 -->
    <div v-if="status?.recentEvents?.length" class="mb-3 space-y-1 max-h-32 overflow-y-auto">
      <div
        v-for="(event, i) in status.recentEvents" :key="i"
        class="text-xs flex items-center gap-2"
        style="color: var(--yunmo-text-caption)"
      >
        <span class="w-1 h-1 rounded-full" style="background: var(--yunmo-border)" />
        <span class="font-tabular opacity-60">{{ event.timestamp?.substring(11, 19) }}</span>
        <span class="truncate">{{ event.message }}</span>
      </div>
    </div>

    <!-- 章数选择（仅在空闲时显示） -->
    <div v-if="status?.state === 'IDLE'" class="mb-3">
      <div class="flex items-center gap-2 text-xs" style="color: var(--yunmo-text-caption)">
        <span>从第</span>
        <span class="font-tabular font-semibold" style="color: var(--yunmo-ink)">{{ props.currentChapter }}</span>
        <span>章开始，生成</span>
        <input
          v-model="targetChapterCount"
          type="number"
          min="1"
          max="200"
          placeholder="全部"
          class="w-14 text-center rounded px-1 py-0.5 text-xs border"
          style="border-color: var(--yunmo-border); background: var(--yunmo-paper-dark); color: var(--yunmo-ink)"
        />
        <span>章</span>
        <span class="opacity-50">（留空写完为止）</span>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="flex gap-2">
      <button
        v-if="status?.state === 'IDLE'"
        class="yunmo-btn flex-1 text-sm"
        :disabled="actionLoading"
        @click="startMarathon"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" class="inline mr-1 -mt-0.5"><polygon points="5 3 19 12 5 21 5 3"/></svg>
        {{ actionLoading ? '启动中...' : (targetChapterCount ? `生成 ${targetChapterCount} 章` : '开始批量生成') }}
      </button>

      <template v-if="status?.state === 'RUNNING'">
        <button class="yunmo-btn-outline flex-1 text-sm" :disabled="actionLoading" @click="pauseMarathon">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" class="inline mr-1 -mt-0.5"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>暂停
        </button>
        <button class="yunmo-btn-outline text-sm" style="color: var(--yunmo-red); border-color: var(--yunmo-red)"
          :disabled="actionLoading" @click="stopMarathon">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" class="inline mr-1 -mt-0.5"><rect x="4" y="4" width="16" height="16" rx="2"/></svg>停止
        </button>
      </template>

      <template v-if="status?.state === 'PAUSED'">
        <button class="yunmo-btn flex-1 text-sm" :disabled="actionLoading" @click="resumeMarathon">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" class="inline mr-1 -mt-0.5"><polygon points="5 3 19 12 5 21 5 3"/></svg>继续
        </button>
        <button class="yunmo-btn-outline text-sm" style="color: var(--yunmo-red); border-color: var(--yunmo-red)"
          :disabled="actionLoading" @click="stopMarathon">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" class="inline mr-1 -mt-0.5"><rect x="4" y="4" width="16" height="16" rx="2"/></svg>停止
        </button>
      </template>
    </div>
  </div>
</template>
