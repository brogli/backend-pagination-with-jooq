<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <div class="min-h-screen bg-surface-0 text-surface-900 dark:bg-surface-950 dark:text-surface-0">
    <header class="border-b border-surface-200 dark:border-surface-800">
      <nav class="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <span class="text-lg font-semibold">Books</span>
      </nav>
    </header>
    <main class="mx-auto max-w-6xl px-6 py-6">
      <RouterView />
    </main>
  </div>
</template>
