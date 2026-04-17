import { describe, it, expect } from 'vitest'
import { getUrlForEventType, urlBase64ToUint8Array } from '../pushUtils'

describe('getUrlForEventType', () => {
  it('returns /checkin for lesson.started', () => {
    expect(getUrlForEventType('lesson.started')).toBe('/checkin')
  })

  it('returns /schedule for lesson.cancelled', () => {
    expect(getUrlForEventType('lesson.cancelled')).toBe('/schedule')
  })

  it('returns /homework for homework.published', () => {
    expect(getUrlForEventType('homework.published')).toBe('/homework')
  })

  it('returns /homework for homework.updated', () => {
    expect(getUrlForEventType('homework.updated')).toBe('/homework')
  })

  it('returns / for unknown event type', () => {
    expect(getUrlForEventType('unknown.event')).toBe('/')
  })

  it('returns / for empty string', () => {
    expect(getUrlForEventType('')).toBe('/')
  })
})

describe('urlBase64ToUint8Array', () => {
  it('converts a known base64url string to correct Uint8Array', () => {
    // 'AAEC' is base64 for bytes [0, 1, 2]
    const result = urlBase64ToUint8Array('AAEC')
    expect(result).toBeInstanceOf(Uint8Array)
    expect(Array.from(result)).toEqual([0, 1, 2])
  })

  it('handles base64url characters (- and _) correctly', () => {
    // '+' in standard base64 = '-' in base64url
    // '/' in standard base64 = '_' in base64url
    // base64 'P/8=' = bytes [63, 255]
    // base64url 'P_8' (no padding)
    const result = urlBase64ToUint8Array('P_8')
    expect(Array.from(result)).toEqual([63, 255])
  })
})
