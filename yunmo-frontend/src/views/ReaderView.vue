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

// 提取纯文本，保持段落结构
function plainText(content) {
  if (!content) return ''
  // 已经是纯文本（无 HTML 标签）则直接返回
  if (!/<[a-zA-Z][^>]*>/.test(content)) return content
  // HTML → 按段落拆分
  const doc = new DOMParser().parseFromString(content, 'text/html')
  const paragraphs = []
  doc.body?.childNodes.forEach(node => {
    const text = node.textContent?.trim()
    if (text) paragraphs.push(text)
  })
  return paragraphs.join('\n\n')
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
      <span class="text-xs">
        第 {{ currentIdx + 1 }} / {{ totalChapters }} 章
      </span>
      <div class="flex-1" />
      <span class="text-xs">字号</span>
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
    <main class="max-w-[65ch] mx-auto px-6 md:px-12 py-10 md:py-20 reader-mode"
      :style="{ fontSize: fontSize + 'px', lineHeight: lineHeight }">
      <h1 v-if="currentChapter" class="font-brush text-center mb-8" style="font-size:1.75rem;font-weight:400;letter-spacing:0.12em">
        {{ currentChapter.title || '第' + currentChapter.chapterNumber + '章' }}
      </h1>
      <div v-if="currentChapter">
        <p v-for="(para, i) in plainText(currentChapter.content).split('\n').filter(Boolean)" :key="i">
          {{ para }}
        </p>
      </div>
      <div v-else class="text-center py-20">
        <p class="text-lg mb-2">尚无笔墨</p>
        <p class="text-sm">返回写作页面，写下第一章</p>
      </div>
    </main>
  </div>
</template>
