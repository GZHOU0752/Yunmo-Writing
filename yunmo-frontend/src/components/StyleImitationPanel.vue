<script setup>
import { ref, computed } from 'vue'
import { useApi } from '@/composables/useApi'

const props = defineProps({
  novelId: String,
  currentStyle: { type: String, default: '' },
})
const emit = defineEmits(['style-updated'])

const api = useApi()
const panelOpen = ref(false)
const referenceText = ref('')
const isAnalyzing = ref(false)
const analysisResult = ref(null)
const saved = ref(false)
const errorMessage = ref('')

const charCount = computed(() => referenceText.value.length)
const maxChars = 50000
const isOverLimit = computed(() => charCount.value > maxChars)

/** 调用后端 AI 进行文风分析 */
async function analyzeStyle() {
  if (!referenceText.value.trim()) return

  isAnalyzing.value = true
  errorMessage.value = ''
  analysisResult.value = null

  try {
    const result = await api.novels.analyzeStyle(props.novelId, referenceText.value)

    if (result.status === 'error') {
      errorMessage.value = result.message || '分析失败，请重试'
      return
    }

    analysisResult.value = result.analysis

    // 如果用户之前未设置文风，自动应用
    if (!props.currentStyle && analysisResult.value?.style_summary) {
      emit('style-updated', {
        referenceText: referenceText.value,
        analysis: analysisResult.value,
      })
      saved.value = true
    }
  } catch (e) {
    errorMessage.value = e.message || '分析失败，请检查网络连接'
    console.error('文风分析失败:', e)
  } finally {
    isAnalyzing.value = false
  }
}

async function applyStyle() {
  if (!analysisResult.value || isOverLimit.value) return

  emit('style-updated', {
    referenceText: referenceText.value,
    analysis: analysisResult.value,
  })
  saved.value = true
}

function resetPanel() {
  referenceText.value = ''
  analysisResult.value = null
  saved.value = false
  errorMessage.value = ''
}
</script>

<template>
  <div>
    <!-- 入口按钮 -->
    <div class="collapsible-header" @click="panelOpen = !panelOpen">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-text-caption)" stroke-width="2.5"
           :style="{ transform: panelOpen ? 'none' : 'rotate(-90deg)', transition: 'transform 0.2s ease' }">
        <path d="M6 9l6 6 6-6" />
      </svg>
      <span class="text-xs font-semibold tracking-wide" style="color:var(--yunmo-accent)">文风模仿</span>
      <a-tag v-if="currentStyle" color="blue" size="small" class="ml-auto">已设置</a-tag>
    </div>

    <div v-if="panelOpen" class="mt-2">
      <!-- 已设置文风提示 -->
      <div v-if="saved && analysisResult" class="mb-3 px-3 py-2 rounded text-xs"
           style="background:rgba(90,122,90,0.08);border:1px solid rgba(90,122,90,0.2);color:var(--yunmo-green)">
        <div class="flex items-center gap-1 mb-1">
          <span>✓</span>
          <span class="font-semibold">文风已学习</span>
        </div>
        <div class="flex flex-wrap gap-1 mt-1">
          <a-tag v-for="tag in analysisResult.tags" :key="tag" size="small">{{ tag }}</a-tag>
        </div>
        <p class="mt-1" style="color:var(--yunmo-text-caption)">{{ analysisResult.style_summary }}</p>
        <button class="text-xs mt-2 underline" style="color:var(--yunmo-accent)" @click="resetPanel">重新设置</button>
      </div>

      <!-- 上传/粘贴区域 -->
      <div v-else>
        <p class="text-xs mb-2" style="color:var(--yunmo-text-caption)">
          粘贴参考文本，AI 将深度分析其文风特征用于后续写作
        </p>

        <a-textarea
          v-model:value="referenceText"
          placeholder="在此粘贴一段参考文本（如你喜欢的小说片段）……&#10;&#10;建议 500-3000 字，效果最佳。"
          :rows="6"
          class="w-full"
          :class="{ 'border-red-400': isOverLimit }"
          @input="saved = false; analysisResult = null; errorMessage = ''"
        />

        <div class="flex items-center justify-between mt-1 mb-3">
          <span class="text-[10px]" :style="{ color: isOverLimit ? 'var(--yunmo-red)' : 'var(--yunmo-text-caption)' }">
            {{ charCount.toLocaleString() }} / {{ maxChars.toLocaleString() }} 字
          </span>
          <a-button
            size="small"
            type="primary"
            :disabled="!referenceText.trim() || isOverLimit"
            :loading="isAnalyzing"
            @click="analyzeStyle"
          >
            {{ isAnalyzing ? 'AI 分析中...' : 'AI 分析文风' }}
          </a-button>
        </div>

        <!-- 错误提示 -->
        <div v-if="errorMessage" class="mb-3 px-3 py-2 rounded text-xs"
             style="background:rgba(179,68,58,0.06);border:1px solid rgba(179,68,58,0.2);color:var(--yunmo-red)">
          {{ errorMessage }}
        </div>

        <!-- AI 分析结果 -->
        <div v-if="analysisResult" class="yunmo-card-ghost p-3 space-y-2">
          <!-- 风格概述 -->
          <div>
            <span class="text-xs font-semibold" style="color:var(--yunmo-accent)">文风概述</span>
            <p class="text-xs mt-1 leading-relaxed" style="color:var(--yunmo-text-secondary)">
              {{ analysisResult.style_summary }}
            </p>
          </div>

          <!-- 关键指标 -->
          <div class="grid grid-cols-2 gap-2 text-xs">
            <div>
              <span style="color:var(--yunmo-text-caption)">句式特点</span>
              <div class="text-xs font-medium mt-0.5">{{ analysisResult.sentence_pattern || '-' }}</div>
            </div>
            <div>
              <span style="color:var(--yunmo-text-caption)">平均句长</span>
              <div class="text-xs font-medium font-tabular mt-0.5">{{ analysisResult.avg_sentence_length_estimate || '-' }} 字</div>
            </div>
            <div>
              <span style="color:var(--yunmo-text-caption)">对话占比</span>
              <div class="text-xs font-medium font-tabular mt-0.5">{{ analysisResult.dialogue_ratio_estimate || '-' }}%</div>
            </div>
            <div>
              <span style="color:var(--yunmo-text-caption)">语气基调</span>
              <div class="text-xs font-medium mt-0.5">{{ analysisResult.tone || '-' }}</div>
            </div>
          </div>

          <!-- 节奏 -->
          <div v-if="analysisResult.rhythm">
            <span class="text-xs" style="color:var(--yunmo-text-caption)">节奏</span>
            <div class="text-xs font-medium mt-0.5">{{ analysisResult.rhythm }}</div>
          </div>

          <!-- 词汇特点 -->
          <div v-if="analysisResult.vocabulary_features?.length">
            <span class="text-xs" style="color:var(--yunmo-text-caption)">词汇特点</span>
            <div class="flex flex-wrap gap-1 mt-1">
              <span
                v-for="(feat, i) in analysisResult.vocabulary_features"
                :key="i"
                class="text-[10px] px-2 py-0.5 rounded-full"
                style="background:var(--yunmo-paper-dark);color:var(--yunmo-text-secondary)"
              >{{ feat }}</span>
            </div>
          </div>

          <!-- 写作技法 -->
          <div v-if="analysisResult.key_techniques?.length">
            <span class="text-xs" style="color:var(--yunmo-text-caption)">写作技法</span>
            <div class="flex flex-wrap gap-1 mt-1">
              <a-tag v-for="(tech, i) in analysisResult.key_techniques" :key="i" size="small" color="processing">{{ tech }}</a-tag>
            </div>
          </div>

          <!-- 适合类型 -->
          <div v-if="analysisResult.suitable_genres?.length">
            <span class="text-xs" style="color:var(--yunmo-text-caption)">适合类型</span>
            <div class="flex flex-wrap gap-1 mt-1">
              <a-tag v-for="(genre, i) in analysisResult.suitable_genres" :key="i" size="small">{{ genre }}</a-tag>
            </div>
          </div>

          <!-- 文风标签 -->
          <div>
            <span class="text-xs" style="color:var(--yunmo-text-caption)">文风标签</span>
            <div class="flex flex-wrap gap-1 mt-1">
              <a-tag v-for="tag in analysisResult.tags" :key="tag" size="small" color="blue">{{ tag }}</a-tag>
            </div>
          </div>

          <!-- 代表句 -->
          <div v-if="analysisResult.sample_sentence" class="px-3 py-2 rounded text-xs italic"
               style="background:var(--yunmo-paper);border:1px solid var(--yunmo-border);color:var(--yunmo-text-secondary)">
            "{{ analysisResult.sample_sentence }}"
          </div>

          <a-button
            type="primary"
            size="small"
            class="w-full mt-2"
            @click="applyStyle"
          >
            应用此文风
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>
