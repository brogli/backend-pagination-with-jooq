import { computed, onScopeDispose, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRouteQuery } from '@vueuse/router'
import { searchBooks } from '@/api/generated/sdk.gen'
import type {
  BookDto,
  Direction,
  Genre,
  Language,
  SeekKey,
  SortField,
} from '@/api/generated/types.gen'

export type PageSize = 25 | 50 | 100

export const SORT_FIELDS: readonly SortField[] = [
  'title',
  'author',
  'price',
  'rating',
  'publishedAt',
]
export const PAGE_SIZES: readonly PageSize[] = [25, 50, 100]

function nullableNumber(name: string) {
  return useRouteQuery<string | null, number | null>(name, null, {
    transform: {
      get: (v) => (v == null || v === '' ? null : Number(v)),
      set: (v) => (v == null ? null : String(v)),
    },
  })
}

function nullableBool(name: string) {
  return useRouteQuery<string | null, boolean | null>(name, null, {
    transform: {
      get: (v) => (v === 'true' ? true : v === 'false' ? false : null),
      set: (v) => (v == null ? null : String(v)),
    },
  })
}

function stringArray<T extends string>(name: string) {
  return useRouteQuery<string | string[] | null | undefined, T[]>(name, [], {
    transform: {
      get: (v) => (v == null ? [] : Array.isArray(v) ? (v as T[]) : [v as T]),
      set: (v) => v,
    },
  })
}

export function useBookSorting() {
  const sort = useRouteQuery<SortField>('sort', 'title')
  const dir = useRouteQuery<Direction>('dir', 'asc')
  const size = useRouteQuery<string, PageSize>('size', '25', {
    transform: { get: (v) => Number(v) as PageSize, set: String },
  })
  return { sort, dir, size }
}

export function useBookFilters() {
  const genre = stringArray<Genre>('genre')
  const language = useRouteQuery<Language | null>('language', null)
  const inStock = nullableBool('inStock')
  const minRating = nullableNumber('minRating')
  const priceMin = nullableNumber('priceMin')
  const priceMax = nullableNumber('priceMax')
  const publishedAfter = useRouteQuery<string | null>('publishedAfter', null)

  function reset(): void {
    genre.value = []
    language.value = null
    inStock.value = null
    minRating.value = null
    priceMin.value = null
    priceMax.value = null
    publishedAfter.value = null
  }

  return {
    genre,
    language,
    inStock,
    minRating,
    priceMin,
    priceMax,
    publishedAfter,
    reset,
  }
}

export function useBooks() {
  const route = useRoute()
  const router = useRouter()

  const { sort, dir, size } = useBookSorting()

  const cursorValue = useRouteQuery<string | null>('cursorValue', null)
  const cursorId = nullableNumber('cursorId')

  const filters = useBookFilters()

  const rows = ref<BookDto[]>([])
  const nextSeed = ref<SeekKey | null>(null)
  const prevSeed = ref<SeekKey | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  let pendingController: AbortController | null = null

  async function fetchData() {
    pendingController?.abort()
    const controller = new AbortController()
    pendingController = controller
    loading.value = true
    error.value = null

    try {
      const response = await searchBooks({
        query: {
          sort: sort.value,
          dir: dir.value,
          size: size.value,
          cursorValue: cursorValue.value ?? undefined,
          cursorId: cursorId.value ?? undefined,
          genre: filters.genre.value.length > 0 ? filters.genre.value : undefined,
          language: filters.language.value ?? undefined,
          inStock: filters.inStock.value ?? undefined,
          minRating: filters.minRating.value ?? undefined,
          priceMin: filters.priceMin.value ?? undefined,
          priceMax: filters.priceMax.value ?? undefined,
          publishedAfter: filters.publishedAfter.value ?? undefined,
        },
        signal: controller.signal,
      })
      if (controller.signal.aborted) return
      if (response.error) {
        console.error('searchBooks returned error', response.error)
        error.value = 'Failed to load books.'
        return
      }
      rows.value = response.data?.content ?? []
      nextSeed.value = response.data?.next ?? null
      prevSeed.value = response.data?.prev ?? null
    } catch (cause) {
      if (controller.signal.aborted) return
      console.error('searchBooks failed', cause)
      error.value = 'Failed to load books.'
    } finally {
      if (pendingController === controller) {
        loading.value = false
        pendingController = null
      }
    }
  }

  // Any sort/size/filter change invalidates the current cursor. `flush: 'sync'`
  // batches the cursor reset into the same router.replace as the trigger.
  watch(
    [
      sort,
      dir,
      size,
      filters.genre,
      filters.language,
      filters.inStock,
      filters.minRating,
      filters.priceMin,
      filters.priceMax,
      filters.publishedAfter,
    ],
    () => {
      cursorValue.value = null
      cursorId.value = null
    },
    { flush: 'sync' },
  )

  function stringifyCursorValue(value: unknown): string {
    if (typeof value === 'string') return value
    if (typeof value === 'number') return String(value)
    return String(value)
  }

  // Cursor steps push so back/forward navigates page-by-page; everything else replaces.
  function pushCursor(seed: SeekKey | null): void {
    void router.push({
      query: {
        ...route.query,
        cursorValue: seed ? stringifyCursorValue(seed.value) : undefined,
        cursorId: seed ? String(seed.id) : undefined,
      },
    })
  }

  function goNext(): void {
    if (nextSeed.value) pushCursor(nextSeed.value)
  }

  const canGoNext = computed(() => nextSeed.value !== null)
  // `prev: null` covers both "no prior" and "prior is the first page"; a URL cursor disambiguates.
  const canGoPrev = computed(() => prevSeed.value !== null || cursorValue.value !== null)

  function goPrev(): void {
    if (canGoPrev.value) pushCursor(prevSeed.value)
  }

  watch(
    () => route.query,
    () => void fetchData(),
    { immediate: true },
  )

  onScopeDispose(() => pendingController?.abort())

  return {
    rows,
    loading,
    error,
    canGoNext,
    canGoPrev,
    goNext,
    goPrev,
  }
}
