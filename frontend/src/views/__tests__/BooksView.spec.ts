import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import BooksView from '../BooksView.vue'
import { mountWithApp } from '@/test/mountWithApp'
import { searchBooks } from '@/api/generated/sdk.gen'
import type { BookDto } from '@/api/generated/types.gen'

type SearchBooks = typeof searchBooks
type SearchBooksResult = Awaited<ReturnType<SearchBooks>>

vi.mock('@/api/generated/sdk.gen', () => ({
  searchBooks: vi.fn<SearchBooks>(),
}))

function fail(message: string, status = 500): SearchBooksResult {
  // why: the generated result type is a union, the test only needs the error branch plus status
  return { error: { title: message }, response: { status } as Response } as SearchBooksResult
}

function page(content: BookDto[]): SearchBooksResult {
  // why: the generated result type is a union, the test only needs the success branch
  return { data: { content, nextCursor: null, prevCursor: null } } as SearchBooksResult
}

const sampleBook: BookDto = {
  id: 1,
  title: 'Loaded Book',
  author: 'Author A',
  genre: 'Fantasy',
  language: 'English',
  inStock: true,
  rating: 4.5,
  price: 9.99,
  publishedAt: '2020-01-01',
}

describe('BooksView', () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    vi.mocked(searchBooks).mockReset()
    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    consoleErrorSpy.mockRestore()
  })

  it('renders rows returned by the API', async () => {
    vi.mocked(searchBooks).mockResolvedValue(page([sampleBook]))

    const { wrapper } = await mountWithApp(BooksView)
    await flushPromises()

    expect(wrapper.text()).toContain('Loaded Book')
  })

  it('shows the error banner when the API returns an error', async () => {
    vi.mocked(searchBooks).mockResolvedValue(fail('Bad request'))

    const { wrapper } = await mountWithApp(BooksView)
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('Failed to load books.')
  })

  it('shows the error banner when the API call rejects', async () => {
    vi.mocked(searchBooks).mockRejectedValue(new Error('network down'))

    const { wrapper } = await mountWithApp(BooksView)
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('Failed to load books.')
  })

  it('drops a cursor the server rejects with 400 and reloads page 1', async () => {
    vi.mocked(searchBooks)
      .mockResolvedValueOnce(fail('cursor is malformed', 400))
      .mockResolvedValueOnce(page([sampleBook]))

    const { wrapper, router } = await mountWithApp(
      BooksView,
      {},
      '/?sort=title&dir=asc&cursor=stale',
    )
    await flushPromises()

    expect(searchBooks).toHaveBeenCalledTimes(2)
    expect(vi.mocked(searchBooks).mock.calls[1]?.[0]?.query?.cursor).toBeUndefined()
    expect(router.currentRoute.value.query.cursor).toBeUndefined()
    expect(router.currentRoute.value.query.sort).toBe('title')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Loaded Book')
  })

  it('drops a cursor that yields an empty page and reloads page 1', async () => {
    vi.mocked(searchBooks)
      .mockResolvedValueOnce(page([]))
      .mockResolvedValueOnce(page([sampleBook]))

    const { router } = await mountWithApp(BooksView, {}, '/?cursor=gone')
    await flushPromises()

    expect(searchBooks).toHaveBeenCalledTimes(2)
    expect(router.currentRoute.value.query.cursor).toBeUndefined()
  })

  it('keeps the error banner for a 400 without a cursor', async () => {
    vi.mocked(searchBooks).mockResolvedValue(fail('priceMin must be <= priceMax', 400))

    const { wrapper } = await mountWithApp(BooksView)
    await flushPromises()

    expect(searchBooks).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[role="alert"]').text()).toContain('Failed to load books.')
  })

  it('shows an empty first page without retrying', async () => {
    vi.mocked(searchBooks).mockResolvedValue(page([]))

    await mountWithApp(BooksView)
    await flushPromises()

    expect(searchBooks).toHaveBeenCalledTimes(1)
  })
})
