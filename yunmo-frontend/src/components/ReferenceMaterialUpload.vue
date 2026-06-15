<script setup>
import { ref } from 'vue'
import { useApi } from '@/composables/useApi'

const props = defineProps({
  novelId: String,
})
const emit = defineEmits(['uploaded'])

const api = useApi()
const uploading = ref(false)
const errorMsg = ref('')

/** 处理文件选择 */
async function handleFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  errorMsg.value = ''

  // 校验文件类型和大小
  if (!file.name.endsWith('.txt')) {
    errorMsg.value = '仅支持 .txt 文本文件'
    return
  }
  if (file.size > 100 * 1024 * 1024) {
    errorMsg.value = '文件不能超过 100MB'
    return
  }

  uploading.value = true
  try {
    const content = await file.text()
    if (content.trim().length < 100) {
      errorMsg.value = '文件内容太少（至少 100 字符）'
      uploading.value = false
      return
    }
    await api.references.upload(props.novelId, file.name, content)
    emit('uploaded')
  } catch (e) {
    errorMsg.value = '上传失败：' + (e.message || '未知错误')
  } finally {
    uploading.value = false
    // 清空 input 以便重新选择同一文件
    e.target.value = ''
  }
}
</script>

<template>
  <div class="reference-upload">
    <label
      class="block border-2 border-dashed border-[var(--yunmo-border)] rounded-lg p-6 text-center cursor-pointer hover:border-[var(--yunmo-accent-light)] transition-colors"
      :class="{ 'opacity-50 pointer-events-none': uploading }"
    >
      <a-spin v-if="uploading" size="small" class="mb-2 block" />
      <div v-else class="text-lg mb-1 font-brush" style="color:var(--yunmo-accent)">+</div>
      <p class="text-sm text-[var(--yunmo-text-secondary)]">
        {{ uploading ? '正在解析并索引素材...' : '点击上传 .txt 参考素材' }}
      </p>
      <p class="text-xs text-caption mt-1">支持 100MB 以内的文本文件</p>
      <input
        type="file"
        accept=".txt"
        class="hidden"
        @change="handleFile"
        :disabled="uploading"
      />
    </label>
    <p v-if="errorMsg" class="text-xs mt-2" style="color:var(--yunmo-red)">{{ errorMsg }}</p>
  </div>
</template>
