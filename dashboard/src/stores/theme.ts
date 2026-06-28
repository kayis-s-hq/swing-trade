import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const STORAGE_KEY = 'swingtrade_theme'

function loadTheme(): boolean {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (raw !== null) return raw === 'true'
  // Detect system preference
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(loadTheme())

  // Persist theme
  watch(isDark, (dark) => {
    localStorage.setItem(STORAGE_KEY, String(dark))
  })

  // Apply theme to document
  watch(isDark, (dark) => {
    document.documentElement.classList.toggle('dark', dark)
    document.documentElement.style.setProperty('--color-bg-primary', dark ? '#0f1117' : '#ffffff')
    document.documentElement.style.setProperty('--color-bg-surface', dark ? '#161923' : '#f8f9fc')
    document.documentElement.style.setProperty('--color-bg-elevated', dark ? '#1c2030' : '#f0f2f8')
    document.documentElement.style.setProperty('--color-bg-hover', dark ? '#1f2438' : '#e8eaf0')
    document.documentElement.style.setProperty('--color-bg-active', dark ? '#252b42' : '#dfe2ea')

    document.documentElement.style.setProperty('--color-border-subtle', dark ? '#252a3a' : '#e2e6ef')
    document.documentElement.style.setProperty('--color-border-default', dark ? '#323850' : '#c8cfd8')
    document.documentElement.style.setProperty('--color-border-focus', '#00d4a0')

    document.documentElement.style.setProperty('--color-text-primary', dark ? '#e8eaf0' : '#1a1f2e')
    document.documentElement.style.setProperty('--color-text-secondary', dark ? '#b0b8c8' : '#4a5568')
    document.documentElement.style.setProperty('--color-text-muted', dark ? '#7a849a' : '#8892a4')
    document.documentElement.style.setProperty('--color-text-inverse', dark ? '#0f1117' : '#ffffff')
  }, { immediate: true })

  function toggle() {
    isDark.value = !isDark.value
  }

  return { isDark, toggle }
})
