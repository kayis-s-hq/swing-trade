<template>
  <aside class="sidebar fixed left-0 top-0 z-9999 h-full w-64 border-r border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-800 transition-transform duration-300 ease-in-out">
    <!-- Logo -->
    <div class="flex items-center justify-between px-6 py-5">
      <div class="flex items-center gap-2">
        <svg class="h-8 w-8 text-indigo-600 dark:text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
        </svg>
        <span class="text-xl font-bold text-gray-800 dark:text-white">SwingTrade</span>
      </div>
      <button @click="$emit('toggle-sidebar')" class="sidebar-toggle md:hidden">
        <svg class="h-6 w-6 text-gray-500 dark:text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>

    <!-- Navigation Menu -->
    <nav class="px-4 py-2">
      <ul class="space-y-1">
        <li>
          <router-link
            to="/"
            class="flex items-center gap-3 rounded-lg px-4 py-3 text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/[0.05]"
          >
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
            </svg>
            <span class="font-medium">Dashboard</span>
          </router-link>
        </li>
        <li>
          <router-link
            to="/positions"
            class="flex items-center gap-3 rounded-lg px-4 py-3 text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/[0.05]"
          >
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" />
            </svg>
            <span class="font-medium">Positions</span>
          </router-link>
        </li>
        <li>
          <router-link
            to="/signals"
            class="flex items-center gap-3 rounded-lg px-4 py-3 text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/[0.05]"
          >
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
            <span class="font-medium">Signals</span>
          </router-link>
        </li>
        <li>
          <router-link
            to="/portfolio"
            class="flex items-center gap-3 rounded-lg px-4 py-3 text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/[0.05]"
          >
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span class="font-medium">Portfolio</span>
          </router-link>
        </li>
      </ul>
    </nav>

    <!-- Dark Mode Toggle in Footer -->
    <div class="absolute bottom-0 w-full border-t border-gray-200 dark:border-gray-700">
      <button
        @click="toggleDarkMode"
        class="flex w-full items-center justify-center gap-2 px-4 py-3 text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-white/[0.05]"
      >
        <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            :d="isDark ? 'M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z' : 'M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z'"
          />
        </svg>
        <span class="text-sm font-medium">{{ isDark ? 'Light Mode' : 'Dark Mode' }}</span>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const isDark = ref(false)

const toggleDarkMode = () => {
  isDark.value = !isDark.value
  localStorage.setItem('dark-mode', isDark.value.toString())

  if (isDark.value) {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
}

const checkDarkMode = () => {
  const stored = localStorage.getItem('dark-mode')
  if (stored === 'true' || (!stored && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    isDark.value = true
    document.documentElement.classList.add('dark')
  }
}

onMounted(() => {
  checkDarkMode()
})

onUnmounted(() => {
  localStorage.setItem('dark-mode', isDark.value.toString())
})
</script>

<style scoped>
/* Sidebar styles */
</style>
