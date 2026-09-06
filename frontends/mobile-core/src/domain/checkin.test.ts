import { applyCheckinAck, countdownLabel, remainingSeconds, unavailableCommand, unavailableReason } from './checkin'
import type { StudentCheckinAck, StudentToday } from '../api/types'
import { describe, expect, it } from 'vitest'

const today: StudentToday = {
  date: '2026-09-06', timeZone: 'Europe/Moscow', serverNow: '2026-09-06T08:30:00Z', _links: {}, lessons: [{
    schedule: { id: '77', date: '2026-09-06', lessonNumber: 2, startsAt: '11:20:00', endsAt: '12:50:00', status: 'ACTIVE', subject: { id: '31', name: 'РС', type: 'LECTURE' }, room: { current: 'А-401', previous: null, changeState: 'UNCHANGED' } },
    attendance: null, request: null, checkinEligibility: { allowed: true, reason: 'ELIGIBLE', retryAt: null },
  }],
}

describe('geo check-in domain', () => {
  it('uses unavailable discriminator instead of zero coordinates', () => {
    expect(unavailableCommand('TIMEOUT')).toEqual({ geo: { kind: 'UNAVAILABLE', reason: 'TIMEOUT' } })
    expect(unavailableReason({ code: 1, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError)).toBe('PERMISSION_DENIED')
    expect(unavailableReason({ code: 2, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError)).toBe('POSITION_UNAVAILABLE')
    expect(unavailableReason({ code: 3, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError)).toBe('TIMEOUT')
  })

  it('waits for ACK before projecting pending and derives countdown from server time', () => {
    const ack: StudentCheckinAck = { outcome: 'PENDING_CONFIRMATION', lessonId: '77', attendance: null, request: { id: 'r1', status: 'PENDING', origin: 'AUTO_GEO_FAILURE', resolutionReason: null }, retryAt: '2026-09-06T08:35:00Z', serverNow: '2026-09-06T08:30:00Z', _links: {} }
    expect(today.lessons[0]?.request).toBeNull()
    expect(applyCheckinAck(today, ack).lessons[0]?.request?.id).toBe('r1')
    expect(countdownLabel(remainingSeconds(ack.serverNow, ack.retryAt, Date.parse('2026-09-06T08:34:00Z')))).toBe('01:00')
  })

  it('does not restart a countdown when a delayed idempotency replay has an old serverNow', () => {
    const replay: StudentCheckinAck = { outcome: 'PENDING_CONFIRMATION', lessonId: '77', attendance: null, request: { id: 'r1', status: 'PENDING', origin: 'AUTO_GEO_FAILURE', resolutionReason: null }, retryAt: '2026-09-06T08:35:00Z', serverNow: '2026-09-06T08:30:00Z', _links: {} }
    const fresh = { ...today, serverNow: '2026-09-06T08:34:00Z' }
    expect(applyCheckinAck(fresh, replay).serverNow).toBe('2026-09-06T08:34:00Z')
  })
})
