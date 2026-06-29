<script setup>
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import FeatureCard from '@/components/landing/FeatureCard.vue'
import AgentShowcase from '@/components/landing/AgentShowcase.vue'

const router = useRouter()
const featuresRef = ref(null)

function scrollToFeatures() {
  nextTick(() => {
    featuresRef.value?.scrollIntoView({ behavior: 'smooth' })
  })
}

// 内联 SVG 图标（替代 emoji，保证跨平台一致）
const svgAgent = '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><circle cx="12" cy="12" r="9"/><circle cx="12" cy="10" r="3"/><path d="M7 16.5c1-2 3-3.5 5-3.5s4 1.5 5 3.5"/><circle cx="9" cy="9" r="0.75" fill="var(--yunmo-accent)"/><circle cx="15" cy="9" r="0.75" fill="var(--yunmo-accent)"/></svg>'
const svgChart = '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><path d="M3 3v18h18"/><path d="M7 16l4-6 4 3 4-8"/></svg>'
const svgBrain = '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="3"/><path d="M3 9h18"/><path d="M9 3v18"/><circle cx="6" cy="6" r="0.5" fill="var(--yunmo-accent)"/><circle cx="6" cy="12" r="0.5" fill="var(--yunmo-accent)"/></svg>'
const svgTree = '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><path d="M12 3v18"/><path d="M5 8l7-5 7 5"/><path d="M5 16l7-5 7 5"/></svg>'
const svgSearch = '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><circle cx="11" cy="11" r="7"/><path d="M16.5 16.5L21 21"/><path d="M8 11h6"/><path d="M11 8v6"/></svg>'
const svgBook = '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--yunmo-accent)" stroke-width="1.5"><path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/><path d="M8 7h7"/><path d="M8 10h5"/></svg>'

const features = [
  {
    icon: svgAgent,
    title: '六大智能体协作',
    description: '主编统筹、架构师规划、主笔写作、质检官审计、类型守卫扫描、角色守护一致性检查，分工明确，各司其职。',
  },
  {
    icon: svgChart,
    title: '33 维度质量审计',
    description: '情节 8 项、角色 7 项、文笔 12 项、合规 6 项全方位质检，配合对抗编辑三轮投票，确保每一章都经得起推敲。',
  },
  {
    icon: svgBrain,
    title: 'RAG 知识库',
    description: '上传参考文稿，AI 自动分块 embedding，写作时智能检索相关内容，让 AI 在你的世界观里创作，而非凭空编造。',
  },
  {
    icon: svgTree,
    title: '四层大纲树',
    description: '总纲 → 分卷 → 章纲 → 节，四级结构层层递进。AI 自动补全子节点，支持节点绑定章节、与 AI 讨论剧情走向。',
  },
  {
    icon: svgSearch,
    title: '全文搜索替换',
    description: '跨章节全文搜索，高亮上下文窗口，支持批量替换 + 版本快照，修改一个细节不必逐章翻找。',
  },
  {
    icon: svgBook,
    title: '沉浸式阅读模式',
    description: '仿古书排版，字号行距自由调节，键盘快捷键翻页，专注模式隐藏一切干扰，只留文字与你。',
  },
]

// 墨粒子装饰
const particles = Array.from({ length: 6 }, (_, i) => ({
  id: i,
  size: 60 + Math.random() * 120,
  left: 10 + Math.random() * 80,
  top: 10 + Math.random() * 80,
}))
</script>

<template>
  <div class="min-h-[100dvh] flex flex-col">

    <!-- 导航 -->
    <nav class="h-14 border-b border-[var(--yunmo-border)] flex items-center justify-between px-6 md:px-10 max-w-7xl mx-auto w-full relative z-10">
      <span class="text-xl md:text-2xl font-bold tracking-widest" style="color:var(--yunmo-accent)">云 墨</span>
      <div class="flex gap-3">
        <a-button @click="router.push('/login')">登录</a-button>
        <a-button type="primary" @click="router.push('/register')">注册</a-button>
      </div>
    </nav>

    <!-- ========== Hero ========== -->
    <section class="hero-ink-bg flex-1 flex flex-col items-center justify-center relative px-6 py-20 md:py-28">
      <!-- 墨粒子装饰 -->
      <div
        v-for="p in particles"
        :key="p.id"
        class="ink-particle"
        :style="{
          width: `${p.size}px`,
          height: `${p.size}px`,
          left: `${p.left}%`,
          top: `${p.top}%`,
        }"
      />

      <div class="relative z-10 max-w-6xl mx-auto w-full grid md:grid-cols-5 gap-8 items-center px-6">
        <!-- 左栏：对联 + 文案 + CTA -->
        <div class="md:col-span-3 flex flex-col">
          <div class="font-brush text-5xl md:text-7xl tracking-[0.08em] hero-title"
               style="color:var(--yunmo-couplet); line-height:1.15">
            <span class="block md:-ml-4">云起笔落处</span>
            <span class="block md:ml-8">墨写千秋书</span>
          </div>
          <p class="mt-10 md:mt-14 text-base md:text-lg leading-relaxed max-w-md hero-subtitle"
             style="color:var(--yunmo-text-secondary)">
            以 AI 之笔，构万界之境，写不朽之书
          </p>
          <p class="mt-4 text-sm md:text-base max-w-sm hero-subtitle">
            六个助手分工协作，从世界观到逐章写作。
            你只需专注于你想讲的故事。
          </p>
          <div class="mt-10 flex gap-4 flex-wrap hero-cta">
            <a-button type="primary" size="large" class="px-10 h-12 text-base"
                      @click="router.push('/login')">
              开始写作
            </a-button>
            <button class="cta-secondary" @click="scrollToFeatures">
              了解更多
            </button>
          </div>
        </div>
        <!-- 右栏：纸稿叠影 -->
        <div class="md:col-span-2 hidden md:flex items-center justify-center">
          <div class="relative w-44 h-56">
            <div class="absolute inset-0 rounded-md opacity-20" style="background:var(--yunmo-paper-light);transform:rotate(-3deg);box-shadow:0 1px 3px rgba(31,22,12,0.06)"></div>
            <div class="absolute inset-1 rounded-md opacity-30" style="background:var(--yunmo-paper-light);transform:rotate(1deg);box-shadow:0 1px 3px rgba(31,22,12,0.05)"></div>
            <div class="absolute inset-2 rounded-md opacity-90 flex items-center justify-center" style="background:var(--yunmo-paper-light);box-shadow:0 2px 8px rgba(31,22,12,0.06)">
              <span class="font-brush text-6xl" style="color:var(--yunmo-accent);opacity:0.25">墨</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部波浪 -->
      <div class="hero-wave">
        <svg viewBox="0 0 1440 60" preserveAspectRatio="none">
          <path d="M0,30 C360,60 1080,0 1440,30 L1440,60 L0,60 Z" fill="var(--yunmo-paper)" />
        </svg>
      </div>
    </section>

    <!-- ========== 特性展示 ========== -->
    <section ref="featuresRef" class="py-20 md:py-28 px-6 md:px-10 bg-[var(--yunmo-paper)]">
      <div class="max-w-6xl mx-auto">
        <div class="md:flex md:items-end md:justify-between mb-14">
          <div>
            <h2 class="text-2xl md:text-3xl font-bold mb-3">
              为长篇创作而生的 AI 写作系统
            </h2>
            <p class="text-sm md:text-base max-w-md">
              不只是续写 — 从世界观搭建到质量把关，全流程智能辅助
            </p>
          </div>
          <!-- 亮点数字 -->
          <div class="hidden md:flex items-end gap-8 mt-6 md:mt-0">
            <div class="text-right">
              <div class="text-2xl font-bold font-tabular" style="color:var(--yunmo-ink)">6</div>
              <div class="text-xs mt-1">AI 智能体</div>
            </div>
            <div class="text-right">
              <div class="text-2xl font-bold font-tabular" style="color:var(--yunmo-ink)">33</div>
              <div class="text-xs mt-1">质量维度</div>
            </div>
            <div class="text-right">
              <div class="text-2xl font-bold font-tabular" style="color:var(--yunmo-ink)">8</div>
              <div class="text-xs mt-1">类型模板</div>
            </div>
          </div>
        </div>

        <!-- 不对称网格：变奏节奏 -->
        <div class="space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <FeatureCard v-bind="features[0]" :delay="0" class="md:col-span-2" />
            <FeatureCard v-bind="features[1]" :delay="100" variant="accent" />
          </div>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <FeatureCard v-bind="features[2]" :delay="200" variant="accent" />
            <FeatureCard v-bind="features[3]" :delay="300" class="md:col-span-2" />
          </div>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <FeatureCard v-bind="features[4]" :delay="400" variant="compact" />
            <FeatureCard v-bind="features[5]" :delay="500" />
          </div>
        </div>
      </div>
    </section>

    <!-- ========== Agent 架构 ========== -->
    <section class="py-20 md:py-24 px-6 md:px-10 bg-[var(--yunmo-paper-light)]">
      <div class="max-w-5xl mx-auto text-center">
        <h2 class="text-2xl md:text-3xl font-bold mb-3">
          六智能体协作流水线
        </h2>
        <p class="text-sm mb-14 max-w-lg mx-auto">
          每个智能体各司其职，通过确定性流水线协作，从上下文组装到质量审计，全自动完成章节创作
        </p>

        <AgentShowcase />

        <div class="mt-12 text-sm">
          对抗编辑 · 三轮投票 · 3 位读者独立评分 · 中位数 ≥ 8 分方可通过
        </div>
      </div>
    </section>

    <!-- ========== 页脚 ========== -->
    <footer class="landing-footer">
      <div class="landing-footer-brand">云墨 YunMo</div>
      <p class="text-xs mt-2">
        古典美学 AI 小说写作系统
      </p>
      <div class="landing-footer-links">
        <span class="landing-footer-link" @click="router.push('/login')">登录</span>
        <span class="landing-footer-link" @click="router.push('/register')">注册</span>
      </div>
    </footer>

  </div>
</template>
