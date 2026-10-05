import { test, expect } from '@playwright/test'

const PAGE_SIZE = 25

test('pages forward and back, then filters by stock', async ({ page }) => {
  const rows = page.locator('tbody tr')
  const firstTitle = rows.first().getByRole('cell').first()
  const previous = page.getByRole('button', { name: 'Previous' })
  const next = page.getByRole('button', { name: 'Next' })

  await page.goto('/')
  await expect(rows).toHaveCount(PAGE_SIZE)
  await expect(previous).toBeDisabled()
  const pageOneTitle = await firstTitle.innerText()

  await next.click()
  await expect(page).toHaveURL(/cursor=/)
  await expect(firstTitle).not.toHaveText(pageOneTitle)
  await expect(rows).toHaveCount(PAGE_SIZE)

  await previous.click()
  await expect(firstTitle).toHaveText(pageOneTitle)

  const stockFilter = page.locator('label', { hasText: /^Stock$/ }).locator('..')
  await stockFilter.getByRole('combobox').click()
  await page.getByRole('option', { name: 'In stock', exact: true }).click()
  await expect(page).toHaveURL(/inStock=true/)
  await expect(rows.locator('td:last-child')).toHaveText(Array<string>(PAGE_SIZE).fill('Yes'))
})
