import { describe, it, expect } from 'vitest'
import { appPath, getUrlForEventType, urlBase64ToUint8Array } from '../pushUtils'

describe('appPath', () => {
  it('prefixes routes with the PWA base path', () => {
    expect(appPath('/checkin')).toBe('/app/checkin')
    expect(appPath('notifications')).toBe('/app/notifications')
    expect(appPath('/')).toBe('/app/')
  })
})

describe('getUrlForEventType', () => {
  it('returns /app/checkin for check-in related events', () => {
    expect(getUrlForEventType('lesson.started')).toBe('/app/checkin')
    expect(getUrlForEventType('lesson.reminder')).toBe('/app/checkin')
    expect(getUrlForEventType('attendance.marked')).toBe('/app/checkin')
  })

  it('returns /app/schedule for schedule events', () => {
    expect(getUrlForEventType('lesson.blocked')).toBe('/app/schedule')
    expect(getUrlForEventType('lesson.cancelled')).toBe('/app/schedule')
    expect(getUrlForEventType('lesson.one_off.created')).toBe('/app/schedule')
    expect(getUrlForEventType('lesson.one_off.cancelled')).toBe('/app/schedule')
  })

  it('returns /app/homework for homework events', () => {
    expect(getUrlForEventType('homework.published')).toBe('/app/homework')
    expect(getUrlForEventType('homework.updated')).toBe('/app/homework')
    expect(getUrlForEventType('homework.weekly_digest')).toBe('/app/homework')
    expect(getUrlForEventType('homework.due_reminder')).toBe('/app/homework')
  })

  it('returns headman pages for request events', () => {
    expect(getUrlForEventType('late_checkin.requested')).toBe('/app/group/late-checkin')
    expect(getUrlForEventType('excuse.requested')).toBe('/app/group/excuses')
  })

  it('returns notifications for unknown event type', () => {
    expect(getUrlForEventType('unknown.event')).toBe('/app/notifications')
    expect(getUrlForEventType('')).toBe('/app/notifications')
  })
})

describe('urlBase64ToUint8Array', () => {
  it('converts a known base64url string to correct Uint8Array', () => {
    // 'AAEC' is base64 for bytes [0, 1, 2].
    const result = urlBase64ToUint8Array('AAEC')
    expect(result).toBeInstanceOf(Uint8Array)
    expect(Array.from(result)).toEqual([0, 1, 2])
  })

  it('handles base64url characters (- and _) correctly', () => {
    // base64 'P/8=' = bytes [63, 255], base64url 'P_8' omits padding.
    const result = urlBase64ToUint8Array('P_8')
    expect(Array.from(result)).toEqual([63, 255])
  })
})
