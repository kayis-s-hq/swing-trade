import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { reportRuntimeError } from './stores/runtimeErrors'
import { loadSettings } from './stores/settings'
import './assets/main.css'

const app = createApp(App)
app.config.errorHandler = (error, _instance, info) => {
  reportRuntimeError(error, { source: 'vue', info })
}
app.use(createPinia())
app.use(router)

// Load persisted broker/settings before mounting the shell. Header and Sidebar
// otherwise render their defaults first and may incorrectly probe Fyers. A
// failure here must not block mounting (the app should still boot on
// defaults) but must not be silently swallowed either - a total settings
// outage otherwise mounts the shell with no signal that anything is wrong.
void loadSettings()
  .catch((error) => {
    reportRuntimeError(error, { source: 'bootstrap', info: 'Initial settings load failed' })
  })
  .finally(() => app.mount('#app'))
