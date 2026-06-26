<script setup>
import { onMounted, onBeforeUnmount, ref, watch, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWriteStore } from '@/composables/useWriteStore'
import { useApi } from '@/composables/useApi'
import ChapterEditor from '@/components/ChapterEditor.vue'
import GeneratePanel from '@/components/GeneratePanel.vue'
import OutlinePanel from '@/components/outline/OutlinePanel.vue'
import ReferenceMaterialList from '@/components/ReferenceMaterialList.vue'
import WritingStatsCard from '@/components/WritingStatsCard.vue'
import CharacterCard from '@/components/CharacterCard.vue'
import BranchSwitcher from '@/components/BranchSwitcher.vue'
import OutlineDiscussion from '@/components/OutlineDiscussion.vue'
import SearchReplaceModal from '@/components/SearchReplaceModal.vue'
import ImportModal from '@/components/ImportModal.vue'
import AIChatPanel from '@/components/AIChatPanel.vue'
import CharacterProgressPanel from '@/components/CharacterProgressPanel.vue'
import EventTimeline from '@/components/EventTimeline.vue'
import StyleImitationPanel from '@/components/StyleImitationPanel.vue'
import DeAIScoreBadge from '@/components/DeAIScoreBadge.vue'
import HookPreviewPanel from '@/components/HookPreviewPanel.vue'
import ChapterControlCard from '@/components/ChapterControlCard.vue'
import MarathonControlPanel from '@/components/MarathonControlPanel.vue'
import { useKeyboardShortcuts } from '@/composables/useKeyboardShortcuts'

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
const searchOpen = ref(false)
const importOpen = ref(false)
const chapterSearchQuery = ref('')
const refMaterialsRef = ref(null)
const refMaterialsCount = computed(() => refMaterialsRef.value?.materials?.length || 0)
// 角色列表
const characters = ref([])
const selectedCharId = ref(null)
const characterCardOpen = ref(false)
const editingTitle = ref('')
const titleEditing = ref(false)
const titleInputRef = ref(null)
const addingChapter = ref(false)
const deletingChapter = ref(false)
const editorKey = ref(0)  // 生成完成后递增，强制ChapterEditor重新挂载
let abortGeneration = null
let autoSaveTimer = null
const AUTO_SAVE_INTERVAL = 30_000 // 30 秒

// 折叠 & 专注模式
const leftCollapsed = ref(false)
const rightCollapsed = ref(false)
const focusMode = ref(false)
const statsBarOpen = ref(true)
const rightPanelSections = ref({
  ai: true,
  control: false,
  deai: true,
  hooks: true,
  discuss: false,
  references: true,
  characters: true,
  marathon: false,
})

// 章节预加载缓存
const chapterCache = new Map()

/** 后台预加载相邻章节 */
async function preloadAdjacentChapters() {
  const cn = selectedChapterNum.value
  const toPreload = [cn - 1, cn + 1].filter(n =>
    n >= 1 && !chapterCache.has(n) && store.chapters.some(c => c.chapterNumber === n)
  )
  for (const n of toPreload) {
    try {
      const ch = await api.chapters.get(novelId, n)
      chapterCache.set(n, ch)
    } catch { /* 静默失败 */ }
  }
}

// 监听生成完成，自动注入内容到编辑器
watch(() => store.sseStatus, async (status) => {
  if (status === 'done' && store.streamedText) {
    if (store.currentChapter && !store.currentChapter.content) {
      store.currentChapter.content = store.streamedText
    }
    await nextTick()
    editorKey.value++
    if (store.currentChapter) {
      chapterCache.set(store.currentChapter.chapterNumber, { ...store.currentChapter })
      preloadAdjacentChapters()
    }
    nextTick(() => clearDraft())
  }
})

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
const activeBranch = ref('main')
const outlineDiscussOpen = ref(false)
const discussNodeId = ref(null)

// 小说信息（面包屑用）
const novelInfo = ref(null)
const currentWritingStyle = ref('')

/** 文风更新回调 */
async function handleStyleUpdated(styleData) {
  try {
    const styleSummary = styleData.analysis?.style_summary || styleData.analysis?.summary || ''
    // 后端 analyze-style 已经自动保存到 novel.writingStyle
    // 这里同步前端状态
    currentWritingStyle.value = styleSummary
    if (novelInfo.value) novelInfo.value.writingStyle = styleSummary
  } catch (e) {
    console.error('保存文风失败:', e)
  }
}

/** AI 聊天插入文本到编辑器 */
function handleChatInsert(text) {
  if (store.currentChapter) {
    const currentContent = store.currentChapter.content || ''
    store.currentChapter.content = currentContent + '\n\n' + text
    editorKey.value++ // 强制编辑器重新渲染
  }
}

/** Ctrl+S 快捷键保存 — 已由 useKeyboardShortcuts 接管 */

onMounted(async () => {
  await store.fetchChapters(novelId)
  if (store.chapters.length === 0) {
    await ensureChapter(1)
  }
  await store.loadChapter(novelId, selectedChapterNum.value)
  syncEditingTitle()
  restoreDraft()
  startAutoSave()
  loadCharacters()
  // 加载小说信息
  try {
    novelInfo.value = await api.novels.get(novelId)
    currentWritingStyle.value = novelInfo.value?.writingStyle || ''
  } catch {}
})

async function loadCharacters() {
  try { characters.value = await api.characters.list(novelId) } catch {}
}

const selectedCharacter = computed(() =>
  characters.value.find(c => c.id === selectedCharId.value) || null
)

let chapterLoadTimer = null
watch(selectedChapterNum, (n) => {
  if (chapterLoadTimer) clearTimeout(chapterLoadTimer)
  const oldCn = store.currentChapter?.chapterNumber
  const oldDraftKey = oldCn ? `yunmo-draft-${novelId}-${oldCn}` : null
  if (oldDraftKey && store.currentChapter?.content) {
    localStorage.setItem(oldDraftKey, store.currentChapter.content)
    lastAutoSaved.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  chapterLoadTimer = setTimeout(async () => {
    if (n) {
      await ensureChapter(n)
      const cached = chapterCache.get(n)
      if (cached) {
        store.currentChapter = cached
        syncEditingTitle()
        restoreDraft()
        preloadAdjacentChapters()
        return
      }
      await store.loadChapter(novelId, n)
      if (store.currentChapter) chapterCache.set(n, store.currentChapter)
      syncEditingTitle()
      restoreDraft()
      preloadAdjacentChapters()
    }
  }, 100)
})

onBeforeUnmount(() => {
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
    const { abort } = await store.generateChapter(novelId, selectedChapterNum.value, '')
    abortGeneration = abort
  } catch (e) {
    console.error('生成章节失败:', e)
  }
}

function clearCheckpoint() {
  store.checkpoint = null
  api.chapters.clearCheckpoint(novelId, selectedChapterNum.value).catch(() => {})
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
    await store.fetchChapters(novelId)
  } catch (e) {
    console.error('恢复版本失败:', e)
  }
}

/** 章节状态徽章颜色 */
function chapterStatusColor(ch) {
  if (ch.status === 'GENERATED' || ch.status === 'FINALIZED') return 'var(--yunmo-green)'
  if (ch.status === 'DRAFT' || ch.status === 'IN_PROGRESS') return 'var(--yunmo-amber)'
  return 'var(--yunmo-text-caption)'
}

/** 删除章节（带防重复点击保护 + 阻止 watcher 自动重建） */
async function handleDeleteChapter(cn) {
  if (!confirm(`确定删除第${cn}章？此操作不可恢复。`)) return
  if (deletingChapter.value) return
  deletingChapter.value = true

  // 清除章节切换定时器，防止 watcher 在删除期间触发 ensureChapter 重建
  if (chapterLoadTimer) { clearTimeout(chapterLoadTimer); chapterLoadTimer = null }

  try {
    await api.chapters.delete(novelId, cn)
    await store.fetchChapters(novelId)

    // 清理当前章节引用
    if (store.currentChapter) {
      store.currentChapter = null
      chapterCache.delete(cn)
    }

    // 跳到前一个章节（至少为1）
    const targetCn = Math.max(1, cn - 1)
    selectedChapterNum.value = targetCn

    // 确保目标章节存在（如果是唯一的章节被删，自动创建第1章）
    await ensureChapter(targetCn)
    await store.loadChapter(novelId, targetCn)
    syncEditingTitle()
    restoreDraft()
  } catch (e) {
    message.error('删除失败')
    console.error('删除章节失败:', e)
  } finally {
    deletingChapter.value = false
  }
}

/** 章节列表搜索过滤 */
const filteredChapters = computed(() => {
  if (!chapterSearchQuery.value.trim()) return store.chapters
  const q = chapterSearchQuery.value.trim().toLowerCase()
  return store.chapters.filter(ch =>
    (ch.title || '').toLowerCase().includes(q) ||
    `第${ch.chapterNumber}章`.includes(q) ||
    ch.chapterNumber.toString().includes(q)
  )
})

/** 快捷键系统 */
useKeyboardShortcuts({
  onSave: () => {
    if (store.currentChapter?.content) {
      store.saveChapter(novelId, selectedChapterNum.value, store.currentChapter.content).then(clearDraft)
    }
  },
  onPrevChapter: () => {
    if (selectedChapterNum.value > 1) {
      selectedChapterNum.value--
    }
  },
  onNextChapter: () => {
    if (selectedChapterNum.value < store.chapters.length) {
      selectedChapterNum.value++
    }
  },
})
</script>

<template>
  <div class="min-h-[100dvh] flex" :class="{ 'focus-mode': focusMode }">
    <!-- 左栏折叠标签 -->
    <div
      v-if="leftCollapsed"
      class="w-8 bg-[var(--yunmo-paper-light)] border-r border-[var(--yunmo-border)] flex flex-col items-center py-3 cursor-pointer hover:bg-[var(--yunmo-paper-dark)] transition-spring flex-shrink-0"
      @click="leftCollapsed = false"
      title="展开章节列表"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-secondary)" stroke-width="2">
        <path d="M15 18l-6-6 6-6" />
      </svg>
    </div>

    <!-- 左栏：章节列表 / 大纲树 -->
    <aside v-show="!leftCollapsed" class="w-56 bg-[var(--yunmo-paper-light)] border-r border-[var(--yunmo-border)] flex flex-col flex-shrink-0 transition-all duration-300">
      <!-- 折叠按钮 -->
      <div class="absolute z-10" style="right:-12px;top:50%;transform:translateY(-50%)">
        <button
          class="w-6 h-12 bg-[var(--yunmo-paper-light)] border border-[var(--yunmo-border)] rounded-r-md flex items-center justify-center hover:bg-[var(--yunmo-paper-dark)] transition-fast"
          @click="leftCollapsed = true"
          title="折叠侧栏"
        >
          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5">
            <path d="M15 18l-6-6 6-6" />
          </svg>
        </button>
      </div>

      <!-- 标签切换 -->
      <div class="flex border-b border-[var(--yunmo-border)] px-2">
        <div
          class="flex-1 text-center py-2.5 text-xs cursor-pointer transition-fast rounded-t"
          :class="leftTab === 'chapters'
            ? 'text-[var(--yunmo-accent)] font-semibold'
            : 'text-caption hover:text-[var(--yunmo-text-secondary)]'"
          :style="leftTab === 'chapters' ? { borderBottom: '2px solid var(--yunmo-accent)', marginBottom: '-1px' } : {}"
          @click="leftTab = 'chapters'"
        >章 节</div>
        <div
          class="flex-1 text-center py-2.5 text-xs cursor-pointer transition-fast rounded-t"
          :class="leftTab === 'outline'
            ? 'text-[var(--yunmo-accent)] font-semibold'
            : 'text-caption hover:text-[var(--yunmo-text-secondary)]'"
          :style="leftTab === 'outline' ? { borderBottom: '2px solid var(--yunmo-accent)', marginBottom: '-1px' } : {}"
          @click="leftTab = 'outline'"
        >大 纲</div>
        <div
          class="flex-1 text-center py-2.5 text-xs cursor-pointer transition-fast rounded-t"
          :class="leftTab === 'relations'
            ? 'text-[var(--yunmo-accent)] font-semibold'
            : 'text-caption hover:text-[var(--yunmo-text-secondary)]'"
          :style="leftTab === 'relations' ? { borderBottom: '2px solid var(--yunmo-accent)', marginBottom: '-1px' } : {}"
          @click="leftTab = 'relations'"
        >关 系</div>
      </div>

      <!-- 章节面板 -->
      <div v-if="leftTab === 'chapters'" class="p-3 flex-1 flex flex-col overflow-hidden">
        <div class="flex items-center justify-between mb-2">
          <h3 class="text-sm font-semibold" style="color:var(--yunmo-accent)">章节</h3>
          <a-button size="small" type="text" @click="addChapter" :disabled="addingChapter" title="新增章节">
            <span class="text-base leading-none">+</span>
          </a-button>
        </div>
        <!-- 章节搜索 -->
        <a-input
          v-model:value="chapterSearchQuery"
          placeholder="搜索章节..."
          size="small"
          allow-clear
          class="mb-2"
        />
        <!-- 章节时间线 -->
        <div class="flex-1 chapter-timeline overflow-y-auto">
          <div
            v-for="(ch, idx) in filteredChapters"
            :key="ch.chapterNumber"
            class="chapter-timeline-item"
            :class="{ 'active': selectedChapterNum === ch.chapterNumber }"
            :style="{ animationDelay: idx * 30 + 'ms' }"
            style="animation: chapterSlideIn 0.3s cubic-bezier(0.16,1,0.3,1) both"
            @click="selectedChapterNum = ch.chapterNumber"
          >
            <div
              class="px-3 py-2.5 rounded-md cursor-pointer text-sm group transition-spring"
              :class="selectedChapterNum === ch.chapterNumber
                ? 'bg-[var(--yunmo-accent)] text-[var(--yunmo-paper-light)] shadow-sm'
                : 'hover:bg-[var(--yunmo-paper-dark)]'"
              :style="selectedChapterNum === ch.chapterNumber ? { boxShadow: '0 2px 8px rgba(139,58,58,0.2)' } : {}"
            >
              <div class="flex items-center justify-between gap-1.5">
                <!-- 状态圆点 -->
                <span
                  class="w-2 h-2 rounded-full shrink-0"
                  :style="{ background: chapterStatusColor(ch) }"
                  :title="ch.status || '未开始'"
                />
                <span class="truncate text-[13px] font-medium flex-1">{{ ch.title || '第' + ch.chapterNumber + '章' }}</span>
                <span
                  v-if="store.chapters.length > 1"
                  class="text-xs shrink-0 cursor-pointer opacity-0 group-hover:opacity-100 transition-fast"
                  :class="selectedChapterNum === ch.chapterNumber ? 'text-[var(--yunmo-paper-light)]' : 'text-[var(--yunmo-red)]'"
                  @click.stop="handleDeleteChapter(ch.chapterNumber)"
                >&times;</span>
              </div>
              <!-- 字数 + 进度条 -->
              <div class="flex items-center gap-2 mt-1">
                <span
                  class="text-xs font-tabular"
                  :class="selectedChapterNum === ch.chapterNumber ? 'opacity-75' : ''"
                  style="opacity:0.55"
                >
                  {{ (ch.wordCount || 0).toLocaleString() }} 字
                </span>
                <div v-if="ch.targetWordCount" class="flex-1 novel-progress" style="height:2px">
                  <div
                    class="novel-progress-bar"
                    :style="{
                      width: `${Math.min(((ch.wordCount || 0) / ch.targetWordCount) * 100, 100)}%`,
                      background: selectedChapterNum === ch.chapterNumber ? 'rgba(250,246,237,0.5)' : 'var(--yunmo-accent-light)',
                    }"
                  />
                </div>
              </div>
            </div>
          </div>
          <div v-if="store.chapters.length === 0" class="text-caption p-2 text-xs">
            墨已备好，点 + 新建章节，或让 AI 为你着笔
          </div>
        </div>
      </div>

      <!-- 大纲面板 -->
      <div v-if="leftTab === 'outline'" class="flex-1 overflow-y-auto">
        <OutlinePanel
          :novel-id="novelId"
          :chapters="store.chapters"
          :active-chapter="selectedChapterNum"
          @select-chapter="(cn) => { selectedChapterNum = cn; leftTab = 'chapters' }"
        />
      </div>

      <!-- 关系面板 -->
      <div v-if="leftTab === 'relations'" class="flex-1 overflow-y-auto p-3 space-y-4">
        <CharacterProgressPanel
          :novel-id="novelId"
          :chapters="store.chapters"
          :current-chapter="selectedChapterNum"
        />
        <hr class="ink-divider" />
        <EventTimeline
          :chapters="store.chapters"
          :current-chapter="selectedChapterNum"
          @select-chapter="(cn) => selectedChapterNum = cn"
        />
      </div>
    </aside>

    <!-- 中栏：编辑器 -->
    <main class="flex-1 flex flex-col min-w-0 overflow-hidden">
      <!-- 头部 -->
      <header class="h-12 border-b border-[var(--yunmo-border)] flex items-center px-4 gap-3 bg-[var(--yunmo-paper-light)] flex-shrink-0">
        <!-- 面包屑 -->
        <div class="flex items-center gap-1.5 text-sm">
          <span class="text-xs cursor-pointer hover:text-[var(--yunmo-accent)] transition-fast"
                style="color:var(--yunmo-text-caption)"
                @click="router.push('/dashboard')">
            书房
          </span>
          <span class="text-xs" style="color:var(--yunmo-border)">/</span>
          <span class="text-xs font-medium truncate max-w-[120px]" style="color:var(--yunmo-ink)">
            {{ novelInfo?.title || '未命名' }}
          </span>
          <span class="text-xs" style="color:var(--yunmo-border)">/</span>
          <span class="text-xs font-medium">第{{ selectedChapterNum }}章</span>
        </div>

        <div class="w-px h-5 mx-1" style="background:var(--yunmo-border)" />

        <!-- 可编辑章节标题 -->
        <div class="flex items-center gap-1">
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

        <a-tag color="blue" class="font-tabular">
          {{ (store.currentChapter?.wordCount || 0).toLocaleString() }} 字
        </a-tag>

        <!-- AI 生成状态指示 -->
        <span
          v-if="store.sseStatus === 'generating' || store.sseStatus === 'reviewing'"
          class="w-2 h-2 rounded-full status-pulse shrink-0"
          :class="store.sseStatus === 'generating' ? 'bg-[var(--yunmo-gold)]' : 'bg-[var(--yunmo-accent-light)]'"
        />

        <div class="flex-1" />

        <span v-if="lastAutoSaved" class="text-xs font-tabular" style="color:var(--yunmo-text-caption)">草稿{{ lastAutoSaved }}</span>

        <div class="w-px h-5 mx-1" style="background:var(--yunmo-border)" />

        <!-- 功能按钮 -->
        <a-button size="small" type="text" class="toolbar-btn" @click="router.push(`/novels/${novelId}/read`)">阅读</a-button>
        <a-button size="small" type="text" class="toolbar-btn" @click="handleOpenVersions">修订</a-button>
        <a-button size="small" type="text" class="toolbar-btn" @click="copyChapter">复制</a-button>
        <a-dropdown>
          <a-button size="small" type="text" class="toolbar-btn">导出</a-button>
          <template #overlay>
            <a-menu @click="({ key }) => { try { api.export[key](novelId) } catch {} }">
              <a-menu-item key="epub">EPUB 电子书</a-menu-item>
              <a-menu-item key="txt">TXT 文本</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <a-button size="small" type="text" class="toolbar-btn" @click="importOpen = true">导入</a-button>
        <a-button size="small" type="text" class="toolbar-btn" @click="searchOpen = true">检索</a-button>

        <!-- 专注模式 -->
        <a-button
          size="small"
          type="text"
          class="toolbar-btn"
          :style="focusMode ? { color: 'var(--yunmo-accent)', background: 'rgba(139,58,58,0.08)' } : {}"
          @click="focusMode = !focusMode; leftCollapsed = focusMode; rightCollapsed = focusMode"
          title="专注模式"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z" /><circle cx="12" cy="12" r="3" />
          </svg>
        </a-button>

        <a-button
          type="primary"
          size="small"
          class="transition-spring"
          :disabled="!store.currentChapter?.content"
          @click="async () => { try { await store.saveChapter(novelId, selectedChapterNum, store.currentChapter?.content || ''); clearDraft(); message.success('已保存') } catch { message.error('保存失败') } }"
        >
          保存
        </a-button>
      </header>

      <!-- 编辑器区域 -->
      <div class="flex-1 overflow-y-auto p-6 md:p-10">
        <div class="editor-page max-w-3xl mx-auto px-8 py-10">
          <ChapterEditor
            :key="editorKey"
            :content="store.currentChapter?.content || store.streamedText || ''"
            @update:content="(v) => { if (store.currentChapter) store.currentChapter.content = v }"
          />
        </div>
      </div>

      <!-- 底部统计栏 -->
      <div
        class="border-t border-[var(--yunmo-border)] bg-[var(--yunmo-paper-light)] transition-all duration-300 overflow-hidden flex-shrink-0"
        :style="{ maxHeight: statsBarOpen ? '120px' : '0', borderTopColor: statsBarOpen ? 'var(--yunmo-border)' : 'transparent' }"
      >
        <div class="px-4 py-2 flex items-center justify-between">
          <button
            class="toolbar-btn flex items-center gap-1"
            @click="statsBarOpen = !statsBarOpen"
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 :style="{ transform: statsBarOpen ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s ease' }">
              <path d="M6 9l6 6 6-6" />
            </svg>
            <span class="text-xs">写作统计</span>
          </button>
          <span class="text-xs font-tabular" style="color:var(--yunmo-text-caption)">
            {{ (store.currentChapter?.wordCount || 0).toLocaleString() }} 字 · 第 {{ selectedChapterNum }} 章
          </span>
        </div>
        <div v-if="statsBarOpen" class="px-4 pb-2">
          <WritingStatsCard :novel-id="novelId" />
        </div>
      </div>
    </main>

    <!-- 右栏折叠标签 -->
    <div
      v-if="rightCollapsed"
      class="w-8 bg-[var(--yunmo-paper)] border-l border-[var(--yunmo-border)] flex flex-col items-center py-3 cursor-pointer hover:bg-[var(--yunmo-paper-dark)] transition-spring flex-shrink-0"
      @click="rightCollapsed = false"
      title="展开工具面板"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-secondary)" stroke-width="2">
        <path d="M9 18l6-6-6-6" />
      </svg>
    </div>

    <!-- 右栏：参考素材 + AI 助手 -->
    <aside v-show="!rightCollapsed" class="w-72 border-l border-[var(--yunmo-border)] bg-[var(--yunmo-paper)] flex flex-col flex-shrink-0 overflow-hidden transition-all duration-300">
      <!-- 折叠按钮 -->
      <div class="absolute z-10" style="left:-12px;top:50%;transform:translateY(-50%)">
        <button
          class="w-6 h-12 bg-[var(--yunmo-paper)] border border-[var(--yunmo-border)] rounded-l-md flex items-center justify-center hover:bg-[var(--yunmo-paper-dark)] transition-fast"
          @click="rightCollapsed = true"
          title="折叠工具面板"
        >
          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5">
            <path d="M9 18l6-6-6-6" />
          </svg>
        </button>
      </div>

      <div class="overflow-y-auto flex-1 p-4 flex flex-col gap-4">
        <!-- AI 生成面板 -->
        <div>
          <div class="collapsible-header" @click="rightPanelSections.ai = !rightPanelSections.ai">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
                 :style="{ transform: rightPanelSections.ai ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
              <path d="M6 9l6 6 6-6" />
            </svg>
            <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">AI 生成</span>
          </div>
          <div v-if="rightPanelSections.ai">
            <GeneratePanel
              :novel-id="novelId"
              :chapter-number="selectedChapterNum"
              :sse-status="store.sseStatus"
              :streamed-text="store.streamedText"
              :quality-report="store.qualityReport"
              :checkpoint="store.checkpoint"
              :writing-style="currentWritingStyle"
              @generate="generate"
              @clear-checkpoint="clearCheckpoint"
            />
          </div>
        </div>

        <!-- 章节控制卡 -->
        <div>
          <div class="collapsible-header" @click="rightPanelSections.control = !rightPanelSections.control">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
                 :style="{ transform: rightPanelSections.control ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
              <path d="M6 9l6 6 6-6" />
            </svg>
            <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">控制卡</span>
          </div>
          <div v-if="rightPanelSections.control" class="mt-2">
            <ChapterControlCard :card="store.chapterControlCard" :loading="store.sseStatus === 'generating'" />
          </div>
        </div>

        <!-- 去AI味评分 -->
        <div>
          <div class="collapsible-header" @click="rightPanelSections.deai = !rightPanelSections.deai">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
                 :style="{ transform: rightPanelSections.deai ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
              <path d="M6 9l6 6 6-6" />
            </svg>
            <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">去AI味</span>
          </div>
          <div v-if="rightPanelSections.deai" class="mt-2">
            <DeAIScoreBadge :diagnosis="store.antiAIDiagnosis" />
          </div>
        </div>

        <!-- 钩子预览 -->
        <div>
          <div class="collapsible-header" @click="rightPanelSections.hooks = !rightPanelSections.hooks">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
                 :style="{ transform: rightPanelSections.hooks ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
              <path d="M6 9l6 6 6-6" />
            </svg>
            <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">钩子编排</span>
          </div>
          <div v-if="rightPanelSections.hooks" class="mt-2">
            <HookPreviewPanel :hook-selection="store.hookSelection" />
          </div>
        </div>

        <!-- AI 讨论入口 -->
        <button class="seal-btn w-full text-sm tracking-wider" @click="outlineDiscussOpen = true">
          与 AI 讨论剧情
        </button>

        <!-- 文风模仿 -->
        <StyleImitationPanel
          :novel-id="novelId"
          :current-style="currentWritingStyle"
          @style-updated="handleStyleUpdated"
        />

        <!-- AI 写作助手 -->
        <AIChatPanel
          :novel-id="novelId"
          :chapter-content="store.currentChapter?.content || ''"
          :chapter-number="selectedChapterNum"
          @insert-text="handleChatInsert"
        />

        <!-- 参考素材 -->
        <div>
          <div class="collapsible-header" @click="rightPanelSections.references = !rightPanelSections.references">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
                 :style="{ transform: rightPanelSections.references ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
              <path d="M6 9l6 6 6-6" />
            </svg>
            <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">参考素材</span>
            <span v-if="refMaterialsCount > 0" class="text-[10px] ml-auto" style="color:var(--yunmo-text-caption)">{{ refMaterialsCount }} 个</span>
          </div>
          <div v-if="rightPanelSections.references">
            <ReferenceMaterialList ref="refMaterialsRef" :novel-id="novelId" />
          </div>
        </div>

        <!-- 角色列表 -->
        <div>
          <div class="collapsible-header" @click="rightPanelSections.characters = !rightPanelSections.characters">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
                 :style="{ transform: rightPanelSections.characters ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
              <path d="M6 9l6 6 6-6" />
            </svg>
            <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">角 色</span>
            <span class="text-[10px] ml-auto" style="color:var(--yunmo-text-caption)">{{ characters.length }} 位</span>
          </div>
          <div v-if="rightPanelSections.characters">
            <div v-if="characters.length === 0" class="text-caption text-xs py-3 text-center">
              尚无角色，生成章节后自动提取
            </div>
            <div v-else class="space-y-0.5 max-h-48 overflow-y-auto">
              <div
                v-for="c in characters"
                :key="c.id"
                class="flex items-center gap-2 px-2.5 py-1.5 rounded-md cursor-pointer text-xs hover:bg-[var(--yunmo-paper-dark)] transition-fast"
                @click="selectedCharId = c.id; characterCardOpen = true"
              >
                <span class="w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold text-white shrink-0"
                  :style="{ background: { PROTAGONIST:'#8b3a3a', ANTAGONIST:'#b3443a', SUPPORTING:'#b8956c', MINOR:'#5a7a5a' }[c.role] || '#888' }"
                >{{ c.name?.charAt(0) }}</span>
                <span class="truncate font-medium">{{ c.name }}</span>
                <span v-if="c.lastAppearanceChapter === selectedChapterNum" class="w-2 h-2 rounded-full bg-[var(--yunmo-accent)] shrink-0 ring-1 ring-[var(--yunmo-accent)] ring-opacity-30" title="本章出场" />
              </div>
            </div>
          </div>
        </div>

        <!-- 马拉松创作 -->
        <div>
          <div class="collapsible-header" @click="rightPanelSections.marathon = !rightPanelSections.marathon">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
                 :style="{ transform: rightPanelSections.marathon ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
              <path d="M6 9l6 6 6-6" />
            </svg>
            <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">马拉松</span>
          </div>
          <div v-if="rightPanelSections.marathon" class="mt-2">
            <MarathonControlPanel :novel-id="novelId" />
          </div>
        </div>
      </div>
    </aside>

    <!-- 导入外部文稿 -->
    <ImportModal :novel-id="novelId" v-model:open="importOpen" @imported="store.fetchChapters(novelId)" />

    <!-- 全文搜索替换 -->
    <SearchReplaceModal :novel-id="novelId" v-model:open="searchOpen" @jump-to="(cn) => selectedChapterNum = cn" />

    <!-- AI 大纲讨论 -->
    <OutlineDiscussion
      :novel-id="novelId"
      :node-id="discussNodeId"
      :open="outlineDiscussOpen"
      @update:open="outlineDiscussOpen = $event"
      @apply-suggestion="async (content) => {
        try {
          const novel = await api.novels.get(novelId)
          const newOutline = (novel.outline || '') + '\n\n---\n' + content
          await api.novels.update(novelId, { outline: newOutline })
          message.success('已同步到全书大纲')
        } catch { message.error('同步失败') }
        outlineDiscussOpen = false
      }"
    />

    <!-- 版本历史抽屉 -->
    <a-drawer
      :open="showVersions"
      @close="showVersions = false"
      width="400px"
    >
      <template #title>
        <div class="flex items-center gap-2">
          <span>修改记录</span>
          <BranchSwitcher
            :novel-id="novelId"
            :chapter-number="selectedChapterNum"
            :active-branch="activeBranch"
            @update:active-branch="activeBranch = $event"
            @refresh="loadVersions"
          />
        </div>
      </template>
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
              <a-button size="small" type="text" class="toolbar-btn" @click="async () => { try { const name = prompt('分支名称'); if (name) { await api.chapters.fork(novelId, selectedChapterNum, name); loadVersions(); } } catch {} }">创建分支</a-button>
            </div>
          </a-timeline-item>
        </a-timeline>
      </a-spin>
    </a-drawer>

    <!-- 角色卡片弹窗 -->
    <a-modal
      :open="characterCardOpen"
      @cancel="characterCardOpen = false"
      :footer="null"
      width="420px"
      :title="null"
    >
      <CharacterCard
        v-if="selectedCharacter"
        :character="selectedCharacter"
        :novel-id="novelId"
        :chapter-number="selectedChapterNum"
        @updated="loadCharacters"
        @close="characterCardOpen = false"
      />
    </a-modal>

  </div>
</template>
