import { describe, expect, it } from 'vitest'
import { createHeadmanNavigationItems, createStudentNavigationItems } from './mobile-navigation-items'

describe('student dock Requests entry', () => {
  it('keeps More visibly unavailable until the owner enables its real root', () => {
    const disabled = createStudentNavigationItems().find((item) => item.id === 'more')
    const enabled = createStudentNavigationItems({ moreEnabled: true }).find((item) => item.id === 'more')
    expect(disabled?.disabled).toBe(true)
    expect(enabled?.disabled).toBeUndefined()
  })
})

describe('headman dock', () => {
  it('keeps five roots and leaves the map entry in More', () => {
    const items = createHeadmanNavigationItems()
    expect(items.map(({ id }) => id)).toEqual([
      'headman-today', 'headman-attendance', 'headman-requests', 'headman-more', 'profile',
    ])
    expect(items.map(({ label }) => label)).toEqual(['Сегодня', 'Учёт', 'Заявки', 'Ещё', 'Профиль'])
  })
})
