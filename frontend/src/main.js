import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import pinia from './store'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import './style/theme.css'

const app = createApp(App)

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 初始化主题
const savedTheme = localStorage.getItem('theme') || 'dark'
document.documentElement.setAttribute('class', savedTheme)

app.use(router)
app.use(pinia)
app.use(ElementPlus, { size: 'default' })
app.mount('#app')
