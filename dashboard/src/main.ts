import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { reportRuntimeError } from './stores/runtimeErrors'
import './assets/main.css'

const app = createApp(App)
app.config.errorHandler = (error, _instance, info) => {
  reportRuntimeError(error, { source: 'vue', info })
}
app.use(createPinia())
app.use(router)
app.mount('#app')
