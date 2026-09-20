import { describe, expect, it } from 'vitest'
import { createMobileNavigationStack, nestedRoute, rootRoute } from './navigation'

describe('Requests navigation stack', () => {
  it('keeps More as the root and returns form → type → inbox → More', () => {
    const navigation = createMobileNavigationStack(rootRoute('more'))
    navigation.push(nestedRoute('more', 'more/requests', 'overview'))
    navigation.push(nestedRoute('more', 'more/requests/type', 'detail'))
    navigation.push(nestedRoute('more', 'more/requests/excuse', 'editor'))

    expect(navigation.current.id).toBe('more/requests/excuse')
    expect(navigation.back()?.id).toBe('more/requests/type')
    expect(navigation.back()?.id).toBe('more/requests')
    expect(navigation.back()?.id).toBe('more')
    expect(navigation.back()).toBeNull()
  })
})
