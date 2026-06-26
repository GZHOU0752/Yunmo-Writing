import { onMounted, onBeforeUnmount } from 'vue'

/**
 * 集中式快捷键管理 composable
 *
 * 快捷键列表：
 *   Ctrl+S      保存当前章节
 *   Alt+←/→    上/下一章
 */
export function useKeyboardShortcuts(options = {}) {
  const {
    onSave,
    onPrevChapter,
    onNextChapter,
  } = options

  /** 判断是否在输入框/编辑器中 */
  function isTyping() {
    const tag = document.activeElement?.tagName
    return tag === 'INPUT' || tag === 'TEXTAREA' ||
      document.activeElement?.isContentEditable ||
      document.activeElement?.closest('.ProseMirror')
  }

  function handleKeydown(e) {
    // Ctrl+S 保存
    if ((e.ctrlKey || e.metaKey) && e.code === 'KeyS') {
      e.preventDefault()
      onSave?.()
      return
    }

    // 输入时不触发导航快捷键
    if (isTyping()) {
      return
    }

    // Alt+← 上一章
    if (e.altKey && e.code === 'ArrowLeft') {
      e.preventDefault()
      onPrevChapter?.()
      return
    }

    // Alt+→ 下一章
    if (e.altKey && e.code === 'ArrowRight') {
      e.preventDefault()
      onNextChapter?.()
      return
    }
  }

  onMounted(() => {
    document.addEventListener('keydown', handleKeydown)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('keydown', handleKeydown)
  })
}
