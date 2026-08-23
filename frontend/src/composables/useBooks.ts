import { computed, onScopeDispose, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRouteQuery } from '@vueuse/router'
import { searchBooks } from '@/api/generated/sdk.gen'
import type { BookDto, Direction, Genre, Language, SortField } from '@/api/generated/types.gen'

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

  const cursor = useRouteQuery<string | null>('cursor', null)

  const filters = useBookFilters()

  const rows = ref<BookDto[]>([])
  const nextCursor = ref<string | null>(null)
  const prevCursor = ref<string | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const canGoNext = computed(() => nextCursor.value !== null)
  const canGoPrev = computed(() => prevCursor.value !== null)

  let pendingController: AbortController | null = null

  async function fetchData() {
    pendingController?.abort()
    const controller = new AbortController()
    pendingController = controller
    loading.value = true
    error.value = null

    // Captured before the request so recovery below is judged against what this request
    // actually sent, not whatever `cursor.value` has drifted to by the time the response lands.
    const sentCursor = cursor.value

    try {
      const response = await searchBooks({
        query: {
          sort: sort.value,
          dir: dir.value,
          size: size.value,
          cursor: sentCursor ?? undefined,
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
      // `cursor.value` can move on before this response lands (e.g. the user paged again
      // while this request was in flight, ahead of the abort actually landing). Only treat
      // the cursor as stale if it's still the one this response answers, so an outdated
      // response never clobbers a newer cursor.
      const staleCursor = sentCursor !== null && cursor.value === sentCursor
      if (response.error) {
        if (staleCursor && response.response.status === 400) {
          console.error('searchBooks returned error, dropping cursor', response.error)
          dropCursor()
          return
        }
        console.error('searchBooks returned error', response.error)
        error.value = 'Failed to load books.'
        return
      }
      const content = response.data?.content ?? []
      if (staleCursor && content.length === 0) {
        dropCursor()
        return
      }
      rows.value = content
      nextCursor.value = response.data?.nextCursor ?? null
      prevCursor.value = response.data?.prevCursor ?? null
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
      cursor.value = null
    },
    { flush: 'sync' },
  )

  // Cursor steps push so back/forward navigates page-by-page; everything else replaces.
  function pushCursor(value: string): void {
    void router.push({
      query: {
        ...route.query,
        cursor: value,
      },
    })
  }

  // A cursor the server rejects (400) or that lands on an empty page is stale: rows were
  // deleted, filters changed out of band, or the cursor format moved on. Clearing it (same
  // idiom as the sort/filter watcher above) lets the route watcher reload page 1 with the same
  // sort and filters. `useRouteQuery`'s default replace mode keeps the broken URL out of
  // history.
  function dropCursor(): void {
    cursor.value = null
  }

  function goNext(): void {
    if (nextCursor.value !== null) {
      pushCursor(nextCursor.value)
    }
  }

  function goPrev(): void {
    if (prevCursor.value !== null) {
      pushCursor(prevCursor.value)
    }
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
