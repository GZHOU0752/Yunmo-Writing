<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useApi, setToken } from '@/composables/useApi'

const router = useRouter()
const api = useApi()
const email = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleLogin() {
  if (!email.value.trim() || !password.value.trim()) {
    error.value = '请输入邮箱和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await api.auth.login(email.value.trim(), password.value)
    setToken(res.token)
    router.push('/dashboard')
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-[100dvh] flex items-center justify-center bg-[var(--yunmo-paper-dark)]">
    <div class="yunmo-card p-10 w-full max-w-sm">
      <h1 class="text-2xl font-bold text-center mb-6" style="color:var(--yunmo-accent)">云 墨</h1>

      <div class="flex flex-col gap-4">
        <div>
          <label class="text-caption block mb-1">邮箱</label>
          <a-input v-model:value="email" placeholder="请输入邮箱" size="large"
                   @keyup.enter="handleLogin" />
        </div>
        <div>
          <label class="text-caption block mb-1">密码</label>
          <a-input-password v-model:value="password" placeholder="输入密码" size="large"
                            @keyup.enter="handleLogin" />
        </div>

        <div v-if="error" class="text-sm text-center" style="color:var(--yunmo-red)">{{ error }}</div>

        <a-button type="primary" size="large" block :loading="loading" @click="handleLogin">
          登录
        </a-button>

        <div class="text-center text-caption">
          还没有账号？<a-button type="link" size="small" @click="router.push('/register')">注册</a-button>
        </div>
      </div>
    </div>
  </div>
</template>
