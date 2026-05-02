import { describe, expect, it } from 'vitest'
import { compareVersions, isVersionOlder } from '../appVersion'

describe('appVersion helpers', () => {
  it('compares equal semantic versions', () => {
    expect(compareVersions('1.2.0', '1.2')).toBe(0)
  })

  it('detects older versions', () => {
    expect(isVersionOlder('1.2.3', '1.2.4')).toBe(true)
    expect(isVersionOlder('1.2.3', '1.3.0')).toBe(true)
  })

  it('does not flag newer versions', () => {
    expect(isVersionOlder('1.3.0', '1.2.9')).toBe(false)
    expect(isVersionOlder('1.2.3', '1.2.3')).toBe(false)
  })
})
