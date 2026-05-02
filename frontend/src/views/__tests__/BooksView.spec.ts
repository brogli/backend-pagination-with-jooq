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

function ok(content: BookDto[]): SearchBooksResult {
  return { data: { content, nextCursor: null, prevCursor: null } } as SearchBooksResult
}

function fail(message: string): SearchBooksResult {
  return { error: { title: message } } as SearchBooksResult
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
    vi.mocked(searchBooks).mockResolvedValue(ok([sampleBook]))

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
})
