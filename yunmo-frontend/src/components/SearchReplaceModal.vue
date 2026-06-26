<script setup>
import { ref, computed } from 'vue'
import { useApi } from '@/composables/useApi'
import { message } from 'ant-design-vue'

const props = defineProps({ novelId: String, open: Boolean })
const emit = defineEmits(['update:open', 'jumpTo'])

const api = useApi()
const keyword = ref('')

const searching = ref(false)
const results = ref([])
const replacing = ref(false)
const replaceText = ref('')
const mode = ref('search') // 'search' | 'replace'

// 搜索选项
const useRegex = ref(false)
const caseSensitive = ref(false)
const wholeWord = ref(false)

// 实时匹配计数
const totalMatches = computed(() => results.value.reduce((s, r) => s + r.matchCount, 0))

async function doSearch() {
  if (!keyword.value.trim()) return
  searching.value = true
  try {
    results.value = await api.novels.search(props.novelId, keyword.value)
  } finally { searching.value = false }
}

/** 将上下文按关键词拆分，用于高亮渲染 */
function highlightContext(context, kw) {
  if (!kw) return [context]
  const parts = []
  let rest = context
  let idx = 0
  let prev = 0

  if (useRegex.value) {
    try {
      const flags = caseSensitive.value ? 'g' : 'gi'
      const regex = new RegExp(kw, flags)
      let match
      while ((match = regex.exec(rest)) !== null) {
        const matchIdx = match.index
        const matchLen = match[0].length
        if (matchIdx > prev) parts.push(rest.slice(prev, matchIdx))
        parts.push(rest.slice(matchIdx, matchIdx + matchLen))
        prev = matchIdx + matchLen
        if (matchLen === 0) { regex.lastIndex++; break }
      }
    } catch {
      return [context]
    }
  } else {
    const lower = caseSensitive.value ? rest : rest.toLowerCase()
    const lowerKw = caseSensitive.value ? kw : kw.toLowerCase()

    if (wholeWord.value) {
      const wordBoundary = '(?<![\\w\\u4e00-\\u9fa5])'
      const wordBoundaryEnd = '(?![\\w\\u4e00-\\u9fa5])'
      try {
        const regex = new RegExp(wordBoundary + kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + wordBoundaryEnd, caseSensitive.value ? 'g' : 'gi')
        let match
        while ((match = regex.exec(rest)) !== null) {
          const matchIdx = match.index
          const matchLen = match[0].length
          if (matchIdx > prev) parts.push(rest.slice(prev, matchIdx))
          parts.push(rest.slice(matchIdx, matchIdx + matchLen))
          prev = matchIdx + matchLen
          if (matchLen === 0) break
        }
      } catch {
        return [context]
      }
    } else {
      while ((idx = lower.indexOf(lowerKw, idx)) >= 0) {
        if (idx > prev) parts.push(rest.slice(prev, idx))
        parts.push(rest.slice(idx, idx + kw.length))
        idx += kw.length
        prev = idx
      }
    }
  }

  if (prev < rest.length) parts.push(rest.slice(prev))
  return parts
}

async function doReplace() {
  if (!replaceText.value || results.value.length === 0) return
  replacing.value = true
  try {
    const chapterNumbers = results.value.map(r => r.chapterNumber)
    const res = await api.novels.replace(props.novelId, keyword.value, replaceText.value, chapterNumbers)
    message.success(`替换完成：${res.replaced} 处，涉及 ${res.chapterCount} 章`)
    results.value = []
    keyword.value = ''
    replaceText.value = ''
    mode.value = 'search'
  } catch (e) {
    message.error('替换失败')
  } finally { replacing.value = false }
}
</script>

<template>
  <a-modal :open="open" title="全文搜索替换" @cancel="$emit('update:open', false)" :footer="null" width="640px">
    <div class="flex gap-2 mb-2">
      <a-input
        v-model:value="keyword"
        placeholder="搜索关键词..."
        @pressEnter="doSearch"
        class="flex-1"
      />
      <a-button type="primary" @click="doSearch" :loading="searching">搜索</a-button>
    </div>

    <!-- 搜索选项 -->
    <div class="flex items-center gap-4 mb-3 px-1">
      <label class="flex items-center gap-1.5 text-xs cursor-pointer" style="color:var(--yunmo-text-caption)">
        <input type="checkbox" v-model="useRegex" class="cursor-pointer" />
        正则
      </label>
      <label class="flex items-center gap-1.5 text-xs cursor-pointer" style="color:var(--yunmo-text-caption)">
        <input type="checkbox" v-model="caseSensitive" class="cursor-pointer" />
        区分大小写
      </label>
      <label class="flex items-center gap-1.5 text-xs cursor-pointer" style="color:var(--yunmo-text-caption)">
        <input type="checkbox" v-model="wholeWord" :disabled="useRegex" class="cursor-pointer" />
        全词匹配
      </label>
      <span v-if="totalMatches > 0" class="text-xs font-tabular ml-auto" style="color:var(--yunmo-accent)">
        {{ totalMatches }} 处匹配
      </span>
    </div>

    <a-spin :spinning="searching || replacing">
      <div v-if="results.length > 0">
        <div class="flex items-center justify-between mb-2">
          <span class="text-xs" style="color:var(--yunmo-text-caption)">
            共 {{ results.length }} 个章节匹配
          </span>
          <div class="flex gap-2">
            <a-input v-if="mode === 'replace'" v-model:value="replaceText" placeholder="替换为..." size="small" style="width:140px" />
            <a-button v-if="mode === 'search'" size="small" @click="mode = 'replace'">批量替换</a-button>
            <a-button v-if="mode === 'replace'" size="small" type="primary" @click="doReplace" :loading="replacing">确认替换</a-button>
            <a-button v-if="mode === 'replace'" size="small" @click="mode = 'search'">取消</a-button>
          </div>
        </div>
        <div v-for="r in results" :key="r.chapterNumber" class="mb-3">
          <div class="text-xs font-semibold mb-1 cursor-pointer" style="color:var(--yunmo-accent)" @click="emit('jumpTo', r.chapterNumber); emit('update:open', false)">
            第{{ r.chapterNumber }}章 {{ r.title || '' }}（{{ r.matchCount }} 处）
          </div>
          <div v-for="(m, i) in r.matches" :key="i" class="text-xs py-0.5 pl-2 border-l-2 border-[var(--yunmo-border)] mb-1" style="color:var(--yunmo-text-secondary);font-family:monospace">
            <span v-if="m.matchCount > 1" class="text-[10px] mr-1" style="color:var(--yunmo-accent)">×{{ m.matchCount }}</span>
            <template v-for="(part, j) in highlightContext(m.context, keyword)" :key="j">
              <span v-if="part.toLowerCase() === keyword.toLowerCase() || (caseSensitive && part === keyword)" style="background:var(--yunmo-accent);color:#faf6ed;padding:0 2px;border-radius:2px">{{ part }}</span>
              <span v-else>{{ part }}</span>
            </template>
          </div>
        </div>
      </div>
      <div v-if="!searching && results.length === 0 && keyword" class="text-center py-8">
        <span class="text-sm" style="color:var(--yunmo-text-caption)">未找到匹配结果</span>
      </div>
    </a-spin>
  </a-modal>
</template>
