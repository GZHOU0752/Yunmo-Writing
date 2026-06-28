<script setup>
import { ref, watch, computed } from 'vue'
import { useApi } from '@/composables/useApi'

const props = defineProps({
  novelId: String,
  chapters: Array,
  activeChapter: Number,
})
const emit = defineEmits(['selectChapter'])

const api = useApi()
const outline = ref('')
const synopsisSaving = ref(false)
const editingPlanFor = ref(null)  // 正在编辑章纲的章节号
const editPlanText = ref('')
const planSaving = ref(false)
const generatingOutline = ref(false)

const chaptersWithPlans = computed(() => {
  return (props.chapters || []).filter(c => c.writingPlan)
})

async function loadOutline() {
  if (!props.novelId) return
  try {
    const novel = await api.novels.get(props.novelId)
    outline.value = novel.outline || ''
  } catch {}
}

async function saveOutline() {
  synopsisSaving.value = true
  try {
    await api.novels.update(props.novelId, { outline: outline.value })
  } catch { /* ignore */ }
  finally { synopsisSaving.value = false }
}

let synopsisTimer = null
function onOutlineInput() {
  if (synopsisTimer) clearTimeout(synopsisTimer)
  synopsisTimer = setTimeout(saveOutline, 1500)
}

function startEditPlan(ch) {
  editingPlanFor.value = ch.chapterNumber
  editPlanText.value = ch.writingPlan || ''
}

async function savePlan() {
  if (editingPlanFor.value == null) return
  planSaving.value = true
  try {
    await api.chapters.update(props.novelId, editingPlanFor.value,
      { writing_plan: editPlanText.value })
    // 更新本地列表
    const target = (props.chapters || []).find(c => c.chapterNumber === editingPlanFor.value)
    if (target) target.writingPlan = editPlanText.value
  } catch {}
  finally {
    planSaving.value = false
    editingPlanFor.value = null
  }
}

function cancelEditPlan() {
  editingPlanFor.value = null
}

async function generateOutline() {
  if (!props.novelId) return
  generatingOutline.value = true
  try {
    const result = await api.novels.generateOutline(props.novelId)
    if (result?.outline) {
      outline.value = result.outline
      await saveOutline()
    }
  } catch (e) {
    console.error('生成大纲失败:', e)
  } finally {
    generatingOutline.value = false
  }
}

watch(() => props.novelId, loadOutline, { immediate: true })
</script>

<template>
  <div class="p-3 flex flex-col h-full">
    <!-- 全书大纲 -->
    <div class="mb-4">
      <div class="flex items-center justify-between mb-1">
        <span class="text-xs font-semibold" style="color:var(--yunmo-accent)">全书大纲</span>
        <div class="flex items-center gap-1">
          <span v-if="synopsisSaving" class="text-[10px]">保存中...</span>
          <a-button
            v-if="!outline"
            size="small"
            type="link"
            :loading="generatingOutline"
            class="!text-[10px] !px-1"
            @click="generateOutline"
          >
            AI 生成
          </a-button>
        </div>
      </div>
      <a-textarea
        v-model:value="outline"
        :rows="12"
        :auto-size="{ minRows: 8, maxRows: 24 }"
        class="text-sm leading-relaxed"
        placeholder="点击「AI 生成」自动列一份大纲，或者自己照这个格式写：

第一卷：初入宗门（预计30章，6万字）
- 主角穿越到废柴身上，被测出废灵根，被所有人嘲笑
- 只有小师妹对他好，夜里偷偷给他送药
- 在后山捡到一枚神秘戒指 → 金手指激活
- 戒指里有上古传承，开始暗中修炼
- 宗门大比，所有人都以为他是炮灰
- 一拳秒杀天才师兄，全场震惊 → 第一个大爽点
- 长老开始调查他的秘密，引出第一卷高潮
- 卷末状态：从废柴到内门第一，获得进入秘境的资格

第二卷：秘境试炼（预计40章，8万字）
- ...

爽点分布：第8章打脸、第15章获得机缘、第30章曝光实力
关键角色：小师妹（温柔忠犬，感情线担当）/ 师尊（护短老头，靠山）
结局落点：燃向，主角成为一方强者但选择归隐"
        @input="onOutlineInput"
      />
    </div>

    <!-- 章纲列表 -->
    <div class="flex-1 flex flex-col min-h-0">
      <span class="text-xs font-semibold mb-2" style="color:var(--yunmo-accent)">
        章纲
        <span class="text-caption font-normal ml-1">{{ chaptersWithPlans.length }} / {{ (chapters || []).length }} 章</span>
      </span>

      <div class="flex-1 overflow-y-auto space-y-1.5">
        <div
          v-for="ch in (chapters || [])"
          :key="ch.chapterNumber"
          class="rounded transition-fast text-xs"
        >
          <!-- 章纲标题行 -->
          <div
            class="px-2 py-1.5 rounded cursor-pointer flex items-center gap-1"
            :class="ch.chapterNumber === activeChapter
              ? 'bg-[var(--yunmo-accent)] text-[var(--yunmo-paper-light)]'
              : 'hover:bg-[var(--yunmo-paper-dark)]'"
            :style="ch.chapterNumber !== activeChapter ? { color: 'var(--yunmo-text-secondary)' } : {}"
            @click="emit('selectChapter', ch.chapterNumber)"
          >
            <span class="font-semibold shrink-0">第{{ ch.chapterNumber }}章</span>
            <span class="truncate flex-1">{{ ch.title || '' }}</span>
            <span
              v-if="ch.writingPlan"
              class="w-1.5 h-1.5 rounded-full shrink-0"
              :class="ch.chapterNumber === activeChapter ? 'bg-white' : ''"
              :style="ch.chapterNumber !== activeChapter ? { background: 'var(--yunmo-accent)' } : {}"
            />
          </div>

          <!-- 章纲编辑区 -->
          <div v-if="editingPlanFor === ch.chapterNumber" class="px-2 pb-2">
            <a-textarea
              v-model:value="editPlanText"
              :rows="4"
              class="text-xs mb-1"
              placeholder="这一章要写什么？例如：主角在灵根测试中被测出废灵根，遭到众人嘲笑。深夜，神秘老者找到他..."
            />
            <div class="flex gap-1">
              <a-button size="small" type="primary" :loading="planSaving" @click="savePlan">保存</a-button>
              <a-button size="small" @click="cancelEditPlan">取消</a-button>
            </div>
          </div>

          <!-- 章纲预览 -->
          <div
            v-else-if="ch.writingPlan"
            class="px-2 pb-1.5 text-[11px] leading-snug cursor-pointer"
           
            @click="startEditPlan(ch)"
          >
            {{ ch.writingPlan.substring(0, 100) }}{{ ch.writingPlan.length > 100 ? '…' : '' }}
          </div>

          <!-- 无章纲时点击添加 -->
          <div
            v-else
            class="px-2 pb-1 text-[11px] cursor-pointer hover:text-[var(--yunmo-accent)]"
            style="color:var(--yunmo-text-caption);opacity:0.4"
            @click="startEditPlan(ch)"
          >
            点击添加章纲...
          </div>
        </div>

        <div v-if="(chapters || []).length === 0" class="text-xs py-4 text-center">
          还没有章节
        </div>
      </div>
    </div>
  </div>
</template>
