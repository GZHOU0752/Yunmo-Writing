<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApi } from '@/composables/useApi'
import { useNovelStore } from '@/composables/useNovelStore'

const route = useRoute()
const router = useRouter()
const api = useApi()
const novelStore = useNovelStore()
const novelId = route.params.id

const current = ref(0)

// Step 1: 类型确认
const genreId = ref('xianxia')
const genres = ref([])

// Step 2: 世界观
const worldElements = ref([])
const newWorldName = ref('')
const newWorldDesc = ref('')

// Step 3: 角色
const characters = ref([])
const newCharName = ref('')
const newCharDesc = ref('')

onMounted(async () => {
  try {
    const novel = await api.novels.get(novelId)
    genreId.value = novel.genreId
  } catch (e) {
    console.error('加载小说信息失败:', e)
  }
  // 动态加载类型列表
  try {
    await novelStore.fetchGenres()
    genres.value = novelStore.genres
  } catch { genres.value = [] }
  // 加载已有世界观元素和角色
  try {
    worldElements.value = await api.world.list(novelId)
    characters.value = await api.characters.list(novelId)
  } catch { /* 首次创建时可能为空 */ }
})

async function addWorldElement() {
  if (!newWorldName.value.trim()) return
  try {
    const w = await api.world.create(novelId, {
      name: newWorldName.value.trim(),
      description: newWorldDesc.value,
      element_type: 'OTHER',
    })
    worldElements.value.push(w)
    newWorldName.value = ''
    newWorldDesc.value = ''
  } catch (e) {
    console.error('添加世界观元素失败:', e)
  }
}

async function addCharacter() {
  if (!newCharName.value.trim()) return
  try {
    const c = await api.characters.create(novelId, {
      name: newCharName.value.trim(),
      description: newCharDesc.value,
      role: 'SUPPORTING',
      importance: 5,
    })
    characters.value.push(c)
    newCharName.value = ''
    newCharDesc.value = ''
  } catch (e) {
    console.error('添加角色失败:', e)
  }
}

function finish() {
  router.push(`/novels/${novelId}/write`)
}
</script>

<template>
  <div class="min-h-[100dvh] p-6 max-w-3xl mx-auto">
    <h1 class="text-2xl font-bold text-[var(--yunmo-accent)] mb-6">设定你的小说</h1>

    <a-steps :current="current" class="mb-8">
      <a-step title="选择类型" />
      <a-step title="世界观" />
      <a-step title="角色设定" />
      <a-step title="完成" />
    </a-steps>

    <!-- Step 0: 类型 -->
    <div v-if="current === 0" class="yunmo-card p-6">
      <h2 class="text-lg font-semibold mb-4">选择类型</h2>
      <a-radio-group v-model:value="genreId">
        <a-radio
          v-for="g in (genres.length > 0 ? genres : [{ id: 'xuanhuan', name: '玄幻' }, { id: 'qihuan', name: '奇幻' }, { id: 'xianxia', name: '仙侠' }, { id: 'dushi', name: '都市' }, { id: 'xuanyi', name: '悬疑灵异' }, { id: 'qingxiaoshuo', name: '轻小说' }])"
          :key="g.id"
          :value="g.id"
          class="block mt-2"
        >{{ g.name }}</a-radio>
      </a-radio-group>
      <a-button type="primary" class="mt-6" @click="current = 1">下一步</a-button>
    </div>

    <!-- Step 1: 世界观 -->
    <div v-if="current === 1" class="yunmo-card p-6">
      <h2 class="text-lg font-semibold mb-4">世界观设定</h2>
      <div class="flex gap-2 mb-4">
        <a-input v-model:value="newWorldName" placeholder="元素名称" class="flex-1" />
        <a-input v-model:value="newWorldDesc" placeholder="描述" class="flex-1" />
        <a-button @click="addWorldElement">添加</a-button>
      </div>
      <a-list :data-source="worldElements" size="small">
        <template #renderItem="{ item }">
          <a-list-item>
            <strong>{{ item.name }}</strong>: {{ item.description }}
          </a-list-item>
        </template>
      </a-list>
      <div class="flex gap-2 mt-6">
        <a-button @click="current = 0">上一步</a-button>
        <a-button type="primary" @click="current = 2">下一步</a-button>
      </div>
    </div>

    <!-- Step 2: 角色 -->
    <div v-if="current === 2" class="yunmo-card p-6">
      <h2 class="text-lg font-semibold mb-4">角色设定</h2>
      <div class="flex gap-2 mb-4">
        <a-input v-model:value="newCharName" placeholder="角色名" />
        <a-input v-model:value="newCharDesc" placeholder="描述" class="flex-1" />
        <a-button @click="addCharacter">添加</a-button>
      </div>
      <a-list :data-source="characters" size="small">
        <template #renderItem="{ item }">
          <a-list-item>
            <strong>{{ item.name }}</strong>: {{ item.description }}
          </a-list-item>
        </template>
      </a-list>
      <div class="flex gap-2 mt-6">
        <a-button @click="current = 1">上一步</a-button>
        <a-button type="primary" @click="current = 3">下一步</a-button>
      </div>
    </div>

    <!-- Step 3: 完成 -->
    <div v-if="current === 3" class="yunmo-card p-6 text-center">
      <h2 class="text-xl font-semibold mb-2">一切就绪</h2>
      <p class="text-[var(--yunmo-text-caption)] mb-6">你可以随时在写作界面补充世界观和角色设定</p>
      <a-button type="primary" size="large" @click="finish">开始写作</a-button>
    </div>
  </div>
</template>
