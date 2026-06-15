<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="min-h-[100dvh] flex flex-col">

    <!-- 导航 — 轻，不抢戏 -->
    <nav class="h-14 border-b border-[var(--yunmo-border)] flex items-center justify-between px-8 max-w-7xl mx-auto w-full">
      <span class="text-xl md:text-2xl font-bold tracking-widest" style="color:var(--yunmo-accent)">云 墨</span>
      <div class="flex gap-3">
        <a-button @click="router.push('/login')">登录</a-button>
        <a-button type="primary" @click="router.push('/register')">注册</a-button>
      </div>
    </nav>

    <!-- ========== Hero ========== -->
    <section class="flex-1 flex flex-col items-center max-w-4xl mx-auto w-full px-6 pt-20 md:pt-24">

      <!-- 对联 — 错落紧贴 -->
      <div class="font-brush text-5xl md:text-7xl tracking-[0.08em] text-center"
           style="color:var(--yunmo-couplet); line-height:1.15">
        <span class="block -ml-12 md:-ml-20">云起笔落处</span>
        <span class="block ml-12 md:ml-20">墨写千秋书</span>
      </div>

      <!-- 副题 + CTA — 与对联拉开距离 -->
      <div class="mt-16 md:mt-20 text-center">
        <p class="text-base md:text-lg leading-relaxed max-w-md mx-auto"
           style="color:var(--yunmo-text-secondary)">
          六个助手分工协作，从世界观到逐章写作。<br>你只需专注于你想讲的故事。
        </p>
        <div class="mt-10">
          <a-button type="primary" size="large" class="px-10 h-12 text-base"
                    @click="router.push('/login')">
            开始写作
          </a-button>
        </div>
      </div>

    </section>

    <!-- 页脚 -->
    <footer class="h-12 border-t border-[var(--yunmo-border)] flex items-center justify-center text-sm"
            style="color:var(--yunmo-text-caption)">
      云墨 YunMo
    </footer>

  </div>
</template>
