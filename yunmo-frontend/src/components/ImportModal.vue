<script setup>
import { ref } from 'vue'
import { useApi } from '@/composables/useApi'
import { message } from 'ant-design-vue'

const props = defineProps({ novelId: String, open: Boolean })
const emit = defineEmits(['update:open', 'imported'])

const api = useApi()
const text = ref('')
const fileName = ref('')
const loading = ref(false)
const previewing = ref(false)
const previews = ref([])

/** 处理文件选择 */
function handleFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (file.size > 10 * 1024 * 1024) { message.warn('文件不能超过 10MB'); return }
  fileName.value = file.name
  const reader = new FileReader()
  reader.onload = () => { text.value = reader.result; preview() }
  reader.readAsText(file, 'UTF-8')
}

/** 预览拆分结果 */
async function preview() {
  if (!text.value.trim()) return
  previewing.value = true
  try { previews.value = await api.import.preview(props.novelId, text.value) }
  catch { previews.value = [] }
  finally { previewing.value = false }
}

/** 确认导入 */
async function doImport() {
  if (!text.value.trim()) return
  loading.value = true
  try {
    const res = await api.import.execute(props.novelId, text.value)
    message.success(`导入完成，共 ${res.imported} 章`)
    emit('imported')
    emit('update:open', false)
    text.value = ''
    fileName.value = ''
    previews.value = []
  } catch (e) {
    message.error('导入失败，请检查文稿格式后重试')
  } finally { loading.value = false }
}
</script>

<template>
  <a-modal :open="open" title="导入外部文稿" @cancel="$emit('update:open', false)" :footer="null" width="650px">
    <!-- 文件上传 -->
    <div class="mb-4">
      <label
        class="block border-2 border-dashed rounded-lg p-8 text-center cursor-pointer hover:border-[var(--yunmo-accent-light)] transition-colors"
        style="border-color:var(--yunmo-border)"
      >
        <div class="text-2xl mb-2">+</div>
        <p class="text-sm" style="color:var(--yunmo-text-secondary)">点击上传 .txt / .md 文稿</p>
        <p class="text-xs text-caption mt-1">自动识别章节标记（第X章 / Chapter X）</p>
        <input type="file" accept=".txt,.md,.text" class="hidden" @change="handleFile" />
      </label>
      <p v-if="fileName" class="text-xs mt-2">{{ fileName }}</p>
    </div>

    <!-- 或粘贴内容 -->
    <a-textarea
      v-model:value="text"
      placeholder="或直接粘贴文稿正文..."
      :rows="6"
      class="mb-3"
    />
    <div class="flex gap-2 mb-4">
      <a-button size="small" @click="preview" :loading="previewing" :disabled="!text.trim()">预览拆分</a-button>
    </div>

    <!-- 拆分预览 -->
    <a-spin :spinning="previewing">
      <div v-if="previews.length > 0" class="max-h-60 overflow-y-auto space-y-2">
        <div
          v-for="(p, i) in previews"
          :key="i"
          class="p-2 text-xs rounded flex items-start gap-2"
          style="background:var(--yunmo-paper-dark)"
        >
          <a-tag color="blue">{{ p.title }}</a-tag>
          <span class="text-caption">{{ p.wordCount?.toLocaleString() }} 字</span>
          <span class="flex-1 truncate" style="color:var(--yunmo-text-secondary)">{{ p.preview }}</span>
        </div>
      </div>
      <div v-if="!previewing && previews.length === 0 && text.trim()" class="text-center text-caption py-4">
        未检测到章节标记，将以单章导入
      </div>
    </a-spin>

    <!-- 操作按钮 -->
    <div class="flex justify-end gap-2 mt-4 pt-3 border-t" style="border-color:var(--yunmo-border)">
      <a-button @click="$emit('update:open', false)">取消</a-button>
      <a-button type="primary" @click="doImport" :loading="loading" :disabled="!text.trim()">
        导入 {{ previews.length > 0 ? previews.length : 1 }} 章
      </a-button>
    </div>
  </a-modal>
</template>
