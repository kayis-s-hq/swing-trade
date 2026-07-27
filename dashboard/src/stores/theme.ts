import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const STORAGE_KEY = 'swingtrade_theme'

function loadTheme(): boolean {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (raw !== null) return raw === 'true'
  // Default to dark for trading dashboard — light mode has poor card contrast
  return true
}

export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(loadTheme())

  // Persist theme
  watch(isDark, (dark) => {
    localStorage.setItem(STORAGE_KEY, String(dark))
  })

  // Apply theme to document
  watch(
    isDark,
    (dark) => {
      document.documentElement.classList.toggle('dark', dark)
      document.documentElement.style.setProperty('--color-bg-primary', dark ? '#0f1117' : '#f0f2f5')
      document.documentElement.style.setProperty('--color-bg-surface', dark ? '#161923' : '#ffffff')
      document.documentElement.style.setProperty(
        '--color-bg-elevated',
        dark ? '#1c2030' : '#ffffff'
      )
      document.documentElement.style.setProperty('--color-bg-hover', dark ? '#1f2438' : '#e8eaef')
      document.documentElement.style.setProperty('--color-bg-active', dark ? '#252b42' : '#dfe2ea')

      document.documentElement.style.setProperty(
        '--color-border-subtle',
        dark ? '#252a3a' : '#d1d5db'
      )
      document.documentElement.style.setProperty(
        '--color-border-default',
        dark ? '#323850' : '#9ca3af'
      )
      document.documentElement.style.setProperty('--color-border-focus', '#00d4a0')

      document.documentElement.style.setProperty(
        '--color-text-primary',
        dark ? '#e8eaf0' : '#1a1f2e'
      )
      document.documentElement.style.setProperty(
        '--color-text-secondary',
        dark ? '#b0b8c8' : '#4a5568'
      )
      document.documentElement.style.setProperty('--color-text-muted', dark ? '#7a849a' : '#8892a4')
      document.documentElement.style.setProperty(
        '--color-text-inverse',
        dark ? '#0f1117' : '#ffffff'
      )

      document.documentElement.style.setProperty(
        '--color-success-bg',
        dark ? 'rgba(0, 212, 160, 0.1)' : 'rgba(0, 212, 160, 0.12)'
      )
      document.documentElement.style.setProperty(
        '--color-danger-bg',
        dark ? 'rgba(255, 77, 106, 0.1)' : 'rgba(255, 77, 106, 0.12)'
      )
      document.documentElement.style.setProperty(
        '--color-info-bg',
        dark ? 'rgba(77, 154, 255, 0.1)' : 'rgba(77, 154, 255, 0.12)'
      )
    },
    { immediate: true }
  )

  function toggle() {
    isDark.value = !isDark.value
  }

  return { isDark, toggle }
})
