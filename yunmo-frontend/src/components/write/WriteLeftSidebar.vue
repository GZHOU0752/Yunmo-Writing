<script setup>
/**
 * 左侧边栏 — 章节列表 / 大纲树 / 关系图
 * 大纲支持展开为全屏面板
 */
import OutlinePanel from '@/components/outline/OutlinePanel.vue'
import CharacterProgressPanel from '@/components/CharacterProgressPanel.vue'
import EventTimeline from '@/components/EventTimeline.vue'
import WriteChapterList from './WriteChapterList.vue'

const props = defineProps({
  novelId: { type: String, required: true },
  selectedChapterNum: { type: Number, required: true },
  chapterSearchQuery: { type: String, default: '' },
  outlineExpanded: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:selectedChapterNum',
  'update:chapterSearchQuery',
  'delete-chapter',
  'add-chapter',
  'toggle-outline-expand',
])

// 左栏标签状态（内部管理）
import { ref } from 'vue'
const leftTab = ref('chapters')

// 通过 defineExpose 暴露给父组件如果需要
defineExpose({ leftTab })
</script>

<template>
  <aside class="hidden md:flex w-56 bg-[var(--yunmo-paper-light)] border-r border-[var(--yunmo-border)] flex-col flex-shrink-0 transition-all duration-300 relative">
    <!-- 折叠按钮 -->
    <slot name="collapse-btn" />

    <!-- 标签切换 -->
    <div class="flex border-b border-[var(--yunmo-border)] px-2">
      <button
        v-for="tab in [{ k: 'chapters', l: '章 节' }, { k: 'outline', l: '大 纲' }, { k: 'relations', l: '关 系' }]"
        :key="tab.k"
        class="flex-1 text-center py-2.5 text-xs transition-fast rounded-t"
        :class="leftTab === tab.k
          ? 'text-[var(--yunmo-accent)] font-semibold'
          : 'text-caption hover:text-[var(--yunmo-text-secondary)]'"
        :style="leftTab === tab.k ? { borderBottom: '2px solid var(--yunmo-accent)', marginBottom: '-1px' } : {}"
        role="tab"
        :aria-selected="leftTab === tab.k"
        @click="leftTab = tab.k"
      >{{ tab.l }}</button>
    </div>

    <!-- 章节面板 -->
    <div v-if="leftTab === 'chapters'" class="p-3 flex-1 flex flex-col overflow-hidden">
      <div class="flex items-center justify-between mb-2">
        <h3 class="text-sm font-semibold" style="color:var(--yunmo-accent)">章节</h3>
        <a-button size="small" type="text" @click="emit('add-chapter')" title="新增章节">
          <span class="text-base leading-none">+</span>
        </a-button>
      </div>
      <WriteChapterList
        :novel-id="novelId"
        :selected-chapter-num="selectedChapterNum"
        :chapter-search-query="chapterSearchQuery"
        @update:selected-chapter-num="emit('update:selectedChapterNum', $event)"
        @update:chapter-search-query="emit('update:chapterSearchQuery', $event)"
        @delete-chapter="emit('delete-chapter', $event)"
      />
    </div>

    <!-- 大纲面板 -->
    <div v-if="leftTab === 'outline'" class="flex-1 overflow-y-auto">
      <div class="flex items-center justify-between px-3 pt-2">
        <span class="text-xs text-caption">大纲</span>
        <button
          class="toolbar-btn !p-1 !rounded"
          @click="emit('toggle-outline-expand')"
          :title="outlineExpanded ? '收回到侧栏' : '展开为全屏'"
        >
          <!-- 展开/收起图标 -->
          <svg v-if="!outlineExpanded" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7" />
          </svg>
          <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 14h6v6M20 10h-6V4M14 10l7-7M3 21l7-7" />
          </svg>
        </button>
      </div>
      <OutlinePanel
        :novel-id="novelId"
        :chapters="$attrs.chapters || []"
        :active-chapter="selectedChapterNum"
        @select-chapter="(cn) => { emit('update:selectedChapterNum', cn); leftTab = 'chapters' }"
      />
    </div>

    <!-- 关系面板 -->
    <div v-if="leftTab === 'relations'" class="flex-1 overflow-y-auto p-3 space-y-4">
      <CharacterProgressPanel
        :novel-id="novelId"
        :chapters="$attrs.chapters || []"
        :current-chapter="selectedChapterNum"
      />
      <hr class="ink-divider" />
      <EventTimeline
        :chapters="$attrs.chapters || []"
        :current-chapter="selectedChapterNum"
        @select-chapter="(cn) => emit('update:selectedChapterNum', cn)"
      />
    </div>
  </aside>
</template>
