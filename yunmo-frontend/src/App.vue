<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isLoggedIn, clearToken } from '@/composables/useApi'
import { useTheme } from '@/composables/useTheme'

const route = useRoute()
const router = useRouter()
const { theme, toggle: toggleTheme } = useTheme()
const loggedIn = ref(isLoggedIn())

watch(() => route.path, () => {
  loggedIn.value = isLoggedIn()
})

function logout() {
  clearToken()
  router.push('/')
}

// ant-design-vue 主题 token — 从源头把主色设为朱砂深红，
// 防止 CSS-in-JS 运行时注入蓝色 focus 样式覆盖我们的静态 CSS
const antTheme = {
  token: {
    colorPrimary: '#8b3a3a',
    colorPrimaryHover: '#6e2626',
    colorPrimaryActive: '#5a1e1e',
    borderRadius: 4,
  },
}
</script>

<template>
  <a-config-provider :theme="antTheme">
    <!-- 登录后的顶部导航 -->
    <nav
      v-if="loggedIn"
      class="h-11 border-b flex items-center px-4 gap-1"
      style="background:var(--yunmo-paper-light);border-color:var(--yunmo-border)"
    >
      <span class="text-base font-bold tracking-widest mr-4" style="color:var(--yunmo-accent)">云 墨</span>

      <router-link
        to="/dashboard"
        class="px-3 py-1.5 rounded text-xs transition-fast"
        :class="route.path === '/dashboard'
          ? 'text-[var(--yunmo-accent)] font-semibold'
          : 'text-[var(--yunmo-text-caption)] hover:text-[var(--yunmo-text-secondary)]'"
        :style="route.path === '/dashboard' ? { background: 'rgba(139,58,58,0.06)' } : {}"
      >书房</router-link>

      <router-link
        to="/profile"
        class="px-3 py-1.5 rounded text-xs transition-fast"
        :class="route.path === '/profile'
          ? 'text-[var(--yunmo-accent)] font-semibold'
          : 'text-[var(--yunmo-text-caption)] hover:text-[var(--yunmo-text-secondary)]'"
        :style="route.path === '/profile' ? { background: 'rgba(139,58,58,0.06)' } : {}"
      >个人中心</router-link>

      <div class="flex-1" />

      <a-button size="small" type="text" class="toolbar-btn text-xs" @click="toggleTheme" :title="theme === 'dark' ? '日间模式' : '墨夜模式'">
        {{ theme === 'dark' ? '素' : '墨' }}
      </a-button>
      <a-button size="small" type="text" class="toolbar-btn text-xs" @click="logout">登出</a-button>
    </nav>

    <router-view v-slot="{ Component }">
      <Transition name="page-fade" mode="out-in">
        <component :is="Component" />
      </Transition>
    </router-view>
  </a-config-provider>
</template>
