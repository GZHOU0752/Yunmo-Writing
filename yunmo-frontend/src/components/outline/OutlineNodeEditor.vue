<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  open: Boolean,
  node: Object,
  novelId: String,
})
const emit = defineEmits(['update:open', 'save'])

const title = ref('')
const causalSentence = ref('')
const outlineContent = ref('')
const wordCountTarget = ref(null)
const saving = ref(false)

watch(() => props.node, (n) => {
  if (n) {
    title.value = n.title || ''
    causalSentence.value = n.causalSentence || ''
    outlineContent.value = n.outlineContent || ''
    wordCountTarget.value = n.wordCountTarget || null
  } else {
    // 新建节点时清空表单
    title.value = ''
    causalSentence.value = ''
    outlineContent.value = ''
    wordCountTarget.value = null
  }
}, { immediate: true })

const levelNames = { 0: '总纲', 1: '卷', 2: '章', 3: '节' }
const nodeLevel = () => props.node ? levelNames[props.node.level] || '节点' : '节点'

async function handleSave() {
  if (!title.value.trim()) return
  saving.value = true
  try {
    emit('save', {
      title: title.value.trim(),
      causalSentence: causalSentence.value.trim() || null,
      outlineContent: outlineContent.value.trim() || null,
      wordCountTarget: wordCountTarget.value,
    })
    emit('update:open', false)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <a-modal
    :open="open"
    :title=" node ? '编辑' + nodeLevel() : '新建' + '大纲节点'"
    @ok="handleSave"
    @cancel="$emit('update:open', false)"
    ok-text="保存"
    cancel-text="取消"
    :confirm-loading="saving"
  >
    <a-form layout="vertical">
      <a-form-item label="标题">
        <a-input v-model:value="title" placeholder="输入标题..." />
      </a-form-item>
      <a-form-item label="因果句">
        <a-input v-model:value="causalSentence" placeholder="因为...所以...导致..." />
      </a-form-item>
      <a-form-item label="详细内容">
        <a-textarea v-model:value="outlineContent" placeholder="该节点的详细写作要点..." :rows="4" />
      </a-form-item>
      <a-form-item label="目标字数">
        <a-input-number v-model:value="wordCountTarget" :min="100" :max="50000" placeholder="字数" style="width:100%" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
