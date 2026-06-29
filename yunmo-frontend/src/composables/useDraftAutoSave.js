import { ref } from 'vue'

/**
 * 草稿自动保存 composable
 * 将草稿保存到 localStorage，防止意外丢失
 */
export function useDraftAutoSave({ store, novelId, selectedChapterNum }) {
  let autoSaveTimer = null
  const AUTO_SAVE_INTERVAL = 30_000 // 30 秒
  const lastAutoSaved = ref('')

  /** 获取当前章节的草稿键 */
  function draftKey() {
    return `yunmo-draft-${novelId}-${selectedChapterNum.value}`
  }

  /** 自动保存草稿到 localStorage */
  function autoSave() {
    const content = store.currentChapter?.content
    if (content) {
      localStorage.setItem(draftKey(), content)
      lastAutoSaved.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    }
  }

  /** 启动自动保存定时器 */
  function startAutoSave() {
    stopAutoSave()
    autoSaveTimer = setInterval(autoSave, AUTO_SAVE_INTERVAL)
  }

  /** 停止自动保存 */
  function stopAutoSave() {
    if (autoSaveTimer) { clearInterval(autoSaveTimer); autoSaveTimer = null }
  }

  /** 检查并恢复本地草稿 */
  function restoreDraft() {
    const saved = localStorage.getItem(draftKey())
    if (saved && store.currentChapter && !store.currentChapter.content) {
      store.currentChapter.content = saved
    }
  }

  /** 清除当前章节草稿 */
  function clearDraft() {
    localStorage.removeItem(draftKey())
  }

  /** 保存指定章节的草稿（章节切换时用） */
  function saveDraftForChapter(chapterNum) {
    const key = `yunmo-draft-${novelId}-${chapterNum}`
    const content = store.currentChapter?.content
    if (content) {
      localStorage.setItem(key, content)
      lastAutoSaved.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    }
  }

  return {
    lastAutoSaved,
    autoSave,
    startAutoSave,
    stopAutoSave,
    restoreDraft,
    clearDraft,
    saveDraftForChapter,
  }
}
