<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useApi } from '@/composables/useApi'

const props = defineProps({
  novelId: String,
  chapters: { type: Array, default: () => [] },
  currentChapter: { type: Number, default: 1 },
})

const api = useApi()
const characters = ref([])
const loading = ref(false)

const totalChapters = computed(() => props.chapters.length || 1)

const roleColors = {
  PROTAGONIST: '#8b3a3a',
  ANTAGONIST: '#b3443a',
  SUPPORTING: '#b8956c',
  MINOR: '#5a7a5a',
}
const roleLabels = { PROTAGONIST: '主角', ANTAGONIST: '反派', SUPPORTING: '配角', MINOR: '龙套' }

onMounted(() => {
  loadCharacters()
})

/** 章节数量变化时自动刷新角色列表 */
watch(() => props.chapters.length, () => {
  loadCharacters()
})

/** 当前章节变化时也刷新（确保"本章出场"标记准确） */
watch(() => props.currentChapter, () => {
  loadCharacters()
})

async function loadCharacters() {
  if (!props.novelId) return
  loading.value = true
  try {
    characters.value = await api.characters.list(props.novelId)
  } catch (e) {
    console.error('加载角色失败:', e)
  } finally {
    loading.value = false
  }
}

/** 暴露刷新方法供父组件调用 */
defineExpose({ refresh: loadCharacters })

/** 计算角色出场章节列表 */
function getAppearanceChapters(character) {
  if (!character.lastAppearanceChapter) return []
  const start = character.firstAppearanceChapter || 1
  const end = character.lastAppearanceChapter || totalChapters.value
  const chapters = []
  for (let i = start; i <= Math.min(end, totalChapters.value); i++) {
    chapters.push(i)
  }
  return chapters
}

/** 出场率 */
function appearanceRate(character) {
  const appearances = getAppearanceChapters(character).length
  return Math.round((appearances / totalChapters.value) * 100)
}

const selectedChar = ref(null)
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-3">
      <span class="text-xs font-semibold" style="color:var(--yunmo-accent)">角色进度</span>
      <span class="text-[10px]">{{ characters.length }} 位</span>
    </div>

    <a-spin :spinning="loading" size="small">
      <div v-if="characters.length === 0" class="text-center py-4">
        <div class="mb-1 opacity-30 flex justify-center">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5">
            <circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 4-7 8-7s8 3 8 7"/>
          </svg>
        </div>
        <p class="text-xs" style="color: var(--yunmo-text-caption)">生成章节后将自动识别角色</p>
      </div>

      <div v-else class="space-y-2">
        <div
          v-for="char in characters"
          :key="char.id"
          class="yunmo-card-ghost p-3 cursor-pointer"
          :class="{ 'border-[var(--yunmo-accent)]': selectedChar?.id === char.id }"
          @click="selectedChar = char"
        >
          <!-- 角色基本信息 -->
          <div class="flex items-center gap-2 mb-2">
            <span class="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold text-white shrink-0"
              :style="{ background: roleColors[char.role] || '#888' }">
              {{ char.name?.charAt(0) }}
            </span>
            <div class="flex-1 min-w-0">
              <div class="text-sm font-semibold truncate">{{ char.name }}</div>
              <div class="flex items-center gap-1">
                <span class="text-[10px]">
                  {{ roleLabels[char.role] || char.role }}
                </span>
                <span class="w-1 h-1 rounded-full bg-[var(--yunmo-border)]" />
                <span class="text-[10px] font-tabular">
                  重要度 {{ char.importance || 5 }}/10
                </span>
              </div>
            </div>
          </div>

          <!-- 出场进度条 -->
          <div>
            <div class="flex items-center justify-between text-[10px] mb-1">
              <span>出场率</span>
              <span class="font-tabular">{{ appearanceRate(char) }}%</span>
            </div>
            <div class="novel-progress">
              <div
                class="novel-progress-bar"
                :style="{
                  width: `${appearanceRate(char)}%`,
                  background: appearanceRate(char) > 60
                    ? 'var(--yunmo-green)'
                    : appearanceRate(char) > 30
                      ? 'var(--yunmo-amber)'
                      : 'var(--yunmo-accent-light)',
                }"
              />
            </div>
            <div class="text-[10px] mt-1">
              第 {{ char.firstAppearanceChapter || 1 }}-{{ char.lastAppearanceChapter || totalChapters }} 章
            </div>
          </div>

          <!-- 当前章出场标记 -->
          <div v-if="char.lastAppearanceChapter === currentChapter" class="mt-2 flex items-center gap-1">
            <span class="w-1.5 h-1.5 rounded-full bg-[var(--yunmo-accent)] status-pulse" />
            <span class="text-[10px]" style="color:var(--yunmo-accent)">本章出场</span>
          </div>
        </div>
      </div>
    </a-spin>

    <!-- 角色详情展开 -->
    <div v-if="selectedChar" class="mt-3 yunmo-card p-3 space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-xs font-semibold">{{ selectedChar.name }}</span>
        <button class="text-xs" style="color:var(--yunmo-text-caption)" @click="selectedChar = null">关闭</button>
      </div>

      <!-- 6层认知摘要 -->
      <div v-if="selectedChar.layer1Worldview || selectedChar.layer2Identity || selectedChar.layer3Values" class="space-y-1.5">
        <div v-if="selectedChar.layer1Worldview" class="text-[11px]">
          <span>世界观：</span>
          <span style="color:var(--yunmo-text-secondary)">{{ selectedChar.layer1Worldview }}</span>
        </div>
        <div v-if="selectedChar.layer2Identity" class="text-[11px]">
          <span>身份：</span>
          <span style="color:var(--yunmo-text-secondary)">{{ selectedChar.layer2Identity }}</span>
        </div>
        <div v-if="selectedChar.layer3Values" class="text-[11px]">
          <span>价值观：</span>
          <span style="color:var(--yunmo-text-secondary)">{{ selectedChar.layer3Values }}</span>
        </div>
      </div>

      <!-- 当前状态 -->
      <div v-if="selectedChar.currentState" class="text-[11px]">
        <span>当前状态：</span>
        <span style="color:var(--yunmo-text-secondary)">
          {{ typeof selectedChar.currentState === 'string'
            ? selectedChar.currentState
            : JSON.stringify(selectedChar.currentState) }}
        </span>
      </div>

      <!-- 描述 -->
      <p v-if="selectedChar.description" class="text-[11px] leading-relaxed" style="color:var(--yunmo-text-secondary)">
        {{ selectedChar.description }}
      </p>
    </div>
  </div>
</template>
