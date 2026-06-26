<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useApi } from '@/composables/useApi'

const route = useRoute()
const api = useApi()
const novelId = route.params.id

const stats = ref(null)
const foreshadows = ref([])
const auditHistory = ref([])
const characterGraph = ref({ nodes: [], edges: [] })
const loading = ref(true)
const activeTab = ref('overview')

// 统计卡片数据
const statCards = computed(() => {
  if (!stats.value) return []
  return [
    { label: '总章节', value: stats.value.totalChapters || 0, icon: '📖', color: 'var(--yunmo-accent)' },
    { label: '总字数', value: ((stats.value.totalWords || 0) / 10000).toFixed(1) + '万', icon: '✍️', color: 'var(--yunmo-accent-light)' },
    { label: '活跃伏笔', value: stats.value.activeHooks || 0, icon: '🎯', color: 'var(--yunmo-gold)' },
    { label: '已回收', value: stats.value.resolvedHooks || 0, icon: '✅', color: 'var(--yunmo-green)' },
  ]
})

// 伏笔状态着色
function hookStatusColor(status) {
  return {
    PLANTED: 'var(--yunmo-gold)',
    ACTIVATED: 'var(--yunmo-accent)',
    RESOLVED: 'var(--yunmo-green)',
    EXPIRED: 'var(--yunmo-border)',
  }[status] || 'var(--yunmo-text-caption)'
}

function hookStatusLabel(status) {
  return {
    PLANTED: '已埋', ACTIVATED: '活跃中', RESOLVED: '已回收', EXPIRED: '已过期'
  }[status] || status
}

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const [s, f, a, g] = await Promise.all([
      api.get(`/api/v1/novels/${novelId}/monitor/stats`).catch(() => null),
      api.get(`/api/v1/novels/${novelId}/monitor/foreshadows`).catch(() => []),
      api.get(`/api/v1/novels/${novelId}/monitor/audits`).catch(() => []),
      api.get(`/api/v1/novels/${novelId}/monitor/characters/graph`).catch(() => ({ nodes: [], edges: [] })),
    ])
    stats.value = s
    foreshadows.value = f || []
    auditHistory.value = a || []
    characterGraph.value = g || { nodes: [], edges: [] }
  } catch (e) {
    console.error('Dashboard数据加载失败:', e)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="min-h-[100dvh]" :style="{ background: 'var(--yunmo-paper)' }">
    <div class="max-w-7xl mx-auto px-6 py-8">
      <!-- 页面标题 -->
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-2xl font-bold tracking-tight" style="color: var(--yunmo-ink)">
            {{ stats?.title || '创作监控' }}
          </h1>
          <p class="text-sm mt-1" style="color: var(--yunmo-text-caption)">
            全链路数据可视化管理
          </p>
        </div>
        <router-link
          :to="`/novels/${novelId}/write`"
          class="yunmo-btn text-sm"
        >返回写作</router-link>
      </div>

      <!-- 统计卡片 -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        <div
          v-for="card in statCards" :key="card.label"
          class="yunmo-card p-4 text-center"
        >
          <div class="text-2xl mb-1">{{ card.icon }}</div>
          <div class="text-2xl font-bold font-tabular" :style="{ color: card.color }">
            {{ card.value }}
          </div>
          <div class="text-xs mt-1" style="color: var(--yunmo-text-caption)">
            {{ card.label }}
          </div>
        </div>
      </div>

      <!-- Tab切换 -->
      <div class="flex gap-2 mb-6">
        <button
          v-for="tab in [
            { key: 'overview', label: '章节趋势' },
            { key: 'hooks', label: '伏笔追踪' },
            { key: 'characters', label: '角色图谱' },
          ]"
          :key="tab.key"
          class="filter-tag"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >{{ tab.label }}</button>
      </div>

      <a-spin :spinning="loading">
        <!-- 章节趋势 -->
        <div v-if="activeTab === 'overview'" class="yunmo-card p-5">
          <h3 class="text-lg font-semibold mb-4" style="color: var(--yunmo-ink)">章节字数趋势</h3>
          <div v-if="stats?.chapterTrend?.length" class="space-y-2">
            <div
              v-for="point in stats.chapterTrend" :key="point.chapterNumber"
              class="flex items-center gap-3"
            >
              <span class="text-xs font-tabular w-10" style="color: var(--yunmo-text-caption)">
                第{{ point.chapterNumber }}章
              </span>
              <div class="flex-1 h-5 rounded relative overflow-hidden" style="background: var(--yunmo-paper-dark)">
                <div
                  class="h-full rounded transition-all duration-500"
                  :style="{
                    width: Math.min((point.wordCount || 0) / 5000 * 100, 100) + '%',
                    background: (point.wordCount || 0) >= 3000
                      ? 'var(--yunmo-green)'
                      : (point.wordCount || 0) >= 1500
                        ? 'var(--yunmo-amber)'
                        : 'var(--yunmo-accent-light)',
                  }"
                />
              </div>
              <span class="text-xs font-tabular w-16 text-right" style="color: var(--yunmo-text-caption)">
                {{ (point.wordCount || 0).toLocaleString() }}字
              </span>
            </div>
          </div>
          <div v-else class="empty-state">
            <div class="empty-state-icon">📊</div>
            <p class="empty-state-desc">暂无章节数据</p>
          </div>
        </div>

        <!-- 伏笔追踪 -->
        <div v-if="activeTab === 'hooks'" class="yunmo-card p-5 overflow-x-auto">
          <h3 class="text-lg font-semibold mb-4" style="color: var(--yunmo-ink)">伏笔追踪表</h3>
          <table v-if="foreshadows.length" class="w-full text-sm">
            <thead>
              <tr class="border-b" style="border-color: var(--yunmo-border)">
                <th class="text-left py-2 px-3" style="color: var(--yunmo-text-caption)">编号</th>
                <th class="text-left py-2 px-3" style="color: var(--yunmo-text-caption)">内容</th>
                <th class="text-right py-2 px-3" style="color: var(--yunmo-text-caption)">埋设章</th>
                <th class="text-right py-2 px-3" style="color: var(--yunmo-text-caption)">预计回收</th>
                <th class="text-center py-2 px-3" style="color: var(--yunmo-text-caption)">状态</th>
                <th class="text-center py-2 px-3" style="color: var(--yunmo-text-caption)">重要度</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="hook in foreshadows" :key="hook.hookId"
                class="border-b transition-colors hover:bg-[rgba(139,58,58,0.03)]"
                style="border-color: var(--yunmo-border)"
                :style="{
                  background: hook.status === 'EXPIRED'
                    ? 'rgba(179,68,58,0.06)'
                    : hook.status === 'RESOLVED'
                      ? 'rgba(90,122,90,0.04)'
                      : 'transparent',
                }"
              >
                <td class="py-2 px-3 font-mono text-xs" style="color: var(--yunmo-accent)">{{ hook.hookId }}</td>
                <td class="py-2 px-3 max-w-60 truncate" style="color: var(--yunmo-ink)">{{ hook.content }}</td>
                <td class="py-2 px-3 text-right font-tabular" style="color: var(--yunmo-text-caption)">{{ hook.plantedChapter }}</td>
                <td class="py-2 px-3 text-right font-tabular" style="color: var(--yunmo-text-caption)">{{ hook.expectedPayoffChapter }}</td>
                <td class="py-2 px-3 text-center">
                  <span
                    class="inline-block px-2 py-0.5 rounded text-xs font-medium"
                    :style="{
                      background: hookStatusColor(hook.status) + '18',
                      color: hookStatusColor(hook.status),
                    }"
                  >{{ hookStatusLabel(hook.status) }}</span>
                </td>
                <td class="py-2 px-3 text-center">
                  <a-tag
                    :color="hook.importance === 'CORE' ? 'red' : hook.importance === 'SUB' ? 'blue' : 'default'"
                    size="small"
                  >{{ hook.importance === 'CORE' ? '核心' : hook.importance === 'SUB' ? '支线' : '装饰' }}</a-tag>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty-state">
            <div class="empty-state-icon">🎯</div>
            <p class="empty-state-desc">暂无伏笔记录</p>
          </div>
        </div>

        <!-- 角色图谱 -->
        <div v-if="activeTab === 'characters'" class="yunmo-card p-5">
          <h3 class="text-lg font-semibold mb-4" style="color: var(--yunmo-ink)">角色图谱</h3>
          <div v-if="characterGraph.nodes.length" class="grid grid-cols-2 md:grid-cols-3 gap-3">
            <div
              v-for="node in characterGraph.nodes" :key="node.id"
              class="yunmo-card p-4 hover:shadow-md transition-shadow"
            >
              <div class="flex items-center gap-3">
                <div
                  class="w-10 h-10 rounded-full flex items-center justify-center text-sm font-bold"
                  :style="{
                    background: node.role === 'PROTAGONIST'
                      ? 'rgba(139,58,58,0.12)'
                      : node.role === 'ANTAGONIST'
                        ? 'rgba(179,68,58,0.12)'
                        : 'rgba(184,150,74,0.12)',
                    color: node.role === 'PROTAGONIST'
                      ? 'var(--yunmo-accent)'
                      : node.role === 'ANTAGONIST'
                        ? 'var(--yunmo-red)'
                        : 'var(--yunmo-gold)',
                  }"
                >
                  {{ node.name?.charAt(0) }}
                </div>
                <div class="flex-1 min-w-0">
                  <div class="font-semibold text-sm truncate" style="color: var(--yunmo-ink)">{{ node.name }}</div>
                  <div class="text-xs" style="color: var(--yunmo-text-caption)">
                    {{ node.role === 'PROTAGONIST' ? '主角' : node.role === 'ANTAGONIST' ? '反派' : '配角' }}
                    <span v-if="node.realm" class="ml-2">· {{ node.realm }}</span>
                  </div>
                </div>
              </div>
              <div v-if="node.currentState" class="text-xs mt-2 px-2 py-1 rounded" style="background: var(--yunmo-paper-dark); color: var(--yunmo-text-caption)">
                {{ node.currentState }}
              </div>
              <div v-if="node.location" class="text-xs mt-1" style="color: var(--yunmo-text-caption)">
                📍 {{ node.location }}
              </div>
            </div>
          </div>
          <div v-else class="empty-state">
            <div class="empty-state-icon">👥</div>
            <p class="empty-state-desc">暂无角色数据</p>
          </div>
        </div>
      </a-spin>
    </div>
  </div>
</template>
