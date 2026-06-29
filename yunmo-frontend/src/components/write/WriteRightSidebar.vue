<script setup>
/**
 * 右侧边栏 — AI 生成面板 / 检测 / 聊天 / 参考素材 / 角色 / 马拉松
 * 角色支持展开为全屏面板
 */
import { computed, ref } from 'vue'
import { useWriteStore } from '@/composables/useWriteStore'
import GeneratePanel from '@/components/GeneratePanel.vue'
import DeAIScoreBadge from '@/components/DeAIScoreBadge.vue'
import AIChatPanel from '@/components/AIChatPanel.vue'
import ReferenceMaterialList from '@/components/ReferenceMaterialList.vue'
import MarathonControlPanel from '@/components/MarathonControlPanel.vue'

const props = defineProps({
  novelId: { type: String, required: true },
  selectedChapterNum: { type: Number, required: true },
  characters: { type: Array, default: () => [] },
  currentWritingStyle: { type: String, default: '' },
  rightPanelSections: { type: Object, required: true },
  charactersExpanded: { type: Boolean, default: false },
})

const emit = defineEmits([
  'generate',
  'clear-checkpoint',
  'chat-insert',
  'open-discussion',
  'open-character-card',
  'toggle-characters-expand',
])

const store = useWriteStore()
const refMaterialsRef = ref(null)
const refMaterialsCount = computed(() => refMaterialsRef.value?.materials?.length || 0)

/** 角色首字母头像背景色 */
function roleColor(role) {
  return { PROTAGONIST:'#8b3a3a', ANTAGONIST:'#b3443a', SUPPORTING:'#b8956c', MINOR:'#5a7a5a' }[role] || '#888'
}
</script>

<template>
  <aside class="hidden md:flex w-72 border-l border-[var(--yunmo-border)] bg-[var(--yunmo-paper)] flex-col flex-shrink-0 overflow-hidden transition-all duration-300">
    <!-- 折叠按钮 -->
    <slot name="collapse-btn" />

    <div class="overflow-y-auto flex-1 p-4 flex flex-col gap-4">
      <!-- AI 生成面板 -->
      <div>
        <button class="collapsible-header w-full text-left" @click="rightPanelSections.ai = !rightPanelSections.ai">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
               :style="{ transform: rightPanelSections.ai ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
            <path d="M6 9l6 6 6-6" />
          </svg>
          <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">AI 生成</span>
        </button>
        <div v-if="rightPanelSections.ai">
          <GeneratePanel
            :novel-id="novelId"
            :chapter-number="selectedChapterNum"
            :sse-status="store.sseStatus"
            :streamed-text="store.streamedText"
            :quality-report="store.qualityReport"
            :checkpoint="store.checkpoint"
            :writing-style="currentWritingStyle"
            @generate="emit('generate')"
            @clear-checkpoint="emit('clear-checkpoint')"
          />
        </div>
      </div>

      <!-- AI检测 -->
      <div>
        <button class="collapsible-header w-full text-left" @click="rightPanelSections.deai = !rightPanelSections.deai">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
               :style="{ transform: rightPanelSections.deai ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
            <path d="M6 9l6 6 6-6" />
          </svg>
          <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">AI检测</span>
        </button>
        <div v-if="rightPanelSections.deai" class="mt-2">
          <DeAIScoreBadge :diagnosis="store.antiAIDiagnosis" />
        </div>
      </div>

      <!-- AI 讨论入口 -->
      <button class="seal-btn w-full text-sm tracking-wider" @click="emit('open-discussion')">
        讨论剧情
      </button>

      <!-- AI 写作助手 -->
      <AIChatPanel
        :novel-id="novelId"
        :chapter-content="store.currentChapter?.content || ''"
        :chapter-number="selectedChapterNum"
        @insert-text="(text) => emit('chat-insert', text)"
      />

      <!-- 参考素材 -->
      <div>
        <button class="collapsible-header w-full text-left" @click="rightPanelSections.references = !rightPanelSections.references">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
               :style="{ transform: rightPanelSections.references ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
            <path d="M6 9l6 6 6-6" />
          </svg>
          <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">参考素材</span>
          <span v-if="refMaterialsCount > 0" class="text-[10px] ml-auto">{{ refMaterialsCount }} 个</span>
        </button>
        <div v-if="rightPanelSections.references">
          <ReferenceMaterialList ref="refMaterialsRef" :novel-id="novelId" />
        </div>
      </div>

      <!-- 角色列表 -->
      <div>
        <div class="flex items-center">
          <button class="collapsible-header flex-1 text-left" @click="rightPanelSections.characters = !rightPanelSections.characters">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
                 :style="{ transform: rightPanelSections.characters ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
              <path d="M6 9l6 6 6-6" />
            </svg>
            <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">角 色</span>
            <span class="text-[10px] ml-auto">{{ characters.length }} 位</span>
          </button>
          <button
            class="toolbar-btn !p-1 !rounded mr-1"
            @click="emit('toggle-characters-expand')"
            :title="charactersExpanded ? '收回到侧栏' : '展开为全屏'"
          >
            <svg v-if="!charactersExpanded" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7" />
            </svg>
            <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 14h6v6M20 10h-6V4M14 10l7-7M3 21l7-7" />
            </svg>
          </button>
        </div>
        <div v-if="rightPanelSections.characters">
          <div v-if="characters.length === 0" class="text-caption text-xs py-3 text-center">
            尚无角色，生成章节后自动提取
          </div>
          <div v-else class="space-y-0.5 max-h-48 overflow-y-auto">
            <div
              v-for="c in characters"
              :key="c.id"
              class="flex items-center gap-2 px-2.5 py-1.5 rounded-md cursor-pointer text-xs hover:bg-[var(--yunmo-paper-dark)] transition-fast"
              @click="emit('open-character-card', c.id)"
            >
              <span class="w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold text-white shrink-0"
                :style="{ background: roleColor(c.role) }"
              >{{ c.name?.charAt(0) }}</span>
              <span class="truncate font-medium">{{ c.name }}</span>
              <span v-if="c.lastAppearanceChapter === selectedChapterNum" class="w-2 h-2 rounded-full bg-[var(--yunmo-accent)] shrink-0 ring-1 ring-[var(--yunmo-accent)] ring-opacity-30" title="本章出场" />
            </div>
          </div>
        </div>
      </div>

      <!-- 马拉松创作 -->
      <div>
        <button class="collapsible-header w-full text-left" @click="rightPanelSections.marathon = !rightPanelSections.marathon">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
               :style="{ transform: rightPanelSections.marathon ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
            <path d="M6 9l6 6 6-6" />
          </svg>
          <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">批量生成</span>
        </button>
        <div v-if="rightPanelSections.marathon" class="mt-2">
          <MarathonControlPanel :novel-id="novelId" :current-chapter="selectedChapterNum" />
        </div>
      </div>
    </div>
  </aside>
</template>
