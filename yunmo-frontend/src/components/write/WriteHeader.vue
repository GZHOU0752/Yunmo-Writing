<script setup>
/**
 * 写作工作区顶部工具栏
 * 包含面包屑、可编辑标题、字数统计、操作按钮
 */
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useWriteStore } from '@/composables/useWriteStore'
import { useApi } from '@/composables/useApi'
import { message } from 'ant-design-vue'

const api = useApi()

const props = defineProps({
  novelId: { type: String, required: true },
  selectedChapterNum: { type: Number, required: true },
  novelInfo: { type: Object, default: null },
  lastAutoSaved: { type: String, default: '' },
  focusMode: { type: Boolean, default: false },
})

const emit = defineEmits([
  'toggle-focus-mode',
  'open-versions',
  'open-search',
  'open-import',
  'save',
])

const router = useRouter()
const store = useWriteStore()

// 标题编辑
const editingTitle = ref('')
const titleEditing = ref(false)
const titleInputRef = ref(null)

/** 同步编辑标题 */
function syncEditingTitle() {
  editingTitle.value = store.currentChapter?.title || `第${props.selectedChapterNum}章`
}

/** 保存标题 */
async function handleTitleSave() {
  const newTitle = editingTitle.value.trim()
  if (!newTitle) {
    editingTitle.value = store.currentChapter?.title || `第${props.selectedChapterNum}章`
    titleEditing.value = false
    return
  }
  try {
    await api.chapters.update(props.novelId, props.selectedChapterNum, { title: newTitle })
    if (store.currentChapter) store.currentChapter.title = newTitle
    await store.fetchChapters(props.novelId)
  } catch (e) {
    console.error('保存章节标题失败:', e)
  }
  titleEditing.value = false
}

/** 开始编辑标题 */
function startEditTitle() {
  titleEditing.value = true
  nextTick(() => titleInputRef.value?.focus())
}

/** 复制本章纯文本到剪贴板 */
async function copyChapter() {
  const content = store.currentChapter?.content
  if (!content) { message.warn('暂无内容可复制'); return }
  const parser = new DOMParser()
  const doc = parser.parseFromString(content, 'text/html')
  const text = doc.body?.textContent || ''
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败，请手动复制')
  }
}

/** 导出文件 */
async function handleExport({ key }) {
  try {
    api.export[key](props.novelId)
  } catch {}
}

/** 保存章节 */
async function handleSave() {
  emit('save')
}

// 暴露给父组件
defineExpose({ syncEditingTitle })
</script>

<template>
  <header class="h-12 border-b border-[var(--yunmo-border)] flex items-center px-4 gap-3 bg-[var(--yunmo-paper-light)] flex-shrink-0 text-sm">
    <!-- 左侧：面包屑 + 标题 -->
    <div class="flex items-center gap-1.5 min-w-0">
      <button class="toolbar-btn !px-1.5 !py-0.5 text-xs" @click="router.push('/dashboard')">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /><polyline points="9 22 9 12 15 12 15 22" />
        </svg>
      </button>
      <span class="text-xs text-caption truncate max-w-[100px]">{{ novelInfo?.title || '未命名' }}</span>
      <span class="text-xs" style="color:var(--yunmo-border)">·</span>

      <!-- 可编辑章节标题 -->
      <div class="flex items-center">
        <input
          v-if="titleEditing"
          ref="titleInputRef"
          v-model="editingTitle"
          class="text-sm font-medium border-b border-[var(--yunmo-accent)] bg-transparent px-1 py-0 outline-none max-w-[180px]"
          @blur="handleTitleSave"
          @keydown.enter="handleTitleSave"
        />
        <span
          v-else
          class="font-medium cursor-pointer hover:text-[var(--yunmo-accent)] transition-fast truncate max-w-[180px]"
          @click="startEditTitle"
        >{{ store.currentChapter?.title || '第' + selectedChapterNum + '章' }}</span>
      </div>

      <a-tag color="blue" class="font-tabular !text-[10px] !ml-1">
        {{ (store.currentChapter?.wordCount || 0).toLocaleString() }}字
      </a-tag>

      <!-- AI 生成状态 -->
      <span
        v-if="store.sseStatus === 'generating' || store.sseStatus === 'reviewing'"
        class="w-1.5 h-1.5 rounded-full status-pulse shrink-0"
        :class="store.sseStatus === 'generating' ? 'bg-[var(--yunmo-gold)]' : 'bg-[var(--yunmo-accent-light)]'"
      />
    </div>

    <div class="flex-1" />

    <!-- 右侧：操作按钮组 -->
    <div class="flex items-center gap-0.5">
      <span v-if="lastAutoSaved" class="text-[10px] font-tabular text-caption mr-1">{{ lastAutoSaved }}</span>

      <!-- 撤销/重做 -->
      <button class="toolbar-btn !p-2" title="撤销 (Ctrl+Z)" @click="document.execCommand('undo')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M3 7v6h6" /><path d="M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13" />
        </svg>
      </button>
      <button class="toolbar-btn !p-2" title="重做 (Ctrl+Shift+Z)" @click="document.execCommand('redo')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 7v6h-6" /><path d="M3 17a9 9 0 0 1 9-9 9 9 0 0 1 6 2.3L21 13" />
        </svg>
      </button>

      <div class="w-px h-4 mx-1" style="background:var(--yunmo-border)" />

      <!-- 查找替换 -->
      <button class="toolbar-btn !p-2" title="查找替换 (Ctrl+F)" @click="emit('open-search')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
      </button>

      <!-- 导入 -->
      <button class="toolbar-btn !p-2" title="导入文稿" @click="emit('open-import')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" />
        </svg>
      </button>

      <!-- 导出下拉 -->
      <a-dropdown class="hidden md:block">
        <button class="toolbar-btn !p-2" title="导出">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" />
          </svg>
        </button>
        <template #overlay>
          <a-menu @click="handleExport">
            <a-menu-item key="epub">EPUB 电子书</a-menu-item>
            <a-menu-item key="txt">TXT 文本</a-menu-item>
            <a-menu-item key="html">HTML 网页</a-menu-item>
            <a-menu-item key="md">Markdown</a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>

      <div class="w-px h-4 mx-1" style="background:var(--yunmo-border)" />

      <!-- 版本历史 -->
      <button class="toolbar-btn !p-2" title="版本历史" @click="emit('open-versions')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
        </svg>
      </button>

      <!-- 复制 -->
      <button class="toolbar-btn !p-2" title="复制全文" @click="copyChapter">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="9" y="9" width="13" height="13" rx="2" ry="2" /><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
        </svg>
      </button>

      <!-- 阅读模式 -->
      <button class="toolbar-btn !p-2" title="阅读模式" @click="router.push(`/novels/${novelId}/read`)">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" /><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
        </svg>
      </button>

      <div class="w-px h-4 mx-1" style="background:var(--yunmo-border)" />

      <!-- 专注模式 -->
      <button
        class="toolbar-btn !p-2"
        :class="{ '!text-[var(--yunmo-accent)]': focusMode }"
        title="专注模式 (Ctrl+\\)"
        @click="emit('toggle-focus-mode')"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z" /><circle cx="12" cy="12" r="3" />
        </svg>
      </button>

      <!-- 保存 -->
      <button
        class="yunmo-btn !text-xs !px-3 !py-1"
        :disabled="!store.currentChapter?.content"
        @click="handleSave"
      >
        保存
      </button>
    </div>
  </header>
</template>
