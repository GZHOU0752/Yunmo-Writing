<script setup>
import { ref, watch, computed } from 'vue'
import { useApi } from '@/composables/useApi'

const props = defineProps({
  novelId: String,
  chapterNumber: Number,
  activeBranch: { type: String, default: 'main' },
})
const emit = defineEmits(['update:activeBranch', 'refresh'])

const api = useApi()
const branches = ref([])
const loading = ref(false)
const showCreate = ref(false)
const newBranchName = ref('')

async function fetchBranches() {
  if (!props.novelId || !props.chapterNumber) return
  loading.value = true
  try {
    branches.value = await api.chapters.branches(props.novelId, props.chapterNumber) || []
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function createBranch() {
  if (!newBranchName.value.trim()) return
  try {
    await api.chapters.fork(props.novelId, props.chapterNumber, newBranchName.value.trim())
    newBranchName.value = ''
    showCreate.value = false
    await fetchBranches()
    emit('refresh')
  } catch (e) {
    console.error('创建分支失败:', e)
  }
}

watch(() => [props.novelId, props.chapterNumber], fetchBranches, { immediate: true })
</script>

<template>
  <div class="flex items-center gap-1 text-xs">
    <span class="text-caption shrink-0">分支</span>
    <a-select
      :value="activeBranch"
      size="small"
      class="flex-1"
      style="min-width:80px"
      @change="(v) => emit('update:activeBranch', v)"
    >
      <a-select-option
        v-for="b in branches"
        :key="b.branchName"
        :value="b.branchName"
      >
        {{ b.branchName }} ({{ b.versionCount }})
      </a-select-option>
    </a-select>
    <a-button size="small" type="text" class="toolbar-btn text-xs" @click="showCreate = true">+分支</a-button>
  </div>

  <!-- 创建分支弹窗 -->
  <a-modal
    :open="showCreate"
    title="创建叙事分支"
    @ok="createBranch"
    @cancel="showCreate = false"
    ok-text="创建"
    cancel-text="取消"
    width="320px"
  >
    <a-input v-model:value="newBranchName" placeholder="分支名称，如：悲剧结局、重生线..." />
  </a-modal>
</template>
