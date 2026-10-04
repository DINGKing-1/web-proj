import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Element Plus 样式由 unplugin 按需注入（importStyle:'css'），不要全量引入
import './styles/main.css'
import './styles/element-theme.css'
import App from './App.vue'
import router from './router'
import { useUserStore } from './stores/user'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.mount('#app')

// client.js 401 时同步清掉 Pinia 登录态，避免 UI 仍显示已登录
window.addEventListener('auth-expired', () => {
  useUserStore().setUser(null)
})
