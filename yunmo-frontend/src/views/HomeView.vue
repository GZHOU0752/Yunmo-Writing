<script setup>
import { useRouter } from 'vue-router'
import FeatureCard from '@/components/landing/FeatureCard.vue'
import AgentShowcase from '@/components/landing/AgentShowcase.vue'

const router = useRouter()

const features = [
  {
    icon: '🤖',
    title: '六大智能体协作',
    description: '主编统筹、架构师规划、主笔写作、质检官审计、类型守卫扫描、角色守护一致性检查，分工明确，各司其职。',
  },
  {
    icon: '📊',
    title: '33 维度质量审计',
    description: '情节 8 项、角色 7 项、文笔 12 项、合规 6 项全方位质检，配合对抗编辑三轮投票，确保每一章都经得起推敲。',
  },
  {
    icon: '',
    title: 'RAG 知识库',
    description: '上传参考文稿，AI 自动分块 embedding，写作时智能检索相关内容，让 AI 在你的世界观里创作，而非凭空编造。',
  },
  {
    icon: '',
    title: '四层大纲树',
    description: '总纲 → 分卷 → 章纲 → 节，四级结构层层递进。AI 自动补全子节点，支持节点绑定章节、与 AI 讨论剧情走向。',
  },
  {
    icon: '🔍',
    title: '全文搜索替换',
    description: '跨章节全文搜索，高亮上下文窗口，支持批量替换 + 版本快照，修改一个细节不必逐章翻找。',
  },
  {
    icon: '📖',
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

      <div class="relative z-10 flex flex-col items-center max-w-3xl mx-auto w-full">
        <!-- 对联 -->
        <div class="font-brush text-5xl md:text-7xl tracking-[0.08em] text-center hero-title"
             style="color:var(--yunmo-couplet); line-height:1.15">
          <span class="block -ml-12 md:-ml-20">云起笔落处</span>
          <span class="block ml-12 md:ml-20">墨写千秋书</span>
        </div>

        <!-- 副标题 -->
        <p class="mt-10 md:mt-14 text-base md:text-lg leading-relaxed text-center max-w-lg mx-auto hero-subtitle"
           style="color:var(--yunmo-text-secondary)">
          以 AI 之笔，构万界之境，写不朽之书
        </p>

        <!-- 描述 -->
        <p class="mt-4 text-sm md:text-base text-center max-w-md mx-auto hero-subtitle"
           style="color:var(--yunmo-text-caption)">
          六个助手分工协作，从世界观到逐章写作。<br class="hidden md:block">
          你只需专注于你想讲的故事。
        </p>

        <!-- CTA -->
        <div class="mt-12 cta-group hero-cta">
          <a-button type="primary" size="large" class="px-10 h-12 text-base"
                    @click="router.push('/login')">
            开始写作
          </a-button>
          <button class="cta-secondary" @click="document.getElementById('features')?.scrollIntoView({ behavior: 'smooth' })">
            了解更多
          </button>
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
    <section id="features" class="py-20 md:py-28 px-6 md:px-10 bg-[var(--yunmo-paper)]">
      <div class="max-w-6xl mx-auto">
        <div class="md:flex md:items-end md:justify-between mb-14">
          <div>
            <h2 class="text-2xl md:text-3xl font-bold mb-3" style="color:var(--yunmo-ink)">
              为长篇创作而生的 AI 写作系统
            </h2>
            <p class="text-sm md:text-base max-w-md" style="color:var(--yunmo-text-caption)">
              不只是续写 — 从世界观搭建到质量把关，全流程智能辅助
            </p>
          </div>
          <!-- 亮点数字 -->
          <div class="hidden md:flex items-end gap-8 mt-6 md:mt-0">
            <div>
              <div class="text-2xl font-bold font-tabular" style="color:var(--yunmo-accent)">6</div>
              <div class="text-xs mt-1" style="color:var(--yunmo-text-caption)">AI 智能体</div>
            </div>
            <div>
              <div class="text-2xl font-bold font-tabular" style="color:var(--yunmo-accent)">33</div>
              <div class="text-xs mt-1" style="color:var(--yunmo-text-caption)">质量维度</div>
            </div>
            <div>
              <div class="text-2xl font-bold font-tabular" style="color:var(--yunmo-accent)">8</div>
              <div class="text-xs mt-1" style="color:var(--yunmo-text-caption)">类型模板</div>
            </div>
          </div>
        </div>

        <!-- 不对称网格：2-1-2-1 节奏 -->
        <div class="space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <FeatureCard v-bind="features[0]" :delay="0" class="md:col-span-2" />
            <FeatureCard v-bind="features[1]" :delay="100" />
          </div>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <FeatureCard v-bind="features[2]" :delay="200" />
            <FeatureCard v-bind="features[3]" :delay="300" class="md:col-span-2" />
          </div>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <FeatureCard v-bind="features[4]" :delay="400" />
            <FeatureCard v-bind="features[5]" :delay="500" />
          </div>
        </div>
      </div>
    </section>

    <!-- ========== Agent 架构 ========== -->
    <section class="py-20 md:py-24 px-6 md:px-10 bg-[var(--yunmo-paper-light)]">
      <div class="max-w-5xl mx-auto text-center">
        <h2 class="text-2xl md:text-3xl font-bold mb-3" style="color:var(--yunmo-ink)">
          六智能体协作流水线
        </h2>
        <p class="text-sm mb-14 max-w-lg mx-auto" style="color:var(--yunmo-text-caption)">
          每个智能体各司其职，通过确定性流水线协作，从上下文组装到质量审计，全自动完成章节创作
        </p>

        <AgentShowcase />

        <div class="mt-12 text-sm" style="color:var(--yunmo-text-caption)">
          对抗编辑 · 三轮投票 · 3 位读者独立评分 · 中位数 ≥ 8 分方可通过
        </div>
      </div>
    </section>

    <!-- ========== 页脚 ========== -->
    <footer class="landing-footer">
      <div class="landing-footer-brand">云墨 YunMo</div>
      <p class="text-xs mt-2" style="color:var(--yunmo-text-caption)">
        古典美学 AI 小说写作系统
      </p>
      <div class="landing-footer-links">
        <span class="landing-footer-link" @click="router.push('/login')">登录</span>
        <span class="landing-footer-link" @click="router.push('/register')">注册</span>
      </div>
    </footer>

  </div>
</template>
