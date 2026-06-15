<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWriteStore } from '@/composables/useWriteStore'

const route = useRoute()
const router = useRouter()
const store = useWriteStore()
const novelId = route.params.id

const fontSize = ref(parseInt(localStorage.getItem('yunmo-reader-fontsize') || '18'))
const lineHeight = ref(parseFloat(localStorage.getItem('yunmo-reader-lineheight') || '2.0'))
const currentIdx = ref(0)

const chapters = computed(() => store.chapters)
const currentChapter = computed(() => chapters.value[currentIdx.value] || null)
const totalChapters = computed(() => chapters.value.length)

function savePrefs() {
  localStorage.setItem('yunmo-reader-fontsize', fontSize.value)
  localStorage.setItem('yunmo-reader-lineheight', lineHeight.value)
}

function changeFont(delta) {
  fontSize.value = Math.max(14, Math.min(28, fontSize.value + delta))
  savePrefs()
}
function toggleLineHeight() {
  lineHeight.value = lineHeight.value >= 2.4 ? 1.6 : 2.0
  savePrefs()
}
function prevChapter() { if (currentIdx.value > 0) currentIdx.value-- }
function nextChapter() { if (currentIdx.value < totalChapters.value - 1) currentIdx.value++ }

function back() { router.push(`/novels/${novelId}/write`) }

// 提取纯文本
function plainText(content) {
  if (!content) return ''
  const doc = new DOMParser().parseFromString(content, 'text/html')
  return doc.body?.textContent || ''
}

// 键盘快捷键
function onKey(e) {
  if (e.key === 'Escape') back()
  if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') prevChapter()
  if (e.key === 'ArrowRight' || e.key === 'ArrowDown') nextChapter()
}

onMounted(async () => {
  await store.fetchChapters(novelId)
  // 定位到当前正在编辑的章节
  if (store.currentChapter) {
    const idx = chapters.value.findIndex(c => c.chapterNumber === store.currentChapter.chapterNumber)
    if (idx >= 0) currentIdx.value = idx
  }
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => document.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="min-h-[100dvh]" style="background:var(--yunmo-paper);color:var(--yunmo-ink)">
    <!-- 顶部工具栏 -->
    <header class="h-12 border-b flex items-center px-4 gap-4" style="border-color:var(--yunmo-border)">
      <a-button size="small" type="text" class="toolbar-btn" @click="back">← 退出阅读</a-button>
      <span class="text-xs" style="color:var(--yunmo-text-caption)">
        第 {{ currentIdx + 1 }} / {{ totalChapters }} 章
      </span>
      <div class="flex-1" />
      <span class="text-xs" style="color:var(--yunmo-text-caption)">字号</span>
      <a-button size="small" type="text" class="toolbar-btn" @click="changeFont(-2)">A-</a-button>
      <a-button size="small" type="text" class="toolbar-btn" @click="changeFont(2)">A+</a-button>
      <div class="w-px h-4" style="background:var(--yunmo-border)" />
      <a-button size="small" type="text" class="toolbar-btn" @click="toggleLineHeight">
        {{ lineHeight >= 2.4 ? '紧凑' : '适中' }}
      </a-button>
      <a-button size="small" type="text" class="toolbar-btn" @click="prevChapter" :disabled="currentIdx <= 0">← 上一章</a-button>
      <a-button size="small" type="text" class="toolbar-btn" @click="nextChapter" :disabled="currentIdx >= totalChapters - 1">下一章 →</a-button>
    </header>

    <!-- 正文区域 -->
    <main class="max-w-2xl mx-auto px-6 md:px-10 py-10 md:py-16">
      <h1 v-if="currentChapter" class="text-center mb-8" style="font-size:1.5rem;font-weight:700">
        {{ currentChapter.title || '第' + currentChapter.chapterNumber + '章' }}
      </h1>
      <div
        v-if="currentChapter"
        class="leading-relaxed"
        :style="{ fontSize: fontSize + 'px', lineHeight: lineHeight }"
      >
        <p v-for="(para, i) in plainText(currentChapter.content).split('\n').filter(Boolean)" :key="i"
           class="mb-4" style="text-indent:2em">
          {{ para }}
        </p>
      </div>
      <div v-else class="text-center py-20" style="color:var(--yunmo-text-caption)">暂无章节内容</div>
    </main>
  </div>
</template>
