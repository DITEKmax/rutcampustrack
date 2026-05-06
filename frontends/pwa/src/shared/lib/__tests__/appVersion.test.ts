import { describe, expect, it } from 'vitest'
import { compareVersions, isUpdateRequiredByPolicy, isVersionOlder } from '../appVersion'

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

  it('forces exact latest match for commit based versions', () => {
    expect(isUpdateRequiredByPolicy('1-abc123', {
      latest: '1-def456',
      minimumSupported: '1-def456',
      force: true,
    })).toBe(true)
    expect(isUpdateRequiredByPolicy('1-def456', {
      latest: '1-def456',
      minimumSupported: '1-def456',
      force: true,
    })).toBe(false)
  })

  it('does not apply minimum version when force mode already matches latest exactly', () => {
    expect(isUpdateRequiredByPolicy('1-def456', {
      latest: '1-def456',
      minimumSupported: '2.0.0',
      force: true,
    })).toBe(false)
  })

  it('still supports minimum semantic version without force mode', () => {
    expect(isUpdateRequiredByPolicy('1.2.3', {
      latest: '1.2.5',
      minimumSupported: '1.2.4',
      force: false,
    })).toBe(true)
    expect(isUpdateRequiredByPolicy('1.2.4', {
      latest: '1.2.5',
      minimumSupported: '1.2.4',
      force: false,
    })).toBe(false)
  })
})
