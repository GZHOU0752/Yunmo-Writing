<script setup>
/**
 * 移动端底部导航栏 + 章节/工具面板
 */
import { useWriteStore } from '@/composables/useWriteStore'
import GeneratePanel from '@/components/GeneratePanel.vue'
import AIChatPanel from '@/components/AIChatPanel.vue'
import ReferenceMaterialList from '@/components/ReferenceMaterialList.vue'
import OutlinePanel from '@/components/outline/OutlinePanel.vue'
import CharacterProgressPanel from '@/components/CharacterProgressPanel.vue'
import EventTimeline from '@/components/EventTimeline.vue'
import WriteChapterList from './WriteChapterList.vue'
import { message } from 'ant-design-vue'

const props = defineProps({
  novelId: { type: String, required: true },
  selectedChapterNum: { type: Number, required: true },
  chapterSearchQuery: { type: String, default: '' },
  mobilePanel: { type: String, default: 'none' },
  currentWritingStyle: { type: String, default: '' },
})

const emit = defineEmits([
  'update:selectedChapterNum',
  'update:chapterSearchQuery',
  'update:mobilePanel',
  'generate',
  'clear-checkpoint',
  'chat-insert',
  'save',
  'delete-chapter',
])

const store = useWriteStore()

import { ref } from 'vue'
const mobileLeftTab = ref('chapters')

function closePanel() {
  emit('update:mobilePanel', 'none')
}

function selectChapter(cn) {
  emit('update:selectedChapterNum', cn)
  closePanel()
}

async function handleSave() {
  emit('save')
}
</script>

<template>
  <!-- 底部导航栏 -->
  <nav class="md:hidden fixed bottom-0 left-0 right-0 h-14 bg-[var(--yunmo-paper-light)] border-t border-[var(--yunmo-border)] flex items-center justify-around px-2 z-50 safe-area-bottom">
    <button
      class="flex flex-col items-center gap-0.5 px-3 py-1 rounded-md transition-fast"
      :class="mobilePanel === 'chapters' ? 'text-[var(--yunmo-accent)]' : 'text-[var(--yunmo-text-caption)]'"
      @click="emit('update:mobilePanel', mobilePanel === 'chapters' ? 'none' : 'chapters')"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 6h16M4 12h16M4 18h16"/></svg>
      <span class="text-[10px] leading-none">章节</span>
    </button>
    <button
      class="flex flex-col items-center gap-0.5 px-3 py-1 rounded-md transition-fast"
      :class="mobilePanel === 'tools' ? 'text-[var(--yunmo-accent)]' : 'text-[var(--yunmo-text-caption)]'"
      @click="emit('update:mobilePanel', mobilePanel === 'tools' ? 'none' : 'tools')"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M12 1v4m0 14v4M4.22 4.22l2.83 2.83m9.9 9.9l2.83 2.83M1 12h4m14 0h4M4.22 19.78l2.83-2.83m9.9-9.9l2.83-2.83"/></svg>
      <span class="text-[10px] leading-none">工具</span>
    </button>
    <button
      class="flex flex-col items-center gap-0.5 px-4 py-1 rounded-md transition-fast"
      :class="store.sseStatus === 'generating' || store.sseStatus === 'reviewing' ? 'text-[var(--yunmo-red)]' : 'text-[var(--yunmo-accent)]'"
      @click="emit('generate')"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l2.4 7.2h7.6l-6 4.8 2.4 7.2-6-4.8-6 4.8 2.4-7.2-6-4.8h7.6z"/></svg>
      <span class="text-[10px] leading-none">{{ store.sseStatus === 'generating' || store.sseStatus === 'reviewing' ? '停止' : '生成' }}</span>
    </button>
    <button
      class="flex flex-col items-center gap-0.5 px-3 py-1 rounded-md transition-fast"
      style="color:var(--yunmo-text-caption)"
      @click="handleSave"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
      <span class="text-[10px] leading-none">保存</span>
    </button>
  </nav>

  <!-- 移动端章节列表面板 -->
  <Transition name="slide-up">
    <div v-if="mobilePanel === 'chapters'" class="md:hidden fixed inset-x-0 bottom-14 top-0 z-40 bg-[var(--yunmo-paper-light)] overflow-y-auto">
      <div class="sticky top-0 bg-[var(--yunmo-paper-light)] border-b border-[var(--yunmo-border)] p-3 flex items-center justify-between">
        <h3 class="text-sm font-semibold" style="color:var(--yunmo-accent)">章节列表</h3>
        <a-button size="small" type="text" @click="closePanel">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
        </a-button>
      </div>
      <!-- 移动端标签切换 -->
      <div class="flex border-b border-[var(--yunmo-border)] px-2">
        <button v-for="tab in [{k:'chapters',l:'章 节'},{k:'outline',l:'大 纲'},{k:'relations',l:'关 系'}]" :key="tab.k"
          class="flex-1 text-center py-2.5 text-xs transition-fast rounded-t"
          :class="mobileLeftTab === tab.k ? 'text-[var(--yunmo-accent)] font-semibold' : 'text-caption'"
          :style="mobileLeftTab === tab.k ? { borderBottom: '2px solid var(--yunmo-accent)', marginBottom: '-1px' } : {}"
          role="tab" :aria-selected="mobileLeftTab === tab.k" @click="mobileLeftTab = tab.k"
        >{{ tab.l }}</button>
      </div>
      <div class="p-3">
        <div v-if="mobileLeftTab === 'chapters'">
          <WriteChapterList
            :novel-id="novelId"
            :selected-chapter-num="selectedChapterNum"
            :chapter-search-query="chapterSearchQuery"
            compact
            @update:selected-chapter-num="selectChapter"
            @update:chapter-search-query="emit('update:chapterSearchQuery', $event)"
            @delete-chapter="emit('delete-chapter', $event)"
          />
        </div>
        <div v-if="mobileLeftTab === 'outline'">
          <OutlinePanel :novel-id="novelId" :chapters="store.chapters" :active-chapter="selectedChapterNum"
            @select-chapter="(cn) => selectChapter(cn)" />
        </div>
        <div v-if="mobileLeftTab === 'relations'">
          <CharacterProgressPanel :novel-id="novelId" :chapters="store.chapters" :current-chapter="selectedChapterNum" />
          <hr class="ink-divider my-3" />
          <EventTimeline :chapters="store.chapters" :current-chapter="selectedChapterNum"
            @select-chapter="(cn) => selectChapter(cn)" />
        </div>
      </div>
    </div>
  </Transition>

  <!-- 移动端工具面板 -->
  <Transition name="slide-up">
    <div v-if="mobilePanel === 'tools'" class="md:hidden fixed inset-x-0 bottom-14 top-0 z-40 bg-[var(--yunmo-paper)] overflow-y-auto">
      <div class="sticky top-0 bg-[var(--yunmo-paper)] border-b border-[var(--yunmo-border)] p-3 flex items-center justify-between">
        <h3 class="text-sm font-semibold" style="color:var(--yunmo-accent)">工具面板</h3>
        <a-button size="small" type="text" @click="closePanel">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
        </a-button>
      </div>
      <div class="p-4 flex flex-col gap-4">
        <GeneratePanel :novel-id="novelId" :chapter-number="selectedChapterNum" :sse-status="store.sseStatus"
          :streamed-text="store.streamedText" :quality-report="store.qualityReport" :checkpoint="store.checkpoint"
          :writing-style="currentWritingStyle" @generate="emit('generate')" @clear-checkpoint="emit('clear-checkpoint')" />
        <AIChatPanel :novel-id="novelId" :chapter-content="store.currentChapter?.content || ''"
          :chapter-number="selectedChapterNum" @insert-text="(text) => emit('chat-insert', text)" />
        <ReferenceMaterialList :novel-id="novelId" />
      </div>
    </div>
  </Transition>
</template>
