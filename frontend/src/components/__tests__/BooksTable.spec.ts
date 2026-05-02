import { describe, expect, it } from 'vitest'
import type { VueWrapper } from '@vue/test-utils'
import BooksTable from '../BooksTable.vue'
import { mountWithApp } from '@/test/mountWithApp'
import type { BookDto } from '@/api/generated/types.gen'

const sampleBook: BookDto = {
  id: 1,
  title: 'Book One',
  author: 'Author A',
  genre: 'Fantasy',
  language: 'English',
  inStock: true,
  rating: 4.5,
  price: 9.99,
  publishedAt: '2020-01-01',
}

function buttonByLabel(wrapper: VueWrapper, label: string) {
  const button = wrapper.findAll('button').find((b) => b.text() === label)
  if (!button) {
    throw new Error(`Button "${label}" not found`)
  }
  return button
}

describe('BooksTable', () => {
  it('renders one row per book', async () => {
    const { wrapper } = await mountWithApp(BooksTable, {
      props: {
        rows: [sampleBook, { ...sampleBook, id: 2, title: 'Book Two' }],
        loading: false,
        hasNext: true,
        hasPrev: false,
      },
    })

    expect(wrapper.text()).toContain('Book One')
    expect(wrapper.text()).toContain('Book Two')
  })

  it('emits next when Next is clicked', async () => {
    const { wrapper } = await mountWithApp(BooksTable, {
      props: { rows: [], loading: false, hasNext: true, hasPrev: true },
    })

    await buttonByLabel(wrapper, 'Next').trigger('click')

    expect(wrapper.emitted('next')).toHaveLength(1)
  })

  it('disables Previous when hasPrev is false', async () => {
    const { wrapper } = await mountWithApp(BooksTable, {
      props: { rows: [], loading: false, hasNext: true, hasPrev: false },
    })

    expect(buttonByLabel(wrapper, 'Previous').attributes('disabled')).toBeDefined()
  })

  it('disables both buttons while loading', async () => {
    const { wrapper } = await mountWithApp(BooksTable, {
      props: { rows: [], loading: true, hasNext: true, hasPrev: true },
    })

    expect(buttonByLabel(wrapper, 'Next').attributes('disabled')).toBeDefined()
    expect(buttonByLabel(wrapper, 'Previous').attributes('disabled')).toBeDefined()
  })
})
