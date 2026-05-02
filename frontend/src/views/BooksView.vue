<script setup lang="ts">
import BooksToolbar from '@/components/BooksToolbar.vue'
import BooksTable from '@/components/BooksTable.vue'
import { useBooks } from '@/composables/useBooks'

const { rows, loading, error, canGoNext, canGoPrev, goNext, goPrev } = useBooks()
</script>

<template>
  <div>
    <BooksToolbar />
    <div
      v-if="error"
      class="mb-3 rounded border border-red-300 bg-red-50 p-2 text-sm text-red-700"
      role="alert"
    >
      {{ error }}
    </div>
    <BooksTable
      :rows="rows"
      :loading="loading"
      :has-next="canGoNext"
      :has-prev="canGoPrev"
      @next="goNext"
      @prev="goPrev"
    />
  </div>
</template>
