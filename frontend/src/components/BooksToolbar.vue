<script setup lang="ts">
import { computed } from 'vue'
import dayjs from 'dayjs'
import MultiSelect from 'primevue/multiselect'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import DatePicker from 'primevue/datepicker'
import Button from 'primevue/button'
import { useBookFilters } from '@/composables/useBooks'
import { GENRE_OPTIONS } from '@/lib/genre'
import { LANGUAGE_OPTIONS } from '@/lib/language'

const filters = useBookFilters()

const STOCK_OPTIONS = [
  { label: 'In stock', value: true },
  { label: 'Out of stock', value: false },
]

const MAX_RATING = 5
const RATING_STEP = 0.5

// DatePicker's types declare Date even with updateModelType="string"; bridge with dayjs (local components round-trip the picked day).
const publishedAfter = computed({
  get: () => (filters.publishedAfter.value ? dayjs(filters.publishedAfter.value).toDate() : null),
  set: (value: Date | null) => {
    filters.publishedAfter.value = value ? dayjs(value).format('YYYY-MM-DD') : null
  },
})
</script>

<template>
  <div class="mb-4 flex flex-wrap items-end gap-3">
    <div class="min-w-32 flex-1">
      <label class="mb-1 block text-sm">Genre</label>
      <MultiSelect
        v-model="filters.genre.value"
        :options="GENRE_OPTIONS"
        option-label="label"
        option-value="value"
        placeholder="Any"
        class="w-full"
        show-clear
      />
    </div>
    <div class="min-w-32 flex-1">
      <label class="mb-1 block text-sm">Language</label>
      <Select
        v-model="filters.language.value"
        :options="LANGUAGE_OPTIONS"
        option-label="label"
        option-value="value"
        placeholder="Any"
        class="w-full"
        show-clear
      />
    </div>
    <div class="min-w-32 flex-1">
      <label class="mb-1 block text-sm">Stock</label>
      <Select
        v-model="filters.inStock.value"
        :options="STOCK_OPTIONS"
        option-label="label"
        option-value="value"
        placeholder="Any"
        class="w-full"
        show-clear
      />
    </div>
    <div class="min-w-32 flex-1">
      <label class="mb-1 block text-sm">Min rating</label>
      <InputNumber
        v-model="filters.minRating.value"
        :min="0"
        :max="MAX_RATING"
        :step="RATING_STEP"
        :max-fraction-digits="1"
        fluid
      />
    </div>
    <div class="min-w-32 flex-1">
      <label class="mb-1 block text-sm">Price min</label>
      <InputNumber v-model="filters.priceMin.value" :min="0" :max-fraction-digits="2" fluid />
    </div>
    <div class="min-w-32 flex-1">
      <label class="mb-1 block text-sm">Price max</label>
      <InputNumber v-model="filters.priceMax.value" :min="0" :max-fraction-digits="2" fluid />
    </div>
    <div class="min-w-32 flex-1">
      <label class="mb-1 block text-sm">Published after</label>
      <DatePicker v-model="publishedAfter" date-format="yy-mm-dd" fluid show-icon show-button-bar />
    </div>
    <Button label="Reset" severity="secondary" outlined @click="filters.reset()" />
  </div>
</template>
