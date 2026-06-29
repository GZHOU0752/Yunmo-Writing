<script setup>
/**
 * 章节列表组件 — 支持卷/章层级结构
 * 每10章自动分卷，可折叠展开
 */
import { computed, ref } from 'vue'
import { useWriteStore } from '@/composables/useWriteStore'

const props = defineProps({
  novelId: { type: String, required: true },
  selectedChapterNum: { type: Number, required: true },
  chapterSearchQuery: { type: String, default: '' },
  compact: { type: Boolean, default: false }, // 移动端紧凑模式
})

const emit = defineEmits([
  'update:selectedChapterNum',
  'update:chapterSearchQuery',
  'delete-chapter',
])

const store = useWriteStore()

// 卷折叠状态：哪些卷是展开的
const expandedVolumes = ref(new Set([1])) // 默认展开第一卷

/** 每卷章节数 */
const CHAPTERS_PER_VOLUME = 10

/** 章节列表搜索过滤 */
const filteredChapters = computed(() => {
  if (!props.chapterSearchQuery.trim()) return store.chapters
  const q = props.chapterSearchQuery.trim().toLowerCase()
  return store.chapters.filter(ch =>
    (ch.title || '').toLowerCase().includes(q) ||
    `第${ch.chapterNumber}章`.includes(q) ||
    ch.chapterNumber.toString().includes(q)
  )
})

/** 按卷分组的章节列表 */
const groupedChapters = computed(() => {
  const chapters = filteredChapters.value
  if (chapters.length === 0) return []

  const groups = []
  const totalVolumes = Math.ceil(chapters.length / CHAPTERS_PER_VOLUME)

  for (let vol = 1; vol <= totalVolumes; vol++) {
    const startIdx = (vol - 1) * CHAPTERS_PER_VOLUME
    const endIdx = Math.min(startIdx + CHAPTERS_PER_VOLUME, chapters.length)
    const volChapters = chapters.slice(startIdx, endIdx)

    groups.push({
      volume: vol,
      title: `第${vol}卷`,
      chapters: volChapters,
      startChapter: volChapters[0]?.chapterNumber || 0,
      endChapter: volChapters[volChapters.length - 1]?.chapterNumber || 0,
    })
  }

  return groups
})

/** 切换卷的展开/折叠 */
function toggleVolume(vol) {
  if (expandedVolumes.value.has(vol)) {
    expandedVolumes.value.delete(vol)
  } else {
    expandedVolumes.value.add(vol)
  }
}

/** 章节状态徽章颜色 */
function chapterStatusColor(ch) {
  if (ch.status === 'GENERATED' || ch.status === 'FINALIZED') return 'var(--yunmo-green)'
  if (ch.status === 'DRAFT' || ch.status === 'IN_PROGRESS') return 'var(--yunmo-amber)'
  return 'var(--yunmo-text-caption)'
}

function selectChapter(cn) {
  emit('update:selectedChapterNum', cn)
}

function deleteChapter(cn) {
  emit('delete-chapter', cn)
}
</script>

<template>
  <div>
    <!-- 搜索框 -->
    <a-input
      :value="chapterSearchQuery"
      @update:value="emit('update:chapterSearchQuery', $event)"
      placeholder="搜索章节..."
      size="small"
      allow-clear
      class="mb-2"
    />

    <!-- 章节列表（按卷分组） -->
    <div class="flex-1 chapter-timeline overflow-y-auto" :class="{ 'space-y-1': compact }">
      <!-- 搜索模式：扁平列表 -->
      <template v-if="chapterSearchQuery.trim()">
        <div
          v-for="ch in filteredChapters"
          :key="ch.chapterNumber"
          class="chapter-timeline-item"
          :class="{ 'active': selectedChapterNum === ch.chapterNumber }"
          @click="selectChapter(ch.chapterNumber)"
        >
          <div
            class="px-3 py-2 rounded-md cursor-pointer text-sm group transition-spring"
            :class="selectedChapterNum === ch.chapterNumber
              ? 'bg-[var(--yunmo-accent)] text-[var(--yunmo-paper-light)] shadow-sm'
              : 'hover:bg-[var(--yunmo-paper-dark)]'"
          >
            <div class="flex items-center gap-1.5">
              <span class="w-2 h-2 rounded-full shrink-0" :style="{ background: chapterStatusColor(ch) }" />
              <span class="truncate text-[13px] font-medium flex-1">{{ ch.title || '第' + ch.chapterNumber + '章' }}</span>
              <span
                v-if="store.chapters.length > 1"
                class="text-xs shrink-0 cursor-pointer opacity-0 group-hover:opacity-100 transition-fast"
                :class="selectedChapterNum === ch.chapterNumber ? 'text-[var(--yunmo-paper-light)]' : 'text-[var(--yunmo-red)]'"
                @click.stop="deleteChapter(ch.chapterNumber)"
              >&times;</span>
            </div>
          </div>
        </div>
      </template>

      <!-- 正常模式：按卷分组 -->
      <template v-else>
        <div v-for="group in groupedChapters" :key="group.volume" class="mb-1">
          <!-- 卷标题（可折叠） -->
          <button
            v-if="groupedChapters.length > 1"
            class="w-full flex items-center gap-1.5 px-2 py-1.5 text-xs font-medium rounded transition-fast"
            :class="expandedVolumes.has(group.volume) ? 'text-[var(--yunmo-accent)]' : 'text-caption hover:bg-[var(--yunmo-paper-dark)]'"
            @click="toggleVolume(group.volume)"
          >
            <svg
              width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
              :style="{ transform: expandedVolumes.has(group.volume) ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }"
            >
              <path d="M6 9l6 6 6-6" />
            </svg>
            <span>{{ group.title }}</span>
            <span class="text-[10px] opacity-50 ml-auto">{{ group.chapters.length }}章</span>
          </button>

          <!-- 卷内章节 -->
          <div v-if="expandedVolumes.has(group.volume) || groupedChapters.length === 1">
            <div
              v-for="(ch, idx) in group.chapters"
              :key="ch.chapterNumber"
              class="chapter-timeline-item"
              :class="{ 'active': selectedChapterNum === ch.chapterNumber }"
              :style="compact ? {} : { animationDelay: idx * 20 + 'ms', animation: 'chapterSlideIn 0.3s cubic-bezier(0.16,1,0.3,1) both' }"
              @click="selectChapter(ch.chapterNumber)"
            >
              <div
                class="px-3 py-2 rounded-md cursor-pointer text-sm group transition-spring"
                :class="selectedChapterNum === ch.chapterNumber
                  ? 'bg-[var(--yunmo-accent)] text-[var(--yunmo-paper-light)] shadow-sm'
                  : 'hover:bg-[var(--yunmo-paper-dark)]'"
                :style="selectedChapterNum === ch.chapterNumber ? { boxShadow: '0 2px 8px rgba(139,58,58,0.2)' } : {}"
              >
                <div class="flex items-center gap-1.5">
                  <!-- 状态圆点 -->
                  <span
                    v-if="!compact"
                    class="w-2 h-2 rounded-full shrink-0"
                    :style="{ background: chapterStatusColor(ch) }"
                    :title="ch.status || '未开始'"
                  />
                  <span class="truncate text-[13px] font-medium flex-1">{{ ch.title || '第' + ch.chapterNumber + '章' }}</span>
                  <span
                    v-if="store.chapters.length > 1"
                    class="text-xs shrink-0 cursor-pointer opacity-0 group-hover:opacity-100 transition-fast"
                    :class="selectedChapterNum === ch.chapterNumber ? 'text-[var(--yunmo-paper-light)]' : 'text-[var(--yunmo-red)]'"
                    @click.stop="deleteChapter(ch.chapterNumber)"
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
                  <div v-if="ch.targetWordCount && !compact" class="flex-1 novel-progress" style="height:2px">
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
          </div>
        </div>
      </template>

      <div v-if="store.chapters.length === 0" class="text-caption p-2 text-xs">
        墨已备好，点 + 新建章节，或让 AI 为你着笔
      </div>
      <div v-if="filteredChapters.length === 0 && store.chapters.length > 0" class="text-caption text-xs p-2 text-center">
        无匹配章节
      </div>
    </div>
  </div>
</template>
