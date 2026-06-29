<script setup>
/**
 * 版本历史抽屉 — 左右并排 diff 视图
 * 参考起点中文网的版本对比设计
 */
import { ref, computed } from 'vue'
import { useApi } from '@/composables/useApi'
import { useWriteStore } from '@/composables/useWriteStore'
import BranchSwitcher from '@/components/BranchSwitcher.vue'

const props = defineProps({
  novelId: { type: String, required: true },
  selectedChapterNum: { type: Number, required: true },
})

const emit = defineEmits(['update:open'])

const api = useApi()
const store = useWriteStore()

const showVersions = ref(false)
const versions = ref([])
const versionsLoading = ref(false)
const activeBranch = ref('main')
const selectedVersionId = ref(null)
const selectedVersionContent = ref('')
const selectedVersionLoading = ref(false)

/** 选中的版本 */
const selectedVersion = computed(() => {
  return versions.value.find(v => v.id === selectedVersionId.value) || null
})

/** 加载版本历史 */
async function loadVersions() {
  versionsLoading.value = true
  try {
    versions.value = await api.chapters.versions(props.novelId, props.selectedChapterNum)
    // 自动选中第一个版本
    if (versions.value.length > 0 && !selectedVersionId.value) {
      selectVersion(versions.value[0])
    }
  } catch (e) {
    console.error('加载版本历史失败:', e)
  } finally {
    versionsLoading.value = false
  }
}

/** 选中版本并加载内容 */
async function selectVersion(version) {
  selectedVersionId.value = version.id
  selectedVersionLoading.value = true
  try {
    // 尝试加载完整内容用于 diff 对比
    const detail = await api.chapters.getVersion?.(props.novelId, props.selectedChapterNum, version.id)
    selectedVersionContent.value = detail?.content || version.preview || ''
  } catch {
    selectedVersionContent.value = version.preview || ''
  } finally {
    selectedVersionLoading.value = false
  }
}

/** 打开版本历史 */
async function open() {
  showVersions.value = true
  selectedVersionId.value = null
  await loadVersions()
}

/** 恢复版本 */
async function restoreVersion(version) {
  try {
    const ch = await api.chapters.restore(props.novelId, props.selectedChapterNum, version.id)
    store.currentChapter.content = ch.content
    store.currentChapter.wordCount = ch.wordCount
    showVersions.value = false
    versions.value = []
    await store.fetchChapters(props.novelId)
  } catch (e) {
    console.error('恢复版本失败:', e)
  }
}

/** 创建分支 */
async function createBranch() {
  try {
    const name = prompt('分支名称')
    if (name) {
      await api.chapters.fork(props.novelId, props.selectedChapterNum, name)
      loadVersions()
    }
  } catch {}
}

/** 简单的文本 diff：标记新增/删除 */
function computeDiff(oldText, newText) {
  if (!oldText && !newText) return { left: [], right: [] }

  const oldLines = (oldText || '').split('\n')
  const newLines = (newText || '').split('\n')
  const maxLen = Math.max(oldLines.length, newLines.length)

  const left = []
  const right = []

  for (let i = 0; i < maxLen; i++) {
    const oldLine = oldLines[i] || ''
    const newLine = newLines[i] || ''

    if (oldLine === newLine) {
      left.push({ text: oldLine, type: 'same' })
      right.push({ text: newLine, type: 'same' })
    } else {
      if (oldLine) left.push({ text: oldLine, type: 'removed' })
      if (newLine) right.push({ text: newLine, type: 'added' })
    }
  }

  return { left, right }
}

const diffResult = computed(() => {
  return computeDiff(selectedVersionContent.value, store.currentChapter?.content || '')
})

defineExpose({ open })
</script>

<template>
  <a-drawer
    :open="showVersions"
    @close="showVersions = false"
    width="720px"
    :body-style="{ padding: 0 }"
  >
    <template #title>
      <div class="flex items-center gap-2">
        <span class="font-medium">历史版本</span>
        <BranchSwitcher
          :novel-id="novelId"
          :chapter-number="selectedChapterNum"
          :active-branch="activeBranch"
          @update:active-branch="activeBranch = $event"
          @refresh="loadVersions"
        />
        <span class="text-xs text-caption ml-auto">
          {{ versions.length > 0 ? `对比当前版本与版本${versions.length}/共${versions.length}个` : '暂无版本' }}
        </span>
      </div>
    </template>

    <a-spin :spinning="versionsLoading">
      <div v-if="versions.length === 0 && !versionsLoading" class="text-center text-caption py-12">
        <p class="text-lg mb-2">暂无修改记录</p>
        <p class="text-xs">保存章节后会自动创建版本快照</p>
      </div>

      <div v-else class="flex h-full">
        <!-- 左侧：版本列表 -->
        <div class="w-[200px] border-r border-[var(--yunmo-border)] overflow-y-auto bg-[var(--yunmo-paper-light)]">
          <div
            v-for="v in versions"
            :key="v.id"
            class="px-3 py-2.5 border-b border-[var(--yunmo-border)] cursor-pointer transition-fast"
            :class="selectedVersionId === v.id ? 'bg-[var(--yunmo-accent)] text-[var(--yunmo-paper-light)]' : 'hover:bg-[var(--yunmo-paper-dark)]'"
            @click="selectVersion(v)"
          >
            <div class="flex items-center justify-between mb-0.5">
              <span class="text-xs font-medium">{{ v.createdAt?.substring(5, 16) || '未知时间' }}</span>
              <span class="text-[10px] font-tabular">{{ v.wordCount || 0 }}字</span>
            </div>
            <div class="flex items-center gap-1">
              <span class="text-[10px] opacity-60">桌面端</span>
            </div>
          </div>
        </div>

        <!-- 右侧：diff 对比视图 -->
        <div class="flex-1 flex overflow-hidden">
          <a-spin :spinning="selectedVersionLoading" class="flex-1">
            <!-- 左栏：选中版本 -->
            <div class="flex-1 border-r border-[var(--yunmo-border)] overflow-y-auto p-4">
              <div class="text-xs font-medium text-caption mb-2">
                第{{ selectedChapterNum }}章 {{ store.currentChapter?.title || '' }}
              </div>
              <div class="text-xs text-[var(--yunmo-text-secondary)] leading-relaxed">
                <template v-if="selectedVersion">
                  <p v-for="(line, i) in diffResult.left" :key="i"
                    class="whitespace-pre-wrap px-1 py-0.5 rounded mb-0.5"
                    :class="{
                      'diff-removed': line.type === 'removed',
                      'opacity-50': line.type === 'same'
                    }"
                  >{{ line.text || ' ' }}</p>
                </template>
                <p v-else class="text-caption text-center py-8">选择左侧版本进行对比</p>
              </div>
            </div>

            <!-- 右栏：当前版本 -->
            <div class="flex-1 overflow-y-auto p-4 bg-[var(--yunmo-paper)]">
              <div class="text-xs font-medium text-caption mb-2">
                第{{ selectedChapterNum }}章 {{ store.currentChapter?.title || '' }}
                <span class="text-[var(--yunmo-green)] ml-2">（当前版本）</span>
              </div>
              <div class="text-xs text-[var(--yunmo-text-secondary)] leading-relaxed">
                <template v-if="selectedVersion">
                  <p v-for="(line, i) in diffResult.right" :key="i"
                    class="whitespace-pre-wrap px-1 py-0.5 rounded mb-0.5"
                    :class="{
                      'diff-added': line.type === 'added',
                      'opacity-50': line.type === 'same'
                    }"
                  >{{ line.text || ' ' }}</p>
                </template>
                <p v-else class="text-caption text-center py-8">选择左侧版本进行对比</p>
              </div>
            </div>
          </a-spin>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <div v-if="selectedVersion" class="absolute bottom-0 left-0 right-0 px-4 py-3 border-t border-[var(--yunmo-border)] bg-[var(--yunmo-paper-light)] flex items-center justify-between">
        <button class="toolbar-btn text-xs" @click="createBranch">另存为分支</button>
        <button class="yunmo-btn !text-xs" @click="restoreVersion(selectedVersion)">恢复此版本</button>
      </div>
    </a-spin>
  </a-drawer>
</template>

<style scoped>
.diff-added {
  background: rgba(90, 122, 90, 0.15);
  color: #2d5a2d;
}
.diff-removed {
  background: rgba(179, 68, 58, 0.15);
  color: #8b2020;
  text-decoration: line-through;
  opacity: 0.7;
}
</style>
