<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useNovelStore } from '@/composables/useNovelStore'
import NovelCard from '@/components/NovelCard.vue'
import WritingStatsCard from '@/components/WritingStatsCard.vue'
import CharacterGraph from '@/components/CharacterGraph.vue'

import { clearToken, useApi } from '@/composables/useApi'

const store = useNovelStore()
const api = useApi()
const router = useRouter()
const showCreate = ref(false)
const newTitle = ref('')
const newSynopsis = ref('')
const newGenre = ref('xuanhuan')

// 书籍详情抽屉
const detailOpen = ref(false)
const detailNovel = ref(null)
const editTitle = ref('')
const editSynopsis = ref('')
const savingDetail = ref(false)
const relationGraphOpen = ref(false)

const genreLabels = {
  xuanhuan: '玄幻', qihuan: '奇幻', xianxia: '仙侠',
  dushi: '都市', xuanyi: '悬疑灵异', qingxiaoshuo: '轻小说',
}

function openDetail(novel) {
  detailNovel.value = novel
  editTitle.value = novel.title
  editSynopsis.value = novel.synopsis || ''
  detailOpen.value = true
}

async function saveDetail() {
  if (!detailNovel.value || !editTitle.value.trim()) return
  savingDetail.value = true
  try {
    await api.novels.update(detailNovel.value.id, {
      title: editTitle.value.trim(),
      synopsis: editSynopsis.value.trim(),
    })
    // 同步更新本地列表
    detailNovel.value.title = editTitle.value.trim()
    detailNovel.value.synopsis = editSynopsis.value.trim()
    detailOpen.value = false
  } catch (e) {
    console.error('保存书籍信息失败:', e)
  } finally {
    savingDetail.value = false
  }
}

async function deleteNovelFromDetail() {
  if (!detailNovel.value) return
  try {
    await store.deleteNovel(detailNovel.value.id)
    detailOpen.value = false
  } catch (e) {
    console.error('删除小说失败:', e)
  }
}

async function logout() {
  try { await api.auth.logout() } catch {}
  clearToken()
  router.push('/')
}

onMounted(() => {
  store.fetchNovels().catch(e => console.error('加载小说列表失败:', e))
  store.fetchGenres().catch(e => console.error('加载类型列表失败:', e))
})

async function handleCreate() {
  if (!newTitle.value.trim()) return
  try {
    const novel = await store.createNovel(newTitle.value.trim(), newGenre.value, newSynopsis.value.trim())
    if (!novel || !novel.id) {
      console.error('创建小说失败：返回数据异常', novel)
      return
    }
    showCreate.value = false
    newTitle.value = ''
    newSynopsis.value = ''
    router.push(`/novels/${novel.id}/setup`)
  } catch (e) {
    console.error('创建小说失败:', e)
  }
}
</script>

<template>
  <div class="min-h-[100dvh] p-6 max-w-6xl mx-auto">
    <!-- 顶部 -->
    <header class="flex items-center justify-between mb-8">
      <div>
        <h1 class="text-2xl font-bold text-[var(--yunmo-accent)]">云 墨</h1>
        <p class="text-caption">我的书房</p>
      </div>
      <div class="flex gap-2">
        <a-button type="primary" @click="showCreate = true">新小说</a-button>
        <a-button @click="logout">登出</a-button>
      </div>
    </header>

    <!-- 创建弹窗 -->
    <a-modal v-model:open="showCreate" title="创建新小说" @ok="handleCreate" ok-text="创建" cancel-text="取消">
      <a-form layout="vertical">
        <a-form-item label="书名">
          <a-input v-model:value="newTitle" placeholder="输入书名..." />
        </a-form-item>
        <a-form-item label="简介">
          <a-textarea v-model:value="newSynopsis" placeholder="写一段简介..." :rows="3" />
        </a-form-item>
        <a-form-item label="类型">
          <a-select v-model:value="newGenre">
            <a-select-option v-for="g in store.genres" :key="g.id" :value="g.id">
              {{ g.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 小说列表 -->
    <a-spin :spinning="store.loading">
      <div
        v-if="store.novels.length > 0"
        class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 auto-rows-min"
      >
        <NovelCard
          v-for="(novel, idx) in store.novels"
          :key="novel.id"
          :novel="novel"
          :class="idx === 0 && store.novels.length >= 2 ? 'md:col-span-2 lg:col-span-2' : ''"
          :style="{ animationDelay: idx * 60 + 'ms' }"
          class="card-enter"
          @click="router.push(`/novels/${novel.id}/write`)"
          @detail="openDetail(novel)"
        />
      </div>

      <div v-if="!store.loading && store.novels.length === 0" class="mt-20 max-w-sm mx-auto text-center">
        <div class="font-brush text-3xl mb-6 tracking-widest" style="color:var(--yunmo-accent)">云 墨</div>
        <p class="text-base leading-loose tracking-wider" style="color:var(--yunmo-text-caption)">
          提笔落墨<br/>写下你的第一部作品
        </p>
        <div class="mt-8">
          <a-button type="primary" size="large" @click="showCreate = true">开始创作</a-button>
        </div>
      </div>
    </a-spin>

    <!-- 书籍详情抽屉 -->
    <a-drawer
      :open="detailOpen"
      @close="detailOpen = false"
      title="书籍详情"
      width="420px"
    >
      <template v-if="detailNovel">
        <a-form layout="vertical">
          <a-form-item label="书名">
            <a-input v-model:value="editTitle" placeholder="书名" />
          </a-form-item>
          <a-form-item label="简介">
            <a-textarea v-model:value="editSynopsis" placeholder="写一段简介..." :rows="4" />
          </a-form-item>
        </a-form>

        <!-- 码字统计 -->
        <WritingStatsCard :novel-id="detailNovel?.id" class="mb-4" />

        <!-- 只读信息 -->
        <div class="yunmo-card p-4 mb-4 space-y-2 text-sm">
          <div class="flex justify-between">
            <span class="text-caption">类型</span>
            <span>{{ genreLabels[detailNovel.genreId] || detailNovel.genreId }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-caption">总字数</span>
            <span>{{ detailNovel.wordCount?.toLocaleString() || 0 }} 字</span>
          </div>
          <div class="flex justify-between">
            <span class="text-caption">章节数</span>
            <span>{{ detailNovel.totalChapters || 0 }} 章</span>
          </div>
          <div class="flex justify-between">
            <span class="text-caption">创建时间</span>
            <span>{{ detailNovel.createdAt?.substring(0, 10) || '-' }}</span>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="flex gap-2 mb-3">
          <a-button @click="relationGraphOpen = true" class="flex-1">角色关系图</a-button>
          <a-dropdown>
            <a-button class="flex-1">导出 ▾</a-button>
            <template #overlay>
              <a-menu @click="({ key }) => { try { api.export[key](detailNovel.id) } catch {} }">
                <a-menu-item key="epub">EPUB 电子书</a-menu-item>
                <a-menu-item key="txt">TXT 文本</a-menu-item>
                <a-menu-item key="md">Markdown</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
        <div class="flex gap-3">
          <a-button type="primary" @click="saveDetail" :loading="savingDetail" class="flex-1">保存修改</a-button>
          <a-popconfirm
            title="确定删除这本小说？所有章节将一并删除，不可恢复。"
            ok-text="确认删除"
            cancel-text="取消"
            placement="top"
            @confirm="deleteNovelFromDetail"
          >
            <a-button danger>删除本书</a-button>
          </a-popconfirm>
        </div>
      </template>
    </a-drawer>

    <!-- 角色关系图谱 -->
    <CharacterGraph :novel-id="detailNovel?.id" v-model:open="relationGraphOpen" />
  </div>
</template>
