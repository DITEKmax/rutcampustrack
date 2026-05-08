import { describe, expect, it } from 'vitest'
import {
  getStartParamFromLaunchParams,
  getStartParamFromSearch,
  resolveStartParamRoute,
} from '../launchRoutes'

describe('resolveStartParamRoute', () => {
  it('opens check-in route from lesson start params', () => {
    expect(resolveStartParamRoute('checkin:123')).toBe('/checkin/123')
    expect(resolveStartParamRoute('lesson_456')).toBe('/checkin/456')
    expect(resolveStartParamRoute('lesson-789')).toBe('/checkin/789')
  })

  it('opens only known direct routes', () => {
    expect(resolveStartParamRoute('schedule')).toBe('/schedule')
    expect(resolveStartParamRoute('group')).toBe('/group')
    expect(resolveStartParamRoute('group_students')).toBe('/group/students')
    expect(resolveStartParamRoute('group_subjects')).toBe('/group/subjects')
    expect(resolveStartParamRoute('group_journal')).toBe('/group/journal')
    expect(resolveStartParamRoute('group_stats')).toBe('/group/stats')
    expect(resolveStartParamRoute('group_excuses')).toBe('/group/excuses')
    expect(resolveStartParamRoute('group-late-checkin')).toBe('/group/late-checkin')
    expect(resolveStartParamRoute('late_checkin')).toBe('/late-checkin')
    expect(resolveStartParamRoute('/profile')).toBe('/profile')
    expect(resolveStartParamRoute('https://example.com')).toBeNull()
    expect(resolveStartParamRoute('../admin')).toBeNull()
  })
})

describe('getStartParamFromLaunchParams', () => {
  it('prefers top-level tgWebAppStartParam', () => {
    expect(
      getStartParamFromLaunchParams({
        tgWebAppStartParam: 'schedule',
        tgWebAppData: { startParam: 'homework' },
      }),
    ).toBe('schedule')
  })

  it('falls back to init data startParam', () => {
    expect(
      getStartParamFromLaunchParams({
        tgWebAppData: { startParam: 'checkin:1' },
      }),
    ).toBe('checkin:1')
  })
})

describe('getStartParamFromSearch', () => {
  it('reads Telegram and dev query aliases', () => {
    expect(getStartParamFromSearch('?tgWebAppStartParam=homework')).toBe('homework')
    expect(getStartParamFromSearch('?start_param=excuses')).toBe('excuses')
    expect(getStartParamFromSearch('?startapp=checkin%3A5')).toBe('checkin:5')
  })
})
