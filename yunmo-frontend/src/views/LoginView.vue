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
    error.value = e.message || '邮箱或密码错误，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-[100dvh] flex items-center justify-center" style="background:linear-gradient(180deg, var(--yunmo-paper) 0%, var(--yunmo-paper-dark) 100%)">
    <div class="yunmo-card p-10 w-full max-w-sm" style="box-shadow:0 8px 32px rgba(31,22,12,0.08),0 2px 8px rgba(0,0,0,0.03)">
      <div class="text-center mb-8">
        <div class="font-brush text-4xl tracking-widest mb-2" style="color:var(--yunmo-accent)">云墨</div>
        <p class="text-xs">古典美学 · AI 写作伴侣</p>
      </div>

      <div class="flex flex-col gap-4">
        <div>
          <label class="text-caption block mb-1.5 text-xs tracking-wide">邮箱</label>
          <a-input v-model:value="email" placeholder="请输入邮箱" size="large"
                   @keyup.enter="handleLogin" />
        </div>
        <div>
          <label class="text-caption block mb-1.5 text-xs tracking-wide">密码</label>
          <a-input-password v-model:value="password" placeholder="输入密码" size="large"
                            @keyup.enter="handleLogin" />
        </div>

        <div v-if="error" class="text-sm text-center py-2 rounded" style="background:rgba(179,68,58,0.06);color:var(--yunmo-red)">{{ error }}</div>

        <a-button type="primary" size="large" block :loading="loading" @click="handleLogin" class="mt-1">
          登 录
        </a-button>

        <div class="text-center text-caption pt-2">
          还没有账号？<a-button type="link" size="small" @click="router.push('/register')" style="padding:0 4px">注册</a-button>
        </div>
      </div>
    </div>
  </div>
</template>
