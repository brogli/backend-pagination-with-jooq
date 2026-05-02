import type { Language } from '@/api/generated/types.gen'

export const LANGUAGES: readonly Language[] = [
  'English',
  'German',
  'French',
  'Spanish',
  'Japanese',
] as const

export const LANGUAGE_OPTIONS = LANGUAGES.map((language) => ({
  label: language,
  value: language,
}))
