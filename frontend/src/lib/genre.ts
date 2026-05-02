import type { Genre } from '@/api/generated/types.gen'

export const GENRES: readonly Genre[] = [
  'Fantasy',
  'SciFi',
  'Mystery',
  'Romance',
  'Thriller',
  'NonFiction',
] as const

export const GENRE_LABEL: Record<Genre, string> = {
  Fantasy: 'Fantasy',
  SciFi: 'Sci-Fi',
  Mystery: 'Mystery',
  Romance: 'Romance',
  Thriller: 'Thriller',
  NonFiction: 'Non-Fiction',
}

export const GENRE_OPTIONS = GENRES.map((genre) => ({
  label: GENRE_LABEL[genre],
  value: genre,
}))
