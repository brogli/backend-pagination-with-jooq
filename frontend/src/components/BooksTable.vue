<script setup lang="ts">
import { computed } from 'vue'
import DataTable, { type DataTableSortEvent } from 'primevue/datatable'
import Column from 'primevue/column'
import Select from 'primevue/select'
import Button from 'primevue/button'
import type { BookDto } from '@/api/generated/types.gen'
import { PAGE_SIZES, SORT_FIELDS, useBookSorting } from '@/composables/useBooks'
import { GENRE_LABEL } from '@/lib/genre'

defineProps<{
  rows: BookDto[]
  loading: boolean
  hasNext: boolean
  hasPrev: boolean
}>()

const emit = defineEmits<{
  next: []
  prev: []
}>()

const { sort, dir, size } = useBookSorting()

const sortOrder = computed<1 | -1>(() => (dir.value === 'asc' ? 1 : -1))

function onSort(event: DataTableSortEvent): void {
  const field = SORT_FIELDS.find((candidate) => candidate === event.sortField)
  if (!field) return
  sort.value = field
  dir.value = event.sortOrder === -1 ? 'desc' : 'asc'
}

const SIZE_OPTIONS = PAGE_SIZES.map((s) => ({ label: String(s), value: s }))

function priceLabel(value: number): string {
  return value.toFixed(2)
}

function ratingLabel(value: number): string {
  return value === 0 ? '—' : value.toFixed(1)
}
</script>

<template>
  <DataTable
    :value="rows"
    lazy
    :loading="loading"
    :sort-field="sort"
    :sort-order="sortOrder"
    striped-rows
    @sort="onSort"
  >
    <Column field="title" header="Title" sortable />
    <Column field="author" header="Author" sortable />
    <Column field="genre" header="Genre">
      <template #body="{ data }: { data: BookDto }">
        {{ GENRE_LABEL[data.genre] }}
      </template>
    </Column>
    <Column field="language" header="Language" />
    <Column field="price" header="Price" sortable>
      <template #body="{ data }: { data: BookDto }">{{ priceLabel(data.price) }}</template>
    </Column>
    <Column field="rating" header="Rating" sortable>
      <template #body="{ data }: { data: BookDto }">{{ ratingLabel(data.rating) }}</template>
    </Column>
    <Column field="publishedAt" header="Published" sortable />
    <Column field="inStock" header="Stock">
      <template #body="{ data }: { data: BookDto }">
        {{ data.inStock ? 'Yes' : 'No' }}
      </template>
    </Column>
  </DataTable>

  <div class="mt-3 flex items-center justify-between">
    <div class="flex items-center gap-2 text-sm">
      <span>Page size</span>
      <Select
        v-model="size"
        :options="SIZE_OPTIONS"
        option-label="label"
        option-value="value"
        class="w-24"
      />
    </div>
    <div class="flex gap-2">
      <Button label="Previous" :disabled="!hasPrev || loading" @click="emit('prev')" />
      <Button label="Next" :disabled="!hasNext || loading" @click="emit('next')" />
    </div>
  </div>
</template>
