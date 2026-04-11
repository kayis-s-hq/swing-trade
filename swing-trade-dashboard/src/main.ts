import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'
import './assets/main.css'

const app = createApp(App)

// Vue configuration
app.config.globalProperties.$debug = false
// TypeScript note: warnHandler requires a function, null is not assignable
// @ts-ignore - warnHandler is not fully typed in Vue 3
app.config.warnHandler = undefined

// Error handler for production
app.config.errorHandler = (err, _instance, info) => {
  console.error('Vue error:', err, info)
}

app.use(createPinia())
app.use(router)

app.mount('#app')
