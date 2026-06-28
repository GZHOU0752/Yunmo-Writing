<script setup>
const agentIcons = {
  editor: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M7 8h10M7 12h6M7 16h3"/></svg>',
  architect: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>',
  writer: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 013 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>',
  auditor: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><path d="M9 12l2 2 4-4"/></svg>',
  guard: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
  character: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 4-7 8-7s8 3 8 7"/></svg>',
}
const agents = [
  { icon: agentIcons.editor, label: '主编', desc: '统筹全局，协调各智能体协作' },
  { icon: agentIcons.architect, label: '架构师', desc: '因果链 · 伏笔 · 大纲规划' },
  { icon: agentIcons.writer, label: '主笔', desc: 'SSE 流式逐章写作' },
  { icon: agentIcons.auditor, label: '质检官', desc: '33 维度质量审计' },
  { icon: agentIcons.guard, label: '类型守卫', desc: '类型禁词机械扫描' },
  { icon: agentIcons.character, label: '角色守护', desc: '6 层认知一致性检查' },
]
</script>

<template>
  <div class="flex flex-wrap justify-center gap-4 md:gap-6">
    <template v-for="(agent, i) in agents" :key="i">
      <div class="agent-node float-in" :style="{ animationDelay: `${i * 80}ms` }">
        <div class="agent-node-icon" v-html="agent.icon"></div>
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
