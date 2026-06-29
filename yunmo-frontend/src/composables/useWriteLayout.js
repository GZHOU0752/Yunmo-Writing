import { ref } from 'vue'

/**
 * 写作工作区布局状态管理
 * 控制侧边栏折叠、专注模式、面板展开等
 */
export function useWriteLayout() {
  // 折叠 & 专注模式
  const leftCollapsed = ref(false)
  const rightCollapsed = ref(false)
  const focusMode = ref(false)
  const statsBarOpen = ref(true)
  const mobilePanel = ref('none') // 'none' | 'chapters' | 'tools'

  // 全屏面板状态（大纲/角色可展开为全屏，也可收回到侧栏）
  const outlineExpanded = ref(false)
  const charactersExpanded = ref(false)

  // 右侧面板各区块展开状态
  const rightPanelSections = ref({
    ai: true,
    deai: true,
    discuss: false,
    references: true,
    characters: true,
    marathon: false,
  })

  /** 切换专注模式 */
  function toggleFocusMode() {
    focusMode.value = !focusMode.value
    leftCollapsed.value = focusMode.value
    rightCollapsed.value = focusMode.value
  }

  /** 展开左栏 */
  function expandLeft() {
    leftCollapsed.value = false
  }

  /** 折叠左栏 */
  function collapseLeft() {
    leftCollapsed.value = true
  }

  /** 展开右栏 */
  function expandRight() {
    rightCollapsed.value = false
  }

  /** 折叠右栏 */
  function collapseRight() {
    rightCollapsed.value = true
  }

  /** 切换大纲全屏展开 */
  function toggleOutlineExpand() {
    outlineExpanded.value = !outlineExpanded.value
    if (outlineExpanded.value) {
      // 展开大纲时，确保左栏可见（大纲在左栏）
      leftCollapsed.value = false
    }
  }

  /** 切换角色全屏展开 */
  function toggleCharactersExpand() {
    charactersExpanded.value = !charactersExpanded.value
    if (charactersExpanded.value) {
      // 展开角色时，确保右栏可见（角色在右栏）
      rightCollapsed.value = false
    }
  }

  return {
    leftCollapsed,
    rightCollapsed,
    focusMode,
    statsBarOpen,
    mobilePanel,
    rightPanelSections,
    outlineExpanded,
    charactersExpanded,
    toggleFocusMode,
    expandLeft,
    collapseLeft,
    expandRight,
    collapseRight,
    toggleOutlineExpand,
    toggleCharactersExpand,
  }
}
