<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useApi, setToken } from '@/composables/useApi'

const router = useRouter()
const api = useApi()
const email = ref('')
const displayName = ref('')
const password = ref('')
const password2 = ref('')
const error = ref('')
const loading = ref(false)

async function handleRegister() {
  if (!email.value.trim() || !password.value.trim()) {
    error.value = '邮箱和密码不能为空'
    return
  }
  if (password.value !== password2.value) {
    error.value = '两次密码不一致'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await api.auth.register(email.value.trim(), password.value, displayName.value.trim())
    setToken(res.token)
    router.push('/dashboard')
  } catch (e) {
    error.value = '注册失败，请检查信息后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-[100dvh] flex items-center justify-center bg-[var(--yunmo-paper-dark)]">
    <div class="yunmo-card p-10 w-full max-w-sm">
      <h1 class="text-2xl font-bold text-center mb-6" style="color:var(--yunmo-accent)">注册</h1>

      <div class="flex flex-col gap-4">
        <div>
          <label class="text-caption block mb-1">邮箱</label>
          <a-input v-model:value="email" placeholder="请输入邮箱" size="large" />
        </div>
        <div>
          <label class="text-caption block mb-1">显示名称</label>
          <a-input v-model:value="displayName" placeholder="作者名（选填）" size="large" />
        </div>
        <div>
          <label class="text-caption block mb-1">密码</label>
          <a-input-password v-model:value="password" placeholder="至少6位" size="large" />
        </div>
        <div>
          <label class="text-caption block mb-1">确认密码</label>
          <a-input-password v-model:value="password2" placeholder="再次输入" size="large"
                            @keyup.enter="handleRegister" />
        </div>

        <div v-if="error" class="text-sm text-center" style="color:var(--yunmo-red)">{{ error }}</div>

        <a-button type="primary" size="large" block :loading="loading" @click="handleRegister">
          注册并开始写作
        </a-button>

        <div class="text-center text-caption">
          已有账号？<a-button type="link" size="small" @click="router.push('/login')">登录</a-button>
        </div>
      </div>
    </div>
  </div>
</template>
