import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElLoading } from 'element-plus'
import App from './App.vue'
import { router } from './router'

// Element Plus：基础样式 + 官方暗色变量（启用 .dark），随后用本项目的
// 毛玻璃主题覆盖成原有审美。样式加载顺序很关键：
//   element 基础 → element 暗色变量 → 本项目 style.css(定义 --pink/--violet…) → element-theme(用这些变量染色)
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './style.css'
import './styles/element-theme.css'

document.documentElement.classList.add('dark') // 启用 Element 暗色基线

const app = createApp(App)
app.use(createPinia()) // 先装 Pinia：路由守卫里要用 store
app.use(router)
app.use(ElLoading) // 注册 v-loading 指令（显式按需引入时需手动装）
app.mount('#app')
