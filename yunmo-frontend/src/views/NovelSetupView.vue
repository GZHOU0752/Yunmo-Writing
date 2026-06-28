<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { useNovelStore } from '@/composables/useNovelStore'

const route = useRoute()
const router = useRouter()
const api = useApi()
const novelStore = useNovelStore()
const novelId = route.params.id

const activeTab = ref(0)

// Tab 0: 类型
const genreId = ref('xianxia')
const genres = ref([])
const genreCards = [
  { id: 'xuanhuan', icon: '', name: '东方玄幻', desc: '修真、斗气、异世界，宏大世界观' },
  { id: 'qihuan', icon: '', name: '西方奇幻', desc: '魔法、龙族、中世纪背景' },
  { id: 'xianxia', icon: '', name: '东方仙侠', desc: '修仙、天道、古典东方美学' },
  { id: 'dushi', icon: '', name: '都市', desc: '现代都市、职场、日常' },
  { id: 'xuanyi', icon: '', name: '悬疑灵异', desc: '推理、超自然、心理惊悚' },
  { id: 'qingxiaoshuo', icon: '', name: '轻小说', desc: '日系风格、轻松幽默' },
  { id: 'tongren', icon: '', name: '同人', desc: '基于原作的二次创作' },
  { id: 'duanpian', icon: '', name: '短篇', desc: '短篇故事、小品文' },
]

// Tab 1: 大纲
const outlineText = ref('')
const generatingOutline = ref(false)

// 完成指示
const tabCompleted = ref([false, false])

// 计算属性：是否可以进入下一步
const canGoNext = computed(() => {
  switch (activeTab.value) {
    case 0: return !!genreId.value
    default: return true
  }
})

onMounted(async () => {
  try {
    const novel = await api.novels.get(novelId)
    genreId.value = novel.genreId || 'xianxia'
    outlineText.value = novel.outline || ''
  } catch (e) {
    console.error('加载小说信息失败:', e)
  }
  try {
    await novelStore.fetchGenres()
    genres.value = novelStore.genres
  } catch { genres.value = [] }
})

async function selectGenre(gid) {
  genreId.value = gid
  try {
    await api.novels.update(novelId, { genre_id: gid })
    tabCompleted.value[0] = true
  } catch (e) {
    console.error('更新类型失败:', e)
  }
}

async function generateOutline() {
  generatingOutline.value = true
  try {
    const result = await api.novels.generateOutline(novelId)
    if (result?.outline) {
      outlineText.value = result.outline
      tabCompleted.value[1] = true
    }
  } catch (e) {
    console.error('生成大纲失败:', e)
  } finally {
    generatingOutline.value = false
  }
}

async function saveOutline() {
  try {
    await api.novels.update(novelId, { outline: outlineText.value })
    tabCompleted.value[1] = true
  } catch (e) {
    console.error('保存大纲失败:', e)
  }
}

function finish() {
  router.push(`/novels/${novelId}/write`)
}

</script>

<template>
  <div class="min-h-[100dvh] flex flex-col">
    <!-- 头部 -->
    <header class="h-14 border-b border-[var(--yunmo-border)] flex items-center px-6 bg-[var(--yunmo-paper-light)] flex-shrink-0">
      <span class="text-xs cursor-pointer hover:text-[var(--yunmo-accent)] transition-fast mr-2"
           
            @click="router.push('/dashboard')">书房</span>
      <span class="text-xs mr-2" style="color:var(--yunmo-border)">/</span>
      <span class="text-sm font-semibold">小说设定</span>
    </header>

    <div class="flex flex-1 overflow-hidden">
      <!-- 左侧 Tab 导航 -->
      <nav class="w-44 border-r border-[var(--yunmo-border)] bg-[var(--yunmo-paper-light)] flex flex-col py-4 flex-shrink-0">
        <button
          v-for="(tab, i) in ['类型', '大纲']"
          :key="i"
          class="setup-tab-item"
          :class="{
            active: activeTab === i,
            completed: tabCompleted[i],
          }"
          @click="activeTab = i"
        >
          <span
            class="w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold shrink-0 border-2"
            :style="{
              borderColor: tabCompleted[i] ? 'var(--yunmo-green)' : activeTab === i ? 'var(--yunmo-accent)' : 'var(--yunmo-border)',
              background: tabCompleted[i] ? 'var(--yunmo-green)' : activeTab === i ? 'var(--yunmo-accent)' : 'transparent',
              color: (tabCompleted[i] || activeTab === i) ? '#fff' : 'var(--yunmo-text-caption)',
            }"
          >
            <template v-if="tabCompleted[i]">✓</template>
            <template v-else>{{ i + 1 }}</template>
          </span>
          <span>{{ tab }}</span>
        </button>
      </nav>

      <!-- 右侧内容面板 -->
      <div class="flex-1 overflow-y-auto p-8">
        <!-- Tab 0: 类型 -->
        <div v-if="activeTab === 0">
          <h2 class="text-xl font-bold mb-2">选择小说类型</h2>
          <p class="text-sm mb-6">类型将决定 AI 的写作风格和禁词规则</p>

          <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div
              v-for="g in genreCards"
              :key="g.id"
              class="yunmo-card p-4 cursor-pointer transition-spring"
              :style="genreId === g.id
                ? { borderColor: 'var(--yunmo-accent)', boxShadow: '0 2px 12px rgba(139,58,58,0.12)' }
                : {}"
              @click="selectGenre(g.id)"
            >
              <div class="text-2xl mb-2">{{ g.icon }}</div>
              <div class="font-semibold text-sm" :style="genreId === g.id ? { color: 'var(--yunmo-accent)' } : { color: 'var(--yunmo-ink)' }">
                {{ g.name }}
              </div>
              <div class="text-xs mt-1">{{ g.desc }}</div>
            </div>
          </div>
        </div>

        <!-- Tab 1: 大纲 -->
        <div v-if="activeTab === 1">
          <h2 class="text-xl font-bold mb-2">全书大纲</h2>
          <p class="text-sm mb-4">
            概述整部小说的主线剧情，AI 将据此规划章节。全书目标总字数不少于 <strong style="color:var(--yunmo-accent)">150万字</strong>。
          </p>

          <div class="yunmo-card p-4">
            <a-textarea
              v-model:value="outlineText"
              placeholder="在这里写下你的故事大纲……&#10;&#10;例如：&#10;少年林风在山中偶然获得一枚神秘玉佩，从此踏上修仙之路。&#10;他先在外门弟子中崭露头角，后被卷入正邪之争……"
              :rows="12"
              class="w-full"
              @blur="saveOutline"
            />
            <div class="flex items-center justify-between mt-3">
              <span class="text-xs">自动保存 · {{ outlineText.length }} 字</span>
              <a-button
                type="primary"
                :loading="generatingOutline"
                @click="generateOutline"
              >
                {{ generatingOutline ? '生成中...' : 'AI 生成大纲' }}
              </a-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部操作栏 -->
    <div class="h-14 border-t border-[var(--yunmo-border)] bg-[var(--yunmo-paper-light)] flex items-center justify-between px-8 flex-shrink-0">
      <a-button
        v-if="activeTab > 0"
        @click="activeTab--"
      >上一步</a-button>
      <div v-else />

      <div class="flex items-center gap-3">
        <span class="text-xs">
          {{ activeTab + 1 }} / 2
        </span>
        <a-button
          v-if="activeTab < 1"
          type="primary"
          @click="activeTab++"
          :disabled="!canGoNext"
        >下一步</a-button>
        <a-button
          v-else
          type="primary"
          size="large"
          class="seal-btn"
          @click="finish"
        >开始写作</a-button>
      </div>
    </div>
  </div>
</template>
