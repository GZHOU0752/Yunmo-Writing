<script setup>
import { ref, watch } from 'vue'
import { useApi } from '@/composables/useApi'
import ReferenceMaterialUpload from './ReferenceMaterialUpload.vue'

const props = defineProps({
  novelId: String,
})
const emit = defineEmits(['selectChapter'])

const api = useApi()
const materials = ref([])
const loading = ref(false)
const expanded = ref(true)
const editingTrigger = ref(null) // 正在编辑触发的素材 id
const triggerForm = ref({ triggerMode: 'MANUAL', triggerKeywords: '', cooldownChapters: 0, priority: 0 })

async function fetchMaterials() {
  if (!props.novelId) return
  loading.value = true
  try {
    materials.value = await api.references.list(props.novelId) || []
  } catch (e) {
    console.error('加载参考素材失败:', e)
  } finally {
    loading.value = false
  }
}

async function deleteMaterial(id) {
  try {
    await api.references.delete(props.novelId, id)
    await fetchMaterials()
  } catch (e) {
    console.error('删除素材失败:', e)
  }
}

function openTriggerEdit(m) {
  triggerForm.value = {
    triggerMode: m.triggerMode || 'MANUAL',
    triggerKeywords: m.triggerKeywords || '',
    cooldownChapters: m.cooldownChapters || 0,
    priority: m.priority || 0,
  }
  editingTrigger.value = m.id
}

async function saveTrigger(materialId) {
  try {
    await api.references.updateTrigger(props.novelId, materialId, triggerForm.value)
    editingTrigger.value = null
    await fetchMaterials()
  } catch (e) {
    console.error('保存触发配置失败:', e)
  }
}

function triggerModeLabel(mode) {
  return { MANUAL: '手动', AUTO: '始终', KEYWORD: '关键词' }[mode] || mode
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function statusLabel(s) {
  if (s === 'ready') return { text: '已索引', color: 'green' }
  if (s === 'indexing') return { text: '索引中', color: 'blue' }
  if (s === 'error') return { text: '失败', color: 'red' }
  return { text: s, color: 'default' }
}

watch(() => props.novelId, () => {
  if (props.novelId) fetchMaterials()
}, { immediate: true })

// 暴露 materials 引用给父组件读取数量
defineExpose({ materials })
</script>

<template>
  <div class="reference-materials">
    <div v-if="expanded" class="space-y-2">
      <!-- 上传区 -->
      <ReferenceMaterialUpload :novel-id="novelId" @uploaded="fetchMaterials" />

      <!-- 素材列表 -->
      <a-spin :spinning="loading" size="small">
        <div v-if="materials.length > 0" class="space-y-1 mt-2">
          <div
            v-for="m in materials"
            :key="m.id"
            class="text-xs p-2 rounded hover:bg-[var(--yunmo-paper-dark)] group"
          >
            <!-- 素材信息行 -->
            <div class="flex items-center gap-2">
              <span class="truncate flex-1 text-[var(--yunmo-text-secondary)]">{{ m.fileName }}</span>
              <span class="text-caption">{{ formatSize(m.fileSize) }}</span>
              <a-tag :color="statusLabel(m.status).color" size="small">{{ statusLabel(m.status).text }}</a-tag>
              <a-tag v-if="m.triggerMode && m.triggerMode !== 'MANUAL'" color="blue" size="small">{{ triggerModeLabel(m.triggerMode) }}</a-tag>
              <span class="text-[10px] cursor-pointer opacity-0 group-hover:opacity-100" style="color:var(--yunmo-accent)" @click="openTriggerEdit(m)">触发</span>
              <a-popconfirm
                title="确定删除该素材？"
                ok-text="删除"
                cancel-text="取消"
                @confirm="deleteMaterial(m.id)"
              >
                <span class="text-xs cursor-pointer opacity-0 group-hover:opacity-100" style="color:var(--yunmo-red)">✕</span>
              </a-popconfirm>
            </div>

            <!-- 触发配置编辑 -->
            <div v-if="editingTrigger === m.id" class="mt-2 p-2 rounded space-y-1" style="background:var(--yunmo-paper-dark)">
              <div class="flex items-center gap-1">
                <span class="text-caption w-10 shrink-0">模式</span>
                <a-select v-model:value="triggerForm.triggerMode" size="small" class="flex-1">
                  <a-select-option value="MANUAL">手动</a-select-option>
                  <a-select-option value="AUTO">始终激活</a-select-option>
                  <a-select-option value="KEYWORD">关键词触发</a-select-option>
                </a-select>
              </div>
              <div v-if="triggerForm.triggerMode === 'KEYWORD'" class="flex items-center gap-1">
                <span class="text-caption w-10 shrink-0">关键词</span>
                <a-input v-model:value="triggerForm.triggerKeywords" size="small" placeholder="逗号分隔, 如: 战斗,修炼" />
              </div>
              <div class="flex items-center gap-1">
                <span class="text-caption w-10 shrink-0">冷却</span>
                <a-input-number v-model:value="triggerForm.cooldownChapters" size="small" :min="0" :max="50" class="w-16" />
                <span class="text-caption">章</span>
                <span class="text-caption w-10 shrink-0 ml-2">优先级</span>
                <a-input-number v-model:value="triggerForm.priority" size="small" :min="0" :max="10" class="w-16" />
              </div>
              <div class="flex gap-1 pt-1">
                <a-button size="small" type="primary" @click="saveTrigger(m.id)">保存</a-button>
                <a-button size="small" @click="editingTrigger = null">取消</a-button>
              </div>
            </div>
          </div>
        </div>
        <div v-if="!loading && materials.length === 0" class="text-xs text-caption py-2">
          上传 txt 文件作为 AI 写作的风格参考
        </div>
      </a-spin>
    </div>
  </div>
</template>
