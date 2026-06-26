<script setup>
const agents = [
  { icon: '📋', label: '主编', desc: '统筹全局，协调各智能体协作' },
  { icon: '🏗️', label: '架构师', desc: '因果链 · 伏笔 · 大纲规划' },
  { icon: '✍️', label: '主笔', desc: 'SSE 流式逐章写作' },
  { icon: '', label: '质检官', desc: '33 维度质量审计' },
  { icon: '🛡️', label: '类型守卫', desc: '类型禁词机械扫描' },
  { icon: '👤', label: '角色守护', desc: '6 层认知一致性检查' },
]
</script>

<template>
  <div class="flex flex-wrap justify-center gap-4 md:gap-6">
    <template v-for="(agent, i) in agents" :key="i">
      <div class="agent-node float-in" :style="{ animationDelay: `${i * 80}ms` }">
        <div class="agent-node-icon">{{ agent.icon }}</div>
        <div class="agent-node-label">{{ agent.label }}</div>
        <div class="agent-node-desc">{{ agent.desc }}</div>
      </div>
      <!-- 连接箭头（每两个节点之间，最后一组不显示） -->
      <div
        v-if="i < agents.length - 1"
        class="agent-connector hidden md:flex"
      >
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M5 12h14M13 6l6 6-6 6" />
        </svg>
      </div>
    </template>
  </div>
</template>
