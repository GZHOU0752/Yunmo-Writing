<script setup>
import { onMounted, onBeforeUnmount, ref, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWriteStore } from '@/composables/useWriteStore'
import { useApi } from '@/composables/useApi'
import ChapterEditor from '@/components/ChapterEditor.vue'
import GeneratePanel from '@/components/GeneratePanel.vue'
import OutlineTree from '@/components/outline/OutlineTree.vue'
import ReferenceMaterialList from '@/components/ReferenceMaterialList.vue'
import WritingStatsCard from '@/components/WritingStatsCard.vue'
import CharacterGraph from '@/components/CharacterGraph.vue'
import SearchReplaceModal from '@/components/SearchReplaceModal.vue'
import ImportModal from '@/components/ImportModal.vue'

import { message } from 'ant-design-vue'
import { useTheme } from '@/composables/useTheme'

const route = useRoute()
const router = useRouter()
const store = useWriteStore()
const api = useApi()
const { theme, toggle: toggleTheme } = useTheme()
const novelId = route.params.id

const selectedChapterNum = ref(1)
const leftTab = ref('chapters') // 'chapters' | 'outline' | 'relations'
const relationGraphOpen = ref(false)
const searchOpen = ref(false)
const importOpen = ref(false)
const focusInput = ref('')
const editingTitle = ref('')
const titleEditing = ref(false)
const titleInputRef = ref(null)
const addingChapter = ref(false)
const deletingChapter = ref(false)
let abortGeneration = null
let autoSaveTimer = null
const AUTO_SAVE_INTERVAL = 30_000 // 30 秒

/** 获取当前章节的草稿键 */
function draftKey() {
  return `yunmo-draft-${novelId}-${selectedChapterNum.value}`
}

const lastAutoSaved = ref('')

/** 自动保存草稿到 localStorage */
function autoSave() {
  const content = store.currentChapter?.content
  if (content) {
    localStorage.setItem(draftKey(), content)
    lastAutoSaved.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
}

/** 启动自动保存定时器 */
function startAutoSave() {
  stopAutoSave()
  autoSaveTimer = setInterval(autoSave, AUTO_SAVE_INTERVAL)
}

/** 停止自动保存 */
function stopAutoSave() {
  if (autoSaveTimer) { clearInterval(autoSaveTimer); autoSaveTimer = null }
}

/** 检查并恢复本地草稿 */
function restoreDraft() {
  const saved = localStorage.getItem(draftKey())
  if (saved && store.currentChapter && !store.currentChapter.content) {
    store.currentChapter.content = saved
  }
}

/** 清除当前章节草稿 */
function clearDraft() {
  localStorage.removeItem(draftKey())
}

// 版本历史
const showVersions = ref(false)
const versions = ref([])
const versionsLoading = ref(false)

/** Ctrl+S 快捷键保存 */
function handleKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    if (store.currentChapter?.content) {
      store.saveChapter(novelId, selectedChapterNum.value, store.currentChapter.content).then(clearDraft)
    }
  }
}
document.addEventListener('keydown', handleKeydown)

onMounted(async () => {
  await store.fetchChapters(novelId)
  if (store.chapters.length === 0) {
    await ensureChapter(1)
  }
  await store.loadChapter(novelId, selectedChapterNum.value)
  syncEditingTitle()
  restoreDraft()
  startAutoSave()
})

let chapterLoadTimer = null
watch(selectedChapterNum, (n) => {
  if (chapterLoadTimer) clearTimeout(chapterLoadTimer)
  // 切换前先保存旧章节的草稿（使用旧章节号对应的 draftKey）
  const oldCn = store.currentChapter?.chapterNumber
  const oldDraftKey = oldCn ? `yunmo-draft-${novelId}-${oldCn}` : null
  if (oldDraftKey && store.currentChapter?.content) {
    localStorage.setItem(oldDraftKey, store.currentChapter.content)
    lastAutoSaved.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  chapterLoadTimer = setTimeout(async () => {
    if (n) {
      await ensureChapter(n)
      await store.loadChapter(novelId, n)
      syncEditingTitle()
      restoreDraft()
    }
  }, 150)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
  if (chapterLoadTimer) clearTimeout(chapterLoadTimer)
  if (abortGeneration) abortGeneration()
  stopAutoSave()
  autoSave()
})

/** 确保章节存在，不存在则自动创建（支持任意章节号跳转） */
async function ensureChapter(n) {
  await store.fetchChapters(novelId)
  const exists = store.chapters.some(c => c.chapterNumber === n)
  if (!exists) {
    await api.chapters.create(novelId, n)
    await store.fetchChapters(novelId)
  }
}

function syncEditingTitle() {
  editingTitle.value = store.currentChapter?.title || `第${selectedChapterNum.value}章`
}

async function handleTitleSave() {
  const newTitle = editingTitle.value.trim()
  if (!newTitle) {
    editingTitle.value = store.currentChapter?.title || `第${selectedChapterNum.value}章`
    titleEditing.value = false
    return
  }
  try {
    await api.chapters.update(novelId, selectedChapterNum.value, { title: newTitle })
    if (store.currentChapter) store.currentChapter.title = newTitle
    await store.fetchChapters(novelId)
  } catch (e) {
    console.error('保存章节标题失败:', e)
  }
  titleEditing.value = false
}

/** 新增章节（防重复点击） */
async function addChapter() {
  if (addingChapter.value) return
  addingChapter.value = true
  try {
    const ch = await api.chapters.create(novelId)
    await store.fetchChapters(novelId)
    selectedChapterNum.value = ch.chapterNumber
  } catch (e) {
    console.error('新增章节失败:', e)
  } finally {
    addingChapter.value = false
  }
}

/** 删除当前章节 */
async function deleteCurrentChapter() {
  if (store.chapters.length <= 1) return
  deletingChapter.value = true
  try {
    const cn = selectedChapterNum.value
    await api.chapters.delete(novelId, cn)
    await store.fetchChapters(novelId)
    if (store.currentChapter) store.currentChapter = null
    // 自动跳到前一个章节
    selectedChapterNum.value = Math.max(1, cn - 1)
  } catch (e) {
    console.error('删除章节失败:', e)
  } finally {
    deletingChapter.value = false
  }
}

async function generate() {
  if (store.sseStatus === 'generating' || store.sseStatus === 'reviewing') {
    abortGeneration?.()
    return
  }
  try {
    const { abort } = await store.generateChapter(novelId, selectedChapterNum.value, focusInput.value)
    abortGeneration = abort
  } catch (e) {
    console.error('生成章节失败:', e)
  }
}

async function loadVersions() {
  versionsLoading.value = true
  try {
    versions.value = await api.chapters.versions(novelId, selectedChapterNum.value)
  } catch (e) {
    console.error('加载版本历史失败:', e)
  } finally {
    versionsLoading.value = false
  }
}

/** 复制本章纯文本到剪贴板 */
async function copyChapter() {
  const content = store.currentChapter?.content
  if (!content) { message.warn('暂无内容可复制'); return }
  // 使用 DOMParser 安全提取纯文本（不会触发外部资源加载）
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

async function handleOpenVersions() {
  showVersions.value = true
  await loadVersions()
}

async function restoreVersion(version) {
  try {
    const ch = await api.chapters.restore(novelId, selectedChapterNum.value, version.id)
    store.currentChapter.content = ch.content
    store.currentChapter.wordCount = ch.wordCount
    showVersions.value = false
    versions.value = []
    // 刷新章节列表以更新字数显示
    await store.fetchChapters(novelId)
  } catch (e) {
    console.error('恢复版本失败:', e)
  }
}
</script>

<template>
  <div class="min-h-[100dvh] flex">
    <!-- 左栏：章节列表 / 大纲树 -->
    <aside class="w-52 bg-[var(--yunmo-paper-light)] border-r border-[var(--yunmo-border)] overflow-y-auto flex flex-col">
      <!-- 标签切换 -->
      <div class="flex border-b border-[var(--yunmo-border)]">
        <div
          class="flex-1 text-center py-2 text-xs cursor-pointer font-semibold"
          :class="leftTab === 'chapters' ? 'border-b-2 text-[var(--yunmo-accent)]' : 'text-caption'"
          :style="leftTab === 'chapters' ? { borderColor: 'var(--yunmo-accent)' } : {}"
          @click="leftTab = 'chapters'"
        >章节</div>
        <div
          class="flex-1 text-center py-2 text-xs cursor-pointer font-semibold"
          :class="leftTab === 'outline' ? 'border-b-2 text-[var(--yunmo-accent)]' : 'text-caption'"
          :style="leftTab === 'outline' ? { borderColor: 'var(--yunmo-accent)' } : {}"
          @click="leftTab = 'outline'"
        >大纲</div>
        <div
          class="flex-1 text-center py-2 text-xs cursor-pointer font-semibold"
          :class="leftTab === 'relations' ? 'border-b-2 text-[var(--yunmo-accent)]' : 'text-caption'"
          :style="leftTab === 'relations' ? { borderColor: 'var(--yunmo-accent)' } : {}"
          @click="leftTab = 'relations'; relationGraphOpen = true"
        >关系</div>
      </div>

      <!-- 章节面板 -->
      <div v-if="leftTab === 'chapters'" class="p-3 flex-1 flex flex-col">
        <div class="flex items-center justify-between mb-3">
          <h3 class="text-sm font-semibold" style="color:var(--yunmo-accent)">我的章节</h3>
          <a-button size="small" type="text" @click="addChapter" title="新增章节">
            <span class="text-lg leading-none">+</span>
          </a-button>
        </div>
        <div class="flex-1">
          <div
            v-for="(ch, idx) in store.chapters"
            :key="ch.chapterNumber"
            class="px-3 py-2 rounded cursor-pointer text-sm mb-0.5 flex items-center justify-between group"
            :class="selectedChapterNum === ch.chapterNumber
              ? 'bg-[var(--yunmo-accent)] text-[var(--yunmo-paper-light)]'
              : 'hover:bg-[var(--yunmo-paper-dark)]'"
            :style="{ animationDelay: idx * 30 + 'ms' }"
            style="animation: chapterSlideIn 0.35s cubic-bezier(0.16,1,0.3,1) both"
            @click="selectedChapterNum = ch.chapterNumber"
          >
            <span class="truncate">{{ ch.title || '第' + ch.chapterNumber + '章' }}</span>
            <span class="text-xs ml-1 shrink-0 flex items-center gap-1" style="opacity:0.65">
              <span>{{ ch.wordCount || 0 }} 字</span>
              <a-popconfirm
                v-if="store.chapters.length > 1"
                title="确定删除该章节？"
                ok-text="删除"
                cancel-text="取消"
                placement="right"
                @confirm="async () => { selectedChapterNum = ch.chapterNumber; await deleteCurrentChapter(); }"
              >
                <span
                  class="text-xs cursor-pointer opacity-0 group-hover:opacity-100 transition-opacity"
                  :class="selectedChapterNum === ch.chapterNumber ? 'text-[var(--yunmo-paper-light)]' : 'text-[var(--yunmo-red)]'"
                  @click.stop
                >✕</span>
              </a-popconfirm>
            </span>
          </div>
          <div v-if="store.chapters.length === 0" class="text-caption p-2">
            点击 + 号新增章节，或使用 AI 生成
          </div>
        </div>
      </div>

      <!-- 大纲面板 -->
      <div v-if="leftTab === 'outline'" class="p-2 flex-1">
        <OutlineTree
          :novel-id="novelId"
          :chapters="store.chapters"
          @select-chapter="(cn) => { selectedChapterNum = cn; leftTab = 'chapters' }"
        />
      </div>
    </aside>

    <!-- 中栏：编辑器 -->
    <main class="flex-1 flex flex-col min-w-0">
      <header class="h-12 border-b border-[var(--yunmo-border)] flex items-center px-4 gap-3">
        <a-button size="small" type="text" class="toolbar-btn" @click="router.push('/dashboard')">← 书房</a-button>
        <a-button size="small" type="text" class="toolbar-btn" @click="router.push(`/novels/${novelId}/read`)">阅读</a-button>
        <div class="w-px h-5" style="background:var(--yunmo-border)" />
        <!-- 可编辑章节标题 -->
        <div class="flex items-center gap-1">
          <span class="text-sm text-[var(--yunmo-text-secondary)] shrink-0">第 {{ selectedChapterNum }} 章</span>
          <input
            v-if="titleEditing"
            ref="titleInputRef"
            v-model="editingTitle"
            class="text-sm font-semibold border-b border-[var(--yunmo-accent)] bg-transparent px-1 py-0.5 outline-none"
            style="min-width:120px"
            @blur="handleTitleSave"
            @keydown.enter="handleTitleSave"
          />
          <span
            v-else
            class="text-sm font-semibold cursor-pointer border-b border-transparent hover:border-[var(--yunmo-border)] px-1 py-0.5"
            @click="titleEditing = true; nextTick(() => titleInputRef?.focus())"
          >{{ store.currentChapter?.title || '第' + selectedChapterNum + '章' }}</span>
        </div>
        <a-tag v-if="store.currentChapter" color="blue">
          {{ store.currentChapter.wordCount || 0 }} 字
        </a-tag>
        <div class="flex-1" />
        <a-button size="small" type="text" class="toolbar-btn" @click="toggleTheme" :title="theme === 'dark' ? '日间模式' : '墨夜模式'">{{ theme === 'dark' ? '素' : '墨' }}</a-button>
        <span v-if="lastAutoSaved" class="text-xs" style="color:var(--yunmo-text-caption)">草稿 {{ lastAutoSaved }}</span>
        <div class="w-px h-5 mx-2" style="background:var(--yunmo-border)" />
        <a-button size="small" type="text" class="toolbar-btn" @click="handleOpenVersions">修订</a-button>
        <a-button size="small" type="text" class="toolbar-btn" @click="copyChapter">复制</a-button>
        <a-dropdown>
          <a-button size="small" type="text" class="toolbar-btn">导出</a-button>
          <template #overlay>
            <a-menu @click="({ key }) => { try { api.export[key](novelId) } catch {} }">
              <a-menu-item key="epub">EPUB 电子书</a-menu-item>
              <a-menu-item key="txt">TXT 文本</a-menu-item>
              <a-menu-item key="md">Markdown</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <a-button size="small" type="text" class="toolbar-btn" @click="importOpen = true">导入</a-button>
        <a-button size="small" type="text" class="toolbar-btn" @click="searchOpen = true">检索</a-button>
        <a-button
          type="primary"
          size="small"
          :disabled="!store.currentChapter?.content"
          @click="async () => { try { await store.saveChapter(novelId, selectedChapterNum, store.currentChapter?.content || ''); clearDraft(); } catch {} }"
        >
          保存本章
        </a-button>
      </header>
      <!-- 码字统计 -->
      <WritingStatsCard :novel-id="novelId" class="mx-4 mt-2" />
      <div class="flex-1 overflow-y-auto p-6 md:p-10">
        <ChapterEditor
          :content="store.currentChapter?.content || ''"
          @update:content="(v) => { if (store.currentChapter) store.currentChapter.content = v }"
        />
      </div>
    </main>

    <!-- 右栏：参考素材 + AI 助手 -->
    <aside class="w-80 border-l border-[var(--yunmo-border)] bg-[var(--yunmo-paper-light)] p-4 overflow-y-auto flex flex-col gap-4">
      <ReferenceMaterialList :novel-id="novelId" />
      <GeneratePanel
        :novel-id="novelId"
        :chapter-number="selectedChapterNum"
        :sse-status="store.sseStatus"
        :streamed-text="store.streamedText"
        :quality-report="store.qualityReport"
        :focus="focusInput"
        @update:focus="focusInput = $event"
        @generate="generate"
      />
    </aside>

    <!-- 导入外部文稿 -->
    <ImportModal :novel-id="novelId" v-model:open="importOpen" @imported="store.fetchChapters(novelId)" />

    <!-- 全文搜索替换 -->
    <SearchReplaceModal :novel-id="novelId" v-model:open="searchOpen" @jump-to="(cn) => selectedChapterNum = cn" />

    <!-- 角色关系图谱 -->
    <CharacterGraph :novel-id="novelId" v-model:open="relationGraphOpen" />

    <!-- 版本历史抽屉 -->
    <a-drawer
      title="修改记录"
      :open="showVersions"
      @close="showVersions = false"
      width="400px"
    >
      <a-spin :spinning="versionsLoading">
        <div v-if="versions.length === 0 && !versionsLoading" class="text-center text-caption py-8">
          还没有保存过修改
        </div>
        <a-timeline v-else>
          <a-timeline-item
            v-for="v in versions"
            :key="v.id"
          >
            <div class="text-sm">
              <div class="flex items-center gap-2 mb-1">
                <a-tag color="blue">V{{ v.versionNumber }}</a-tag>
                <span class="text-caption">{{ v.createdAt?.substring(0, 19) }}</span>
                <span class="text-caption">{{ v.wordCount || 0 }} 字</span>
              </div>
              <div class="text-xs text-[var(--yunmo-text-secondary)] bg-[var(--yunmo-paper-light)] p-2 rounded mb-2 whitespace-pre-wrap">
                {{ v.preview }}
              </div>
              <a-button size="small" @click="restoreVersion(v)">恢复此版本</a-button>
            </div>
          </a-timeline-item>
        </a-timeline>
      </a-spin>
    </a-drawer>
  </div>
</template>
