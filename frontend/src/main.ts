import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { useThemeStore } from './stores/theme'
import './styles/fonts.css'
import './styles/noto-serif-sc.css'
import './styles/main.css'

const app = createApp(App)
const pinia = createPinia()

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 挂载前同步主题（index.html 内联脚本已提前加好 class，这里做兜底与系统偏好监听）
useThemeStore(pinia).init()

app.mount('#app')
