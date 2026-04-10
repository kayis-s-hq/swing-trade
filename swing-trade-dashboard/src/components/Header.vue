<template>
  <header class="header sticky top-0 z-999 flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4 dark:border-gray-800 dark:bg-gray-800">
    <!-- Mobile Hamburger Menu -->
    <div class="flex items-center gap-4 md:hidden">
      <button @click="$emit('toggle-sidebar')" class="text-gray-600 hover:text-gray-800 dark:text-gray-400 dark:hover:text-white">
        <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
        </svg>
      </button>
    </div>

    <!-- Desktop Search Bar (Hidden on Mobile) -->
    <div class="hidden md:flex flex-1 max-w-md mx-4">
      <div class="relative w-full">
        <svg class="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
        <input
          type="text"
          placeholder="Search..."
          class="w-full rounded-lg border border-gray-200 bg-gray-50 py-2 pl-10 pr-4 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-700 dark:bg-gray-700 dark:text-white dark:placeholder-gray-400"
        />
      </div>
    </div>

    <!-- Right Side Icons -->
    <div class="flex items-center gap-4">
      <!-- Dark Mode Toggle -->
      <button @click="toggleDarkMode" class="text-gray-600 hover:text-gray-800 dark:text-gray-400 dark:hover:text-white">
        <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            :d="isDark ? 'M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z' : 'M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z'"
          />
        </svg>
      </button>

      <!-- User Profile -->
      <div class="relative">
        <button class="flex items-center gap-2 rounded-full bg-gray-100 p-1.5 hover:bg-gray-200 dark:bg-gray-700 dark:hover:bg-gray-600">
          <div class="h-8 w-8 rounded-full bg-indigo-500 flex items-center justify-center text-white font-medium">
            U
          </div>
          <span class="hidden md:block text-sm font-medium text-gray-700 dark:text-white">User</span>
          <svg class="hidden md:block h-4 w-4 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
          </svg>
        </button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

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
</script>

<style scoped>
/* Header styles */
</style>
