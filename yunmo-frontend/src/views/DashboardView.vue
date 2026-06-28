<script setup>
import { onMounted, ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useNovelStore } from '@/composables/useNovelStore'
import WritingStatsCard from '@/components/WritingStatsCard.vue'
import { useApi } from '@/composables/useApi'
import NovelCard from '@/components/NovelCard.vue'

const store = useNovelStore()
const api = useApi()
const router = useRouter()
const showCreate = ref(false)
const newTitle = ref('')
const newSynopsis = ref('')
const newGenre = ref('xuanhuan')
const deletingId = ref(null)

const detailOpen = ref(false)
const detailNovel = ref(null)
const editTitle = ref('')
const editSynopsis = ref('')
const editGenre = ref('xuanhuan')
const savingDetail = ref(false)

// 搜索 & 筛选
const searchQuery = ref(localStorage.getItem('yunmo-dashboard-search') || '')
const activeGenre = ref(localStorage.getItem('yunmo-dashboard-genre') || 'all')
const sortBy = ref(localStorage.getItem('yunmo-dashboard-sort') || 'updated')

// 持久化筛选状态
watch(searchQuery, (v) => localStorage.setItem('yunmo-dashboard-search', v))
watch(activeGenre, (v) => localStorage.setItem('yunmo-dashboard-genre', v))
watch(sortBy, (v) => localStorage.setItem('yunmo-dashboard-sort', v))

const genreLabels = {
  xuanhuan: '玄幻', qihuan: '奇幻', xianxia: '仙侠',
  dushi: '都市', xuanyi: '悬疑灵异', qingxiaoshuo: '轻小说',
  tongren: '同人', duanpian: '短篇',
}

// 筛选后的小说列表
const filteredNovels = computed(() => {
  let list = [...store.novels]

  // 搜索
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    list = list.filter(n =>
      n.title?.toLowerCase().includes(q) ||
      n.synopsis?.toLowerCase().includes(q)
    )
  }

  // 类型筛选
  if (activeGenre.value !== 'all') {
    list = list.filter(n => n.genreId === activeGenre.value)
  }

  // 排序
  list.sort((a, b) => {
    switch (sortBy.value) {
      case 'words':
        return (b.wordCount || 0) - (a.wordCount || 0)
      case 'created':
        return new Date(a.createdAt || 0) - new Date(b.createdAt || 0)
      case 'updated':
      default:
        return new Date(b.updatedAt || b.createdAt || 0) - new Date(a.updatedAt || a.createdAt || 0)
    }
  })

  return list
})

function openDetail(novel) {
  detailNovel.value = novel
  editTitle.value = novel.title
  editSynopsis.value = novel.synopsis || ''
  editGenre.value = novel.genreId || 'xuanhuan'
  detailOpen.value = true
}

async function saveDetail() {
  if (!detailNovel.value || !editTitle.value.trim()) return
  savingDetail.value = true
  try {
    await api.novels.update(detailNovel.value.id, {
      title: editTitle.value.trim(),
      synopsis: editSynopsis.value.trim(),
      genre_id: editGenre.value,
    })
    detailNovel.value.title = editTitle.value.trim()
    detailNovel.value.synopsis = editSynopsis.value.trim()
    detailNovel.value.genreId = editGenre.value
    detailOpen.value = false
  } catch (e) {
    console.error('保存失败:', e)
  } finally {
    savingDetail.value = false
  }
}

async function deleteNovel(id) {
  deletingId.value = id
  try {
    await store.deleteNovel(id)
    if (detailNovel.value?.id === id) detailOpen.value = false
  } catch (e) {
    console.error('删除失败:', e)
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  store.fetchNovels().catch(() => {})
  store.fetchGenres().catch(() => {})
})

async function handleCreate() {
  if (!newTitle.value.trim()) return
  try {
    const novel = await store.createNovel(newTitle.value.trim(), newGenre.value, newSynopsis.value.trim())
    if (!novel?.id) return
    showCreate.value = false
    newTitle.value = ''
    newSynopsis.value = ''
    router.push(`/novels/${novel.id}/setup`)
  } catch (e) {
    console.error('创建失败:', e)
  }
}

// 封面渐变色池
const coverColors = [
  ['#8b3a3a', '#6e2626'],
  ['#5a7a5a', '#3d553d'],
  ['#b8956c', '#8b6f4e'],
  ['#4a6b8a', '#2d4a63'],
  ['#7a5a4a', '#5a3d2e'],
  ['#b3443a', '#8a2a22'],
]
function coverGradient(idx) {
  const c = coverColors[idx % coverColors.length]
  return `linear-gradient(160deg, ${c[0]} 0%, ${c[1]} 100%)`
}
</script>

<template>
  <div class="min-h-[100dvh]">
    <div class="max-w-6xl mx-auto px-6 py-8">
      <!-- 页面标题 -->
      <div class="flex items-center justify-between mb-8">
        <div>
          <h1 class="text-2xl font-bold tracking-tight antialiased-title">书房</h1>
          <p class="text-sm mt-1">笔墨纸砚，皆备于此</p>
        </div>
        <a-button type="primary" size="large" @click="showCreate = true" class="yunmo-btn">
          新建作品
        </a-button>
      </div>

      <!-- 搜索 & 筛选栏 -->
      <div class="flex flex-col md:flex-row gap-4 mb-6">
        <!-- 搜索框 -->
        <div class="relative flex-1">
          <a-input
            v-model:value="searchQuery"
            placeholder="搜索书名或简介..."
            allow-clear
            class="w-full"
          >
            <template #prefix>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2">
                <circle cx="11" cy="11" r="8" /><path d="M21 21l-4.35-4.35" />
              </svg>
            </template>
          </a-input>
        </div>

        <!-- 排序 -->
        <a-select v-model:value="sortBy" style="width:120px" class="flex-shrink-0">
          <a-select-option value="updated">最近更新</a-select-option>
          <a-select-option value="words">字数最多</a-select-option>
          <a-select-option value="created">最早创建</a-select-option>
        </a-select>
      </div>

      <!-- 类型 & 状态筛选标签 -->
      <div class="flex flex-wrap gap-2 mb-6">
        <div class="flex items-center gap-2 mr-4">
          <span class="text-xs font-medium">类型</span>
          <button
            class="filter-tag"
            :class="{ active: activeGenre === 'all' }"
            @click="activeGenre = 'all'"
          >全部</button>
          <button
            v-for="g in store.genres"
            :key="g.id"
            class="filter-tag"
            :class="{ active: activeGenre === g.id }"
            @click="activeGenre = g.id"
          >{{ g.name }}</button>
        </div>
      </div>

      <!-- 结果计数 -->
      <div v-if="filteredNovels.length > 0" class="text-xs mb-4">
        共 {{ filteredNovels.length }} 部作品
      </div>

      <!-- 列表 -->
      <a-spin :spinning="store.loading">
        <!-- 骨架屏 -->
        <div v-if="store.loading" class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div v-for="i in 4" :key="i" class="yunmo-card p-5">
            <a-skeleton active :paragraph="{ rows: 4 }" />
          </div>
        </div>

        <!-- 小说卡片网格 -->
        <div v-if="!store.loading && filteredNovels.length > 0" class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <NovelCard
            v-for="(novel, idx) in filteredNovels"
            :key="novel.id"
            :novel="novel"
            :index="idx"
            class="card-enter"
            @click="router.push(`/novels/${novel.id}/write`)"
            @detail="openDetail(novel)"
          />
        </div>

        <!-- 有搜索/筛选但无结果 -->
        <div v-if="!store.loading && store.novels.length > 0 && filteredNovels.length === 0" class="empty-state">
          <div class="empty-state-icon">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5" opacity="0.35"><circle cx="11" cy="11" r="7"/><path d="M16.5 16.5L21 21"/></svg>
          </div>
          <h3 class="empty-state-title">未找到匹配的作品</h3>
          <p class="empty-state-desc">
            尝试调整搜索关键词或筛选条件
          </p>
          <button class="yunmo-btn-outline mt-4" @click="searchQuery = ''; activeGenre = 'all'">
            清除筛选
          </button>
        </div>

        <!-- 空状态 -->
        <div v-if="!store.loading && store.novels.length === 0" class="empty-state">
          <div class="empty-state-icon font-brush">云墨</div>
          <h3 class="empty-state-title">砚已注水，墨已研开</h3>
          <p class="empty-state-desc">
            每一部作品都是一方天地。创建你的第一部小说，从这里开始落笔。
          </p>
          <a-button type="primary" size="large" @click="showCreate = true" class="mt-6">开始创作</a-button>
        </div>
      </a-spin>
    </div>

    <!-- 创建弹窗 -->
    <a-modal v-model:open="showCreate" title="新建作品" @ok="handleCreate" ok-text="创建" cancel-text="取消">
      <a-form layout="vertical">
        <a-form-item label="书名">
          <a-input v-model:value="newTitle" placeholder="输入书名..." />
        </a-form-item>
        <a-form-item label="简介">
          <a-textarea v-model:value="newSynopsis" placeholder="写一段简介..." :rows="3" />
        </a-form-item>
        <p class="text-xs">类型可在下一步设置中选择</p>
      </a-form>
    </a-modal>

    <!-- 详情抽屉 -->
    <a-drawer :open="detailOpen" @close="detailOpen = false" title="作品信息" width="420px">
      <template v-if="detailNovel">
        <a-form layout="vertical">
          <a-form-item label="书名">
            <a-input v-model:value="editTitle" placeholder="书名" />
          </a-form-item>
          <a-form-item label="类型">
            <a-select v-model:value="editGenre">
              <a-select-option v-for="(label, id) in genreLabels" :key="id" :value="id">{{ label }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="简介">
            <a-textarea v-model:value="editSynopsis" placeholder="写一段简介..." :rows="4" />
          </a-form-item>
        </a-form>

        <WritingStatsCard :novel-id="detailNovel?.id" class="mb-4" />

        <div class="yunmo-card p-4 mb-4 space-y-2 text-sm">
          <div class="flex justify-between"><span class="text-caption">总字数</span><span>{{ detailNovel.wordCount?.toLocaleString() || 0 }} 字</span></div>
          <div class="flex justify-between"><span class="text-caption">章节数</span><span>{{ detailNovel.totalChapters || 0 }} 章</span></div>
          <div class="flex justify-between"><span class="text-caption">创建时间</span><span>{{ detailNovel.createdAt?.substring(0, 10) || '-' }}</span></div>
        </div>

        <div class="flex gap-2 mb-3">
          <a-dropdown class="flex-1">
            <button class="yunmo-btn-outline flex-1 text-sm">导出</button>
            <template #overlay>
              <a-menu @click="({ key }) => { try { api.export[key](detailNovel.id) } catch {} }">
                <a-menu-item key="epub">EPUB 电子书</a-menu-item>
                <a-menu-item key="txt">TXT 文本</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>

        <div class="flex gap-3">
          <button class="yunmo-btn flex-1" :disabled="savingDetail" @click="saveDetail">{{ savingDetail ? '保存中...' : '保存修改' }}</button>
          <a-popconfirm title="确定删除？不可恢复。" ok-text="确认" cancel-text="取消" @confirm="deleteNovel(detailNovel.id)">
            <button class="yunmo-btn-outline">删除本书</button>
          </a-popconfirm>
        </div>
      </template>
    </a-drawer>
  </div>
</template>
