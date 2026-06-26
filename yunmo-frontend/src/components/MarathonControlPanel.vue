<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useApi } from '@/composables/useApi'
import { message } from 'ant-design-vue'

const props = defineProps({
  novelId: { type: String, required: true },
})

const api = useApi()
const status = ref(null)
const loading = ref(false)
const actionLoading = ref(false)
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
    const res = await api.get(`/api/v1/novels/${props.novelId}/marathon/status`)
    status.value = res
  } catch {
    // 无活跃任务
    status.value = { state: 'IDLE' }
  }
}

async function startMarathon() {
  actionLoading.value = true
  try {
    await api.post(`/api/v1/novels/${props.novelId}/marathon/start`, {
      startChapter: status.value?.currentChapter || 1,
      targetChapters: 0,
    })
    await fetchStatus()
    message.success('马拉松创作已启动')
  } catch (e) {
    message.error('启动失败: ' + (e?.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

async function pauseMarathon() {
  actionLoading.value = true
  try {
    await api.post(`/api/v1/novels/${props.novelId}/marathon/pause`, { reason: '手动暂停' })
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
    await api.post(`/api/v1/novels/${props.novelId}/marathon/resume`)
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
    await api.post(`/api/v1/novels/${props.novelId}/marathon/stop`)
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
  pollTimer = setInterval(fetchStatus, 15000) // 15秒轮询
})

onBeforeUnmount(() => {
  clearInterval(pollTimer)
})
</script>

<template>
  <div class="yunmo-card p-4">
    <div class="flex items-center justify-between mb-3">
      <div class="flex items-center gap-2">
        <span class="text-lg">⚙️</span>
        <h4 class="font-semibold text-sm" style="color: var(--yunmo-ink)">马拉松创作</h4>
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
        <div class="text-xs" style="color: var(--yunmo-text-caption)">当前章节</div>
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

    <!-- 操作按钮 -->
    <div class="flex gap-2">
      <button
        v-if="status?.state === 'IDLE'"
        class="yunmo-btn flex-1 text-sm"
        :disabled="actionLoading"
        @click="startMarathon"
      >{{ actionLoading ? '启动中...' : '▶ 启动马拉松' }}</button>

      <template v-if="status?.state === 'RUNNING'">
        <button class="yunmo-btn-outline flex-1 text-sm" :disabled="actionLoading" @click="pauseMarathon">
          ⏸ 暂停
        </button>
        <button class="yunmo-btn-outline text-sm" style="color: var(--yunmo-red); border-color: var(--yunmo-red)"
          :disabled="actionLoading" @click="stopMarathon">
          ⏹ 停止
        </button>
      </template>

      <template v-if="status?.state === 'PAUSED'">
        <button class="yunmo-btn flex-1 text-sm" :disabled="actionLoading" @click="resumeMarathon">
          ▶ 继续
        </button>
        <button class="yunmo-btn-outline text-sm" style="color: var(--yunmo-red); border-color: var(--yunmo-red)"
          :disabled="actionLoading" @click="stopMarathon">
          ⏹ 停止
        </button>
      </template>
    </div>
  </div>
</template>
