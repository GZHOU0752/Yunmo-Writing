<script setup>
import { ref, watch, computed } from 'vue'

const props = defineProps({
  open: Boolean,
  node: Object,
  novelId: String,
  chapterNumber: { type: Number, default: null },
})
const emit = defineEmits(['update:open', 'save'])

const title = ref('')
const outlineContent = ref('')
const saving = ref(false)

const levelMeta = {
  0: { name: '全书大纲', hint: '这个故事从头到尾的大致走向。可以写得很随意——想到什么写什么，后续随时改。',
    placeholder: '开头：主角是青云城一个普通的杂役弟子，被测出废灵根后被所有人看不起...\n\n发展：一次意外中获得了神秘功法，暗中修炼。宗门大比上一鸣惊人，却也引来了杀身之祸...\n\n高潮：发现自己的身世秘密，与幕后黑手正面对决。在绝境中突破，以废灵根证道...\n\n结尾：成为一方强者，但选择归隐。最后一场戏是他在当年测试灵根的广场上，看着一个新入门的少年...' },
  1: { name: '分卷大纲', hint: '这一卷要讲什么。一个完整的起承转合。',
    placeholder: '本卷核心冲突是什么？主角在这一卷结束时会有什么变化？\n\n例如：主角从杂役弟子成长为内门弟子，期间经历了宗门试炼、结识了重要伙伴、得罪了大长老的孙子。' },
  2: { name: '章纲', hint: '这一章具体写什么。AI 会参照这个来写正文。',
    placeholder: '1. 开场：主角在灵根测试广场排队\n2. 冲突：被测出废灵根，周围人嘲笑\n3. 转折：神秘老者传音入密，告诉主角"你的灵根不是废的，是被封印了"\n4. 结尾：主角在深夜按照老者指引，第一次感受到体内沉睡的力量' },
}

const currentLevel = computed(() => {
  const lv = props.node?.level ?? 2
  return levelMeta[lv] || levelMeta[2]
})

watch(() => props.node, (n) => {
  if (n) {
    title.value = n.title || ''
    outlineContent.value = n.outlineContent || ''
  } else {
    title.value = ''
    outlineContent.value = ''
  }
}, { immediate: true })

async function handleSave() {
  if (!outlineContent.value.trim()) return
  saving.value = true
  try {
    emit('save', {
      title: title.value.trim() || outlineContent.value.trim().substring(0, 30),
      outlineContent: outlineContent.value.trim(),
      chapterNumber: props.chapterNumber,
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
    :title="'编辑' + currentLevel.name"
    @ok="handleSave"
    @cancel="$emit('update:open', false)"
    ok-text="保存"
    cancel-text="取消"
    :confirm-loading="saving"
    width="560px"
  >
    <div class="mb-3 text-xs px-2 py-1.5 rounded leading-relaxed"
      style="background:var(--yunmo-paper-dark);color:var(--yunmo-text-caption)">
      {{ currentLevel.hint }}
    </div>

    <a-form layout="vertical">
      <a-form-item label="标题（可选）">
        <a-input v-model:value="title"
          :placeholder="currentLevel.name === '章纲' ? '如：灵根觉醒 · 震惊全场' : '给这段大纲起个名字'" />
      </a-form-item>

      <a-form-item label="大纲内容">
        <a-textarea v-model:value="outlineContent"
          :placeholder="currentLevel.placeholder"
          :rows="10"
          :auto-size="{ minRows: 6, maxRows: 16 }"
          class="font-sans" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
