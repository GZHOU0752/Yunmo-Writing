import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import './styles/main.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

// 全局错误处理
app.config.errorHandler = (err, _instance, info) => {
  console.error('[Vue Error]', err, 'Info:', info)
}

app.use(createPinia())
app.use(router)
app.use(Antd)
app.mount('#app')
