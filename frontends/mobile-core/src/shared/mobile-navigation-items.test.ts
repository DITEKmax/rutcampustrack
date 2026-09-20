import { describe, expect, it } from 'vitest'
import { createStudentNavigationItems } from './mobile-navigation-items'

describe('student dock Requests entry', () => {
  it('keeps More visibly unavailable until the owner enables its real root', () => {
    const disabled = createStudentNavigationItems().find((item) => item.id === 'more')
    const enabled = createStudentNavigationItems({ moreEnabled: true }).find((item) => item.id === 'more')
    expect(disabled?.disabled).toBe(true)
    expect(enabled?.disabled).toBeUndefined()
  })
})
