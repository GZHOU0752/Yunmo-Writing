<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useApi, clearToken } from '@/composables/useApi'
import { useNovelStore } from '@/composables/useNovelStore'

const router = useRouter()
const api = useApi()
const novelStore = useNovelStore()
const user = ref({ email: '', displayName: '' })
const editName = ref('')
const editingName = ref(false)

// 修改密码
const pwdModal = ref(false)
const pwdForm = ref({ oldPwd: '', newPwd: '', confirm: '' })

// 统计
const totalWords = ref(0)
const totalChapters = ref(0)
const novelCount = ref(0)

onMounted(async () => {
  try { user.value = await api.auth.me() || {} } catch {}
  try {
    await novelStore.fetchNovels()
    novelCount.value = novelStore.novels.length
    totalWords.value = novelStore.novels.reduce((s, n) => s + (n.wordCount || 0), 0)
    totalChapters.value = novelStore.novels.reduce((s, n) => s + (n.totalChapters || 0), 0)
  } catch {}
})

function startEdit() {
  editName.value = user.value.displayName || ''
  editingName.value = true
}

async function saveName() {
  // 后端暂无昵称更新接口，仅本地显示
  user.value.displayName = editName.value
  editingName.value = false
}

async function changePwd() {
  if (!pwdForm.value.oldPwd || !pwdForm.value.newPwd) return
  if (pwdForm.value.newPwd !== pwdForm.value.confirm) return
  // 后端暂无改密接口，占位
  pwdModal.value = false
}

async function handleLogout() {
  try { await api.auth.logout() } catch {}
  clearToken()
  router.push('/')
}
</script>

<template>
  <div class="max-w-xl mx-auto p-6 pt-10">
    <h1 class="text-xl font-bold mb-6" style="color:var(--yunmo-accent)">个人中心</h1>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-3 gap-3 mb-6">
      <div class="yunmo-card p-4 text-center">
        <div class="text-2xl font-bold font-tabular" style="color:var(--yunmo-accent)">{{ novelCount }}</div>
        <div class="text-xs mt-1 text-caption">部作品</div>
      </div>
      <div class="yunmo-card p-4 text-center">
        <div class="text-2xl font-bold font-tabular" style="color:var(--yunmo-accent)">{{ totalWords.toLocaleString() }}</div>
        <div class="text-xs mt-1 text-caption">总字数</div>
      </div>
      <div class="yunmo-card p-4 text-center">
        <div class="text-2xl font-bold font-tabular" style="color:var(--yunmo-accent)">{{ totalChapters }}</div>
        <div class="text-xs mt-1 text-caption">章节</div>
      </div>
    </div>

    <!-- 基本信息 -->
    <div class="yunmo-card p-5 mb-4">
      <h3 class="text-sm font-semibold mb-3" style="color:var(--yunmo-accent)">基本信息</h3>
      <div class="space-y-3 text-sm">
        <div class="flex items-center justify-between">
          <span class="text-caption">邮箱</span>
          <span>{{ user.email || '-' }}</span>
        </div>
        <div class="flex items-center justify-between">
          <span class="text-caption">昵称</span>
          <template v-if="editingName">
            <div class="flex items-center gap-1">
              <a-input v-model:value="editName" size="small" class="w-28" />
              <a-button size="small" type="primary" @click="saveName">保存</a-button>
              <a-button size="small" @click="editingName = false">取消</a-button>
            </div>
          </template>
          <span v-else class="cursor-pointer hover:text-[var(--yunmo-accent)]" @click="startEdit">{{ user.displayName || '未设置' }}</span>
        </div>
      </div>
    </div>

    <!-- 账号安全 -->
    <div class="yunmo-card p-5 mb-4">
      <h3 class="text-sm font-semibold mb-3" style="color:var(--yunmo-accent)">账号安全</h3>
      <button class="yunmo-btn text-sm" @click="pwdModal = true">修改密码</button>
    </div>

    <!-- 危险操作 -->
    <div class="yunmo-card p-5 mb-4" style="border-color:var(--yunmo-red)">
      <h3 class="text-sm font-semibold mb-1" style="color:var(--yunmo-red)">注销账号</h3>
      <p class="text-xs mb-3">注销后所有作品、章节、设定将永久删除，不可恢复。</p>
      <a-popconfirm
        title="确定注销账号？所有数据将永久删除，不可恢复！"
        ok-text="确认注销"
        cancel-text="取消"
        @confirm="async () => { try { await api.auth.deleteAccount(); clearToken(); router.push('/') } catch {} }"
      >
        <button class="yunmo-btn text-sm">注销账号</button>
      </a-popconfirm>
    </div>

    <!-- 退出 -->
    <div class="text-center mt-8">
      <button class="yunmo-btn text-sm" @click="handleLogout">退出登录</button>
    </div>

    <!-- 修改密码弹窗 -->
    <a-modal v-model:open="pwdModal" title="修改密码" @ok="changePwd" ok-text="确认" cancel-text="取消">
      <a-form layout="vertical" class="mt-4">
        <a-form-item label="原密码">
          <a-input-password v-model:value="pwdForm.oldPwd" />
        </a-form-item>
        <a-form-item label="新密码">
          <a-input-password v-model:value="pwdForm.newPwd" />
        </a-form-item>
        <a-form-item label="确认新密码">
          <a-input-password v-model:value="pwdForm.confirm" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
