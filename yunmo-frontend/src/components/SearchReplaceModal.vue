<script setup>
import { ref } from 'vue'
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
  const lower = rest.toLowerCase()
  const lowerKw = kw.toLowerCase()
  let idx = 0
  let prev = 0
  while ((idx = lower.indexOf(lowerKw, idx)) >= 0) {
    if (idx > prev) parts.push(rest.slice(prev, idx))
    parts.push(rest.slice(idx, idx + kw.length))
    idx += kw.length
    prev = idx
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
  } catch (e) {
    message.error('替换失败')
  } finally { replacing.value = false }
}
</script>

<template>
  <a-modal :open="open" title="全文搜索替换" @cancel="$emit('update:open', false)" :footer="null" width="600px">
    <div class="flex gap-2 mb-3">
      <a-input v-model:value="keyword" placeholder="搜索关键词..." @pressEnter="doSearch" class="flex-1" />
      <a-button type="primary" @click="doSearch" :loading="searching">搜索</a-button>
    </div>

    <a-spin :spinning="searching || replacing">
      <div v-if="results.length > 0">
        <div class="flex items-center justify-between mb-2">
          <span class="text-xs text-caption">共 {{ results.reduce((s, r) => s + r.matchCount, 0) }} 处匹配</span>
          <div class="flex gap-2">
            <a-input v-if="mode === 'replace'" v-model:value="replaceText" placeholder="替换为..." size="small" style="width:120px" />
            <a-button v-if="mode === 'search'" size="small" @click="mode = 'replace'">批量替换</a-button>
            <a-button v-if="mode === 'replace'" size="small" danger @click="doReplace" :loading="replacing">确认替换</a-button>
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
              <span v-if="part.toLowerCase() === keyword.toLowerCase()" style="background:var(--yunmo-accent);color:#faf6ed;padding:0 2px;border-radius:2px">{{ part }}</span>
              <span v-else>{{ part }}</span>
            </template>
          </div>
        </div>
      </div>
      <div v-if="!searching && results.length === 0 && keyword" class="text-center text-caption py-8">未找到匹配结果</div>
    </a-spin>
  </a-modal>
</template>
