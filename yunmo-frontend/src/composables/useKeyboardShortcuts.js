import { onMounted, onBeforeUnmount, ref } from 'vue'

/**
 * 集中式快捷键管理 composable（注册表模式）
 *
 * 快捷键列表：
 *   Ctrl+S        保存当前章节
 *   Alt+←/→       上/下一章
 *   Ctrl+Enter    触发/取消 AI 生成
 *   Ctrl+K        打开命令面板
 *   Ctrl+Shift+F  全文搜索
 *   Ctrl+B        侧边栏折叠切换
 *   Ctrl+\        聚焦模式切换
 *   F1            快捷键帮助
 */

/** 默认快捷键定义 */
const SHORTCUT_DEFINITIONS = [
  { key: 'ctrl+s', action: 'save', label: '保存当前章节', category: '编辑', ignoreTyping: true },
  { key: 'alt+ArrowLeft', action: 'prevChapter', label: '上一章', category: '导航' },
  { key: 'alt+ArrowRight', action: 'nextChapter', label: '下一章', category: '导航' },
  { key: 'ctrl+Enter', action: 'generate', label: '触发/取消 AI 生成', category: 'AI' },
  { key: 'ctrl+k', action: 'commandPalette', label: '命令面板', category: '通用' },
  { key: 'ctrl+shift+f', action: 'search', label: '全文搜索', category: '编辑' },
  { key: 'ctrl+b', action: 'toggleSidebar', label: '侧边栏折叠', category: '布局' },
  { key: 'ctrl+\\', action: 'focusMode', label: '聚焦模式', category: '布局' },
  { key: 'F1', action: 'showHelp', label: '快捷键帮助', category: '通用', ignoreTyping: true },
]

/**
 * 使用键盘快捷键
 * @param {Object} handlers - 以 action 名为键的回调函数映射
 * @param {Object} handlers.onSave - 保存
 * @param {Object} handlers.onPrevChapter - 上一章
 * @param {Object} handlers.onNextChapter - 下一章
 * @param {Object} handlers.onGenerate - 触发/取消生成
 * @param {Object} handlers.onSearch - 全文搜索
 * @param {Object} handlers.onToggleSidebar - 切换侧边栏
 * @param {Object} handlers.onFocusMode - 聚焦模式
 * @param {Object} handlers.onShowHelp - 显示帮助
 */
export function useKeyboardShortcuts(handlers = {}) {
  const showHelp = ref(false)

  /** 判断是否在输入框/编辑器中 */
  function isTyping() {
    const tag = document.activeElement?.tagName
    return tag === 'INPUT' || tag === 'TEXTAREA' ||
      document.activeElement?.isContentEditable ||
      document.activeElement?.closest('.ProseMirror')
  }

  /** 解析按键事件为标准化字符串 */
  function parseKey(e) {
    const parts = []
    if (e.ctrlKey || e.metaKey) parts.push('ctrl')
    if (e.altKey) parts.push('alt')
    if (e.shiftKey) parts.push('shift')
    parts.push(e.key === ' ' ? 'Space' : e.key)
    return parts.join('+')
  }

  /** 匹配并执行快捷键 */
  function handleKeydown(e) {
    const combo = parseKey(e)

    for (const def of SHORTCUT_DEFINITIONS) {
      if (combo === def.key || combo.toLowerCase() === def.key.toLowerCase()) {
        // 输入中的非豁免快捷键不触发
        if (!def.ignoreTyping && isTyping()) continue

        e.preventDefault()

        // 执行对应的 handler
        const handlerMap = {
          save: handlers.onSave,
          prevChapter: handlers.onPrevChapter,
          nextChapter: handlers.onNextChapter,
          generate: handlers.onGenerate,
          search: handlers.onSearch,
          toggleSidebar: handlers.onToggleSidebar,
          focusMode: handlers.onFocusMode,
          showHelp: handlers.onShowHelp || (() => { showHelp.value = !showHelp.value }),
          commandPalette: handlers.onCommandPalette,
        }

        handlerMap[def.action]?.()
        return
      }
    }
  }

  onMounted(() => {
    document.addEventListener('keydown', handleKeydown)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('keydown', handleKeydown)
  })

  /** 获取所有快捷键定义（用于帮助面板） */
  function getAllShortcuts() {
    return SHORTCUT_DEFINITIONS.map(def => ({
      key: def.key,
      label: def.label,
      category: def.category,
      displayKey: formatKeyDisplay(def.key),
    }))
  }

  return {
    showHelp,
    getAllShortcuts,
  }
}

/**
 * 格式化快捷键为可读显示文本
 * @param {string} key - 标准化快捷键字符串 (如 "ctrl+s")
 * @returns {string} 可读文本 (如 "Ctrl + S")
 */
export function formatKeyDisplay(key) {
  const isMac = typeof navigator !== 'undefined' && /Mac/.test(navigator.platform)

  return key
    .split('+')
    .map(part => {
      switch (part) {
        case 'ctrl': return isMac ? '⌘' : 'Ctrl'
        case 'alt': return isMac ? '⌥' : 'Alt'
        case 'shift': return isMac ? '⇧' : 'Shift'
        case 'ArrowLeft': return '←'
        case 'ArrowRight': return '→'
        case 'ArrowUp': return '↑'
        case 'ArrowDown': return '↓'
        case 'Enter': return '↵'
        case ' ': return 'Space'
        case '\\': return '\\'
        default: return part.length === 1 ? part.toUpperCase() : part
      }
    })
    .join(isMac ? '' : ' + ')
}
