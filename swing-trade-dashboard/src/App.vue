<template>
  <div class="flex h-screen overflow-hidden bg-gray-50 dark:bg-gray-900">
    <!-- Sidebar -->
    <Sidebar @toggle-sidebar="isSidebarOpen = !isSidebarOpen" />

    <!-- Main Content Area -->
    <div class="relative flex flex-1 flex-col overflow-y-auto overflow-x-hidden">
      <!-- Header -->
      <Header @toggle-sidebar="isSidebarOpen = !isSidebarOpen" />

      <!-- Main Content -->
      <main class="flex-1">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'

const isSidebarOpen = ref(false)
</script>

<style scoped>
/* Main app styles */
</style>
