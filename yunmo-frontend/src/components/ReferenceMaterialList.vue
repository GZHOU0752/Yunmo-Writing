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
const expanded = ref(false)

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
</script>

<template>
  <div class="reference-materials">
    <div class="flex items-center justify-between mb-2">
      <div
        class="flex items-center gap-1 cursor-pointer"
        @click="expanded = !expanded"
      >
        <span class="text-xs font-semibold" style="color:var(--yunmo-accent)">参考素材</span>
        <span class="text-xs text-caption">{{ materials.length }} 个</span>
        <span class="text-[10px] text-caption">{{ expanded ? '▾' : '▸' }}</span>
      </div>
    </div>

    <div v-if="expanded" class="space-y-2">
      <!-- 上传区 -->
      <ReferenceMaterialUpload :novel-id="novelId" @uploaded="fetchMaterials" />

      <!-- 素材列表 -->
      <a-spin :spinning="loading" size="small">
        <div v-if="materials.length > 0" class="space-y-1 mt-2">
          <div
            v-for="m in materials"
            :key="m.id"
            class="flex items-center gap-2 text-xs p-2 rounded hover:bg-[var(--yunmo-paper-dark)] group"
          >
            <span class="truncate flex-1 text-[var(--yunmo-text-secondary)]">{{ m.fileName }}</span>
            <span class="text-caption">{{ formatSize(m.fileSize) }}</span>
            <a-tag :color="statusLabel(m.status).color" size="small">{{ statusLabel(m.status).text }}</a-tag>
            <a-popconfirm
              title="确定删除该素材？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="deleteMaterial(m.id)"
            >
              <span class="text-xs cursor-pointer opacity-0 group-hover:opacity-100" style="color:var(--yunmo-red)">✕</span>
            </a-popconfirm>
          </div>
        </div>
        <div v-if="!loading && materials.length === 0" class="text-xs text-caption py-2">
          上传 txt 文件作为 AI 写作的风格参考
        </div>
      </a-spin>
    </div>
  </div>
</template>
