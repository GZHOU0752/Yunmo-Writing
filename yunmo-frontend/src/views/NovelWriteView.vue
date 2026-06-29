<script setup>
/**
 * 写作工作区主视图
 * 纯布局编排，业务逻辑已拆分至子组件和 composables
 */
import { onMounted, onBeforeUnmount, ref, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWriteStore } from '@/composables/useWriteStore'
import { useApi } from '@/composables/useApi'
import { useKeyboardShortcuts } from '@/composables/useKeyboardShortcuts'
import { useDraftAutoSave } from '@/composables/useDraftAutoSave'
import { useWriteLayout } from '@/composables/useWriteLayout'
import { useTheme } from '@/composables/useTheme'
import { message } from 'ant-design-vue'

// 子组件
import ChapterEditor from '@/components/ChapterEditor.vue'
import WritingStatsCard from '@/components/WritingStatsCard.vue'
import CharacterCard from '@/components/CharacterCard.vue'
import SearchReplaceModal from '@/components/SearchReplaceModal.vue'
import ImportModal from '@/components/ImportModal.vue'
import OutlineDiscussion from '@/components/OutlineDiscussion.vue'
import OutlinePanel from '@/components/outline/OutlinePanel.vue'
import WriteShortcutHelp from '@/components/write/WriteShortcutHelp.vue'
import WriteHeader from '@/components/write/WriteHeader.vue'
import WriteLeftSidebar from '@/components/write/WriteLeftSidebar.vue'
import WriteRightSidebar from '@/components/write/WriteRightSidebar.vue'
import WriteVersionDrawer from '@/components/write/WriteVersionDrawer.vue'
import WriteMobileNav from '@/components/write/WriteMobileNav.vue'

const route = useRoute()
const router = useRouter()
const store = useWriteStore()
const api = useApi()
const { theme, toggle: toggleTheme } = useTheme()
const novelId = route.params.id

// 核心状态
const selectedChapterNum = ref(1)
const searchOpen = ref(false)
const importOpen = ref(false)
const chapterSearchQuery = ref('')
const editorKey = ref(0)
let abortGeneration = null

// 角色列表
const characters = ref([])
const selectedCharId = ref(null)
const characterCardOpen = ref(false)

// 版本/大纲讨论
const outlineDiscussOpen = ref(false)
const discussNodeId = ref(null)

// 小说信息
const novelInfo = ref(null)
const currentWritingStyle = ref('')

// 章节预加载缓存
const chapterCache = new Map()

// 子组件引用
const headerRef = ref(null)
const versionDrawerRef = ref(null)

// composables
const layout = useWriteLayout()
const draft = useDraftAutoSave({ store, novelId, selectedChapterNum })

// ===== 章节导航逻辑 =====

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

/** 确保章节存在，不存在则自动创建 */
async function ensureChapter(n) {
  await store.fetchChapters(novelId)
  const exists = store.chapters.some(c => c.chapterNumber === n)
  if (!exists) {
    await api.chapters.create(novelId, n)
    await store.fetchChapters(novelId)
  }
}

async function loadCharacters() {
  try { characters.value = await api.characters.list(novelId) } catch {}
}

const selectedCharacter = ref(null)
watch([selectedCharId, characters], () => {
  selectedCharacter.value = characters.value.find(c => c.id === selectedCharId.value) || null
})

// ===== 监听生成完成 =====
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
    nextTick(() => draft.clearDraft())
    try {
      const ch = await api.chapters.get(novelId, selectedChapterNum.value)
      if (ch) chapterCache.set(selectedChapterNum.value, ch)
    } catch {}
    loadCharacters()
    try {
      novelInfo.value = await api.novels.get(novelId)
      currentWritingStyle.value = novelInfo.value?.writingStyle || ''
    } catch {}
  }
})

// ===== 章节切换 =====
let chapterLoadTimer = null
watch(selectedChapterNum, (n) => {
  if (chapterLoadTimer) clearTimeout(chapterLoadTimer)
  const oldCn = store.currentChapter?.chapterNumber
  if (oldCn && store.currentChapter?.content) {
    draft.saveDraftForChapter(oldCn)
  }
  chapterLoadTimer = setTimeout(async () => {
    if (n) {
      await ensureChapter(n)
      const cached = chapterCache.get(n)
      if (cached) {
        store.currentChapter = cached
        headerRef.value?.syncEditingTitle()
        draft.restoreDraft()
        preloadAdjacentChapters()
        return
      }
      await store.loadChapter(novelId, n)
      if (store.currentChapter) chapterCache.set(n, store.currentChapter)
      headerRef.value?.syncEditingTitle()
      draft.restoreDraft()
      preloadAdjacentChapters()
    }
  }, 100)
})

// ===== 生命周期 =====
onMounted(async () => {
  await store.fetchChapters(novelId)
  if (store.chapters.length === 0) {
    await ensureChapter(1)
  }
  await store.loadChapter(novelId, selectedChapterNum.value)
  headerRef.value?.syncEditingTitle()
  draft.restoreDraft()
  draft.startAutoSave()
  loadCharacters()
  try {
    novelInfo.value = await api.novels.get(novelId)
    currentWritingStyle.value = novelInfo.value?.writingStyle || ''
  } catch {}
})

onBeforeUnmount(() => {
  if (chapterLoadTimer) clearTimeout(chapterLoadTimer)
  if (abortGeneration) abortGeneration()
  draft.stopAutoSave()
  draft.autoSave()
})

// ===== 操作函数 =====

async function addChapter() {
  try {
    const ch = await api.chapters.create(novelId)
    await store.fetchChapters(novelId)
    selectedChapterNum.value = ch.chapterNumber
  } catch (e) {
    console.error('新增章节失败:', e)
  }
}

let deletingChapter = false
async function handleDeleteChapter(cn) {
  if (!confirm(`确定删除第${cn}章？此操作不可恢复。`)) return
  if (deletingChapter) return
  deletingChapter = true
  if (chapterLoadTimer) { clearTimeout(chapterLoadTimer); chapterLoadTimer = null }
  try {
    await api.chapters.delete(novelId, cn)
    await store.fetchChapters(novelId)
    if (store.currentChapter) {
      store.currentChapter = null
      chapterCache.delete(cn)
    }
    const targetCn = Math.max(1, cn - 1)
    selectedChapterNum.value = targetCn
    await ensureChapter(targetCn)
    await store.loadChapter(novelId, targetCn)
    headerRef.value?.syncEditingTitle()
    draft.restoreDraft()
  } catch (e) {
    message.error('删除失败')
    console.error('删除章节失败:', e)
  } finally {
    deletingChapter = false
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

function handleChatInsert(text) {
  if (store.currentChapter) {
    store.currentChapter.content = (store.currentChapter.content || '') + '\n\n' + text
    editorKey.value++
  }
}

async function handleSave() {
  try {
    await store.saveChapter(novelId, selectedChapterNum.value, store.currentChapter?.content || '')
    draft.clearDraft()
    message.success('已保存')
  } catch {
    message.error('保存失败')
  }
}

// ===== 快捷键 =====
const { showHelp: showShortcutHelp, getAllShortcuts } = useKeyboardShortcuts({
  onSave: () => {
    if (store.currentChapter?.content) {
      store.saveChapter(novelId, selectedChapterNum.value, store.currentChapter.content).then(draft.clearDraft)
    }
  },
  onPrevChapter: () => {
    if (selectedChapterNum.value > 1) selectedChapterNum.value--
  },
  onNextChapter: () => {
    if (selectedChapterNum.value < store.chapters.length) selectedChapterNum.value++
  },
  onGenerate: generate,
  onSearch: () => { searchOpen.value = true },
  onToggleSidebar: () => {
    if (layout.leftCollapsed.value) { layout.expandLeft(); layout.expandRight() }
    else { layout.collapseLeft(); layout.collapseRight() }
  },
  onFocusMode: layout.toggleFocusMode,
})
</script>

<template>
  <div class="min-h-[100dvh] flex" :class="{ 'focus-mode': layout.focusMode.value }">
    <!-- 左栏折叠标签 -->
    <div
      v-if="layout.leftCollapsed.value"
      class="hidden md:flex w-8 bg-[var(--yunmo-paper-light)] border-r border-[var(--yunmo-border)] flex-col items-center py-3 cursor-pointer hover:bg-[var(--yunmo-paper-dark)] transition-spring flex-shrink-0"
      @click="layout.expandLeft()"
      title="展开章节列表"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-secondary)" stroke-width="2">
        <path d="M15 18l-6-6 6-6" />
      </svg>
    </div>

    <!-- 左栏 -->
    <WriteLeftSidebar
      v-show="!layout.leftCollapsed.value"
      :novel-id="novelId"
      :selected-chapter-num="selectedChapterNum"
      :chapter-search-query="chapterSearchQuery"
      :chapters="store.chapters"
      :outline-expanded="layout.outlineExpanded.value"
      @update:selected-chapter-num="selectedChapterNum = $event"
      @update:chapter-search-query="chapterSearchQuery = $event"
      @add-chapter="addChapter"
      @delete-chapter="handleDeleteChapter"
      @toggle-outline-expand="layout.toggleOutlineExpand()"
    >
      <template #collapse-btn>
        <div class="absolute z-10" style="right:-12px;top:50%;transform:translateY(-50%)">
          <button
            class="w-6 h-12 bg-[var(--yunmo-paper-light)] border border-[var(--yunmo-border)] rounded-r-md flex items-center justify-center hover:bg-[var(--yunmo-paper-dark)] transition-fast"
            @click="layout.collapseLeft()"
            title="折叠侧栏"
          >
            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5">
              <path d="M15 18l-6-6 6-6" />
            </svg>
          </button>
        </div>
      </template>
    </WriteLeftSidebar>

    <!-- 中栏：编辑器 -->
    <main class="flex-1 flex flex-col min-w-0 overflow-hidden">
      <WriteHeader
        ref="headerRef"
        :novel-id="novelId"
        :selected-chapter-num="selectedChapterNum"
        :novel-info="novelInfo"
        :last-auto-saved="draft.lastAutoSaved.value"
        :focus-mode="layout.focusMode.value"
        @toggle-focus-mode="layout.toggleFocusMode()"
        @open-versions="versionDrawerRef?.open()"
        @open-search="searchOpen = true"
        @open-import="importOpen = true"
        @save="handleSave"
      />

      <!-- 编辑器区域 -->
      <div class="flex-1 overflow-y-auto p-6 md:p-10 pb-20 md:pb-10">
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
        :style="{ maxHeight: layout.statsBarOpen.value ? '120px' : '0', borderTopColor: layout.statsBarOpen.value ? 'var(--yunmo-border)' : 'transparent' }"
      >
        <div class="px-4 py-2 flex items-center justify-between">
          <button
            class="toolbar-btn flex items-center gap-1"
            @click="layout.statsBarOpen.value = !layout.statsBarOpen.value"
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 :style="{ transform: layout.statsBarOpen.value ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s ease' }">
              <path d="M6 9l6 6 6-6" />
            </svg>
            <span class="text-xs">写作统计</span>
          </button>
          <span class="text-xs font-tabular">
            {{ (store.currentChapter?.wordCount || 0).toLocaleString() }} 字 · 第 {{ selectedChapterNum }} 章
          </span>
        </div>
        <div v-if="layout.statsBarOpen.value" class="px-4 pb-2">
          <WritingStatsCard :novel-id="novelId" />
        </div>
      </div>
    </main>

    <!-- 右栏折叠标签 -->
    <div
      v-if="layout.rightCollapsed.value"
      class="hidden md:flex w-8 bg-[var(--yunmo-paper)] border-l border-[var(--yunmo-border)] flex-col items-center py-3 cursor-pointer hover:bg-[var(--yunmo-paper-dark)] transition-spring flex-shrink-0"
      @click="layout.expandRight()"
      title="展开工具面板"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-secondary)" stroke-width="2">
        <path d="M9 18l6-6-6-6" />
      </svg>
    </div>

    <!-- 右栏 -->
    <WriteRightSidebar
      v-show="!layout.rightCollapsed.value"
      :novel-id="novelId"
      :selected-chapter-num="selectedChapterNum"
      :characters="characters"
      :current-writing-style="currentWritingStyle"
      :right-panel-sections="layout.rightPanelSections.value"
      :characters-expanded="layout.charactersExpanded.value"
      @generate="generate"
      @clear-checkpoint="clearCheckpoint"
      @chat-insert="handleChatInsert"
      @open-discussion="outlineDiscussOpen = true"
      @open-character-card="(id) => { selectedCharId = id; characterCardOpen = true }"
      @toggle-characters-expand="layout.toggleCharactersExpand()"
    >
      <template #collapse-btn>
        <div class="absolute z-10" style="left:-12px;top:50%;transform:translateY(-50%)">
          <button
            class="w-6 h-12 bg-[var(--yunmo-paper)] border border-[var(--yunmo-border)] rounded-l-md flex items-center justify-center hover:bg-[var(--yunmo-paper-dark)] transition-fast"
            @click="layout.collapseRight()"
            title="折叠工具面板"
          >
            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5">
              <path d="M9 18l6-6-6-6" />
            </svg>
          </button>
        </div>
      </template>
    </WriteRightSidebar>

    <!-- 全屏大纲面板 -->
    <Transition name="page-fade">
      <div v-if="layout.outlineExpanded.value" class="fullscreen-panel">
        <div class="flex items-center justify-between px-6 py-3 border-b border-[var(--yunmo-border)] bg-[var(--yunmo-paper-light)]">
          <h2 class="text-lg font-semibold" style="color:var(--yunmo-accent)">大纲编辑</h2>
          <button
            class="toolbar-btn flex items-center gap-1.5"
            @click="layout.toggleOutlineExpand()"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 14h6v6M20 10h-6V4M14 10l7-7M3 21l7-7" />
            </svg>
            <span class="text-xs">收回到侧栏</span>
          </button>
        </div>
        <div class="flex-1 overflow-y-auto p-6">
          <OutlinePanel
            :novel-id="novelId"
            :chapters="store.chapters"
            :active-chapter="selectedChapterNum"
            full-width
            @select-chapter="(cn) => { selectedChapterNum = cn; layout.toggleOutlineExpand() }"
          />
        </div>
      </div>
    </Transition>

    <!-- 全屏角色面板 -->
    <Transition name="page-fade">
      <div v-if="layout.charactersExpanded.value" class="fullscreen-panel">
        <div class="flex items-center justify-between px-6 py-3 border-b border-[var(--yunmo-border)] bg-[var(--yunmo-paper-light)]">
          <h2 class="text-lg font-semibold" style="color:var(--yunmo-accent)">角色管理</h2>
          <button
            class="toolbar-btn flex items-center gap-1.5"
            @click="layout.toggleCharactersExpand()"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 14h6v6M20 10h-6V4M14 10l7-7M3 21l7-7" />
            </svg>
            <span class="text-xs">收回到侧栏</span>
          </button>
        </div>
        <div class="flex-1 overflow-y-auto p-6">
          <div class="max-w-4xl mx-auto">
            <div v-if="characters.length === 0" class="text-center py-12 text-caption">
              <p class="text-lg mb-2">暂无角色</p>
              <p class="text-sm">生成章节后会自动提取角色信息</p>
            </div>
            <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              <div
                v-for="c in characters"
                :key="c.id"
                class="yunmo-card p-4 cursor-pointer"
                @click="selectedCharId = c.id; characterCardOpen = true"
              >
                <div class="flex items-center gap-3 mb-3">
                  <span
                    class="w-10 h-10 rounded-full flex items-center justify-center text-sm font-bold text-white"
                    :style="{ background: { PROTAGONIST:'#8b3a3a', ANTAGONIST:'#b3443a', SUPPORTING:'#b8956c', MINOR:'#5a7a5a' }[c.role] || '#888' }"
                  >{{ c.name?.charAt(0) }}</span>
                  <div>
                    <div class="font-medium text-sm">{{ c.name }}</div>
                    <div class="text-xs text-caption">{{ c.roleName || c.role }}</div>
                  </div>
                </div>
                <p v-if="c.description" class="text-xs text-caption line-clamp-2">{{ c.description }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 模态框 -->
    <ImportModal :novel-id="novelId" v-model:open="importOpen" @imported="store.fetchChapters(novelId)" />
    <SearchReplaceModal :novel-id="novelId" v-model:open="searchOpen" @jump-to="(cn) => selectedChapterNum = cn" />

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

    <WriteVersionDrawer ref="versionDrawerRef" :novel-id="novelId" :selected-chapter-num="selectedChapterNum" />

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

    <!-- 快捷键帮助 -->
    <WriteShortcutHelp
      v-model:open="showShortcutHelp"
      :shortcuts="getAllShortcuts()"
    />

    <!-- 移动端导航 -->
    <WriteMobileNav
      :novel-id="novelId"
      :selected-chapter-num="selectedChapterNum"
      :chapter-search-query="chapterSearchQuery"
      :mobile-panel="layout.mobilePanel.value"
      :current-writing-style="currentWritingStyle"
      @update:selected-chapter-num="selectedChapterNum = $event"
      @update:chapter-search-query="chapterSearchQuery = $event"
      @update:mobile-panel="layout.mobilePanel.value = $event"
      @generate="generate"
      @clear-checkpoint="clearCheckpoint"
      @chat-insert="handleChatInsert"
      @save="handleSave"
      @delete-chapter="handleDeleteChapter"
    />
  </div>
</template>
