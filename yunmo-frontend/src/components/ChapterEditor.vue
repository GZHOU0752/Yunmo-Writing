<script setup>
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import Underline from '@tiptap/extension-underline'
import { watch, onBeforeUnmount } from 'vue'

const props = defineProps({ content: String })
const emit = defineEmits(['update:content'])

const editor = useEditor({
  content: plainTextToHTML(props.content),
  extensions: [
    StarterKit.configure({
      codeBlock: false,
      code: false,
      blockquote: false,
    }),
    Underline,
    Placeholder.configure({ placeholder: '在此书写正文，段间以空行分隔...' }),
  ],
  onUpdate: ({ editor }) => {
    emit('update:content', editor.getHTML())
  },
  editorProps: {
    attributes: {
      class: 'prose max-w-none focus:outline-none min-h-[60vh]',
    },
  },
})

/** 将纯文本转为 HTML 段落，\n\n 分隔的文本块各成一个带首行缩进的 <p> */
function plainTextToHTML(text) {
  if (!text) return ''
  // 如果已经是 HTML 则直接返回
  if (/<[a-zA-Z][^>]*>/.test(text)) return text
  return text
    .split(/\n\n+/)
    .map(p => p.trim())
    .filter(Boolean)
    .map(p => `<p style="text-indent:2em;line-height:1.85">${p.replace(/\n/g, '<br>')}</p>`)
    .join('')
}

watch(() => props.content, (newContent) => {
  if (editor.value && newContent !== editor.value.getHTML()) {
    editor.value.commands.setContent(plainTextToHTML(newContent), false)
  }
})

onBeforeUnmount(() => {
  editor.value?.destroy()
})
</script>

<template>
  <div class="max-w-[72ch] mx-auto w-full">
    <!-- 编辑区 — 纯书写，无干扰 -->
    <EditorContent :editor="editor" class="min-h-[60vh]" />
  </div>
</template>

<style scoped>
/* 书写纸感 — 非 scoped 部分在下方，因为 ProseMirror 动态 DOM 不受 scoped 约束 */

.ProseMirror:focus {
  outline: none;
}
</style>

<!-- ProseMirror 样式不可 scoped：其内部 DOM 由 ProseMirror 运行时生成，不带 Vue scoped data 属性 -->
<style>
.ProseMirror {
  padding: 2rem 1rem;
  caret-color: var(--yunmo-accent);
  color: var(--yunmo-ink);
  font-family: "Noto Serif SC", "Source Han Serif SC", serif;
  font-size: 1.0625rem;
  line-height: 1.9;
  text-wrap: pretty;
}

.ProseMirror p {
  text-indent: 2em;
  margin-bottom: 0.6em;
}

.ProseMirror h1,
.ProseMirror h2 {
  text-indent: 0;
  font-weight: 600;
  color: var(--yunmo-accent);
}

.ProseMirror p.is-editor-empty:first-child::before {
  color: var(--yunmo-text-caption);
  opacity: 0.45;
  content: attr(data-placeholder);
  float: inline-start;
  pointer-events: none;
  height: 0;
  text-indent: 2em;
}

/* 暗色模式下强制编辑器内所有文字使用变量 */
.ProseMirror * {
  color: inherit !important;
  background: transparent !important;
}
</style>
