import { ref, watch } from 'vue'

const theme = ref(localStorage.getItem('yunmo-theme') || 'light')

function apply() {
  document.documentElement.setAttribute('data-theme', theme.value)
  localStorage.setItem('yunmo-theme', theme.value)
}

function toggle() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
}

// 初始化
apply()
watch(theme, apply)

export function useTheme() {
  return { theme, toggle }
}
