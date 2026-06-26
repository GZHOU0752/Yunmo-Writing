import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { message } from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import './styles/main.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

// 全局错误处理——所有提示均为中文，不展示原始英文报错
app.config.errorHandler = (err, _instance, info) => {
  console.error('[异常]', err, '详情:', info)
  const msg = (err?.message || err?.detail || String(err)).toLowerCase()
  if (msg && !msg.includes('abort')) {
    // 常见错误映射为中文提示
    if (msg.includes('network') || msg.includes('fetch') || msg.includes('timeout')) {
      message.error('网络连接异常，请检查网络后重试')
    } else if (msg.includes('401') || msg.includes('unauthorized')) {
      message.error('登录已过期，请重新登录')
    } else if (msg.includes('500') || msg.includes('internal')) {
      message.error('服务器繁忙，请稍后重试')
    } else if (msg.includes('json') || msg.includes('parse')) {
      message.error('数据解析异常，请刷新页面后重试')
    } else {
      message.error('操作失败，请重试')
    }
  }
}

app.use(createPinia())
app.use(router)
app.mount('#app')
