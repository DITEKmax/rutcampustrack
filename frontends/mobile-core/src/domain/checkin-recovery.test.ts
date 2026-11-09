import { describe, expect, it, vi } from 'vitest'
import { CheckinBeforeSendCancelled, CheckinCommandRecovery } from './checkin'
import type { StudentCheckinAck, StudentCheckinCommand } from '../api/types'
import { StudentApiError } from '../api/student-client'

const command: StudentCheckinCommand = {
  geo: { kind: 'COORDINATES', latitude: 55.75, longitude: 37.62 },
}
const ack: StudentCheckinAck = {
  outcome: 'PRESENT',
  lessonId: '77',
  attendance: { status: 'PRESENT', source: 'STUDENT_GEO', markedAt: '2026-09-06T08:30:00Z' },
  request: null,
  retryAt: null,
  serverNow: '2026-09-06T08:30:00Z',
  _links: {},
}

describe('check-in command recovery', () => {
  it('freezes coordinates and diagnostics from the first POST when its acknowledgement is lost', async () => {
    const recovery = new CheckinCommandRecovery(() => 'immutable-key')
    const original: StudentCheckinCommand = {
      geo: { kind: 'COORDINATES', latitude: 55, longitude: 37 },
      diagnostics: {
        source: 'BROWSER',
        accuracyMeters: 20,
        sampleCount: 2,
        completionReason: 'QUALITY_REACHED',
        fixTimestamp: '2026-09-06T08:30:00Z',
        receivedAt: '2026-09-06T08:30:01Z',
        acquisitionDurationMs: 1000,
      },
    }
    const sent: StudentCheckinCommand[] = []
    const submit = vi.fn(async (attempt: { command: StudentCheckinCommand }) => {
      sent.push(attempt.command)
      if (sent.length === 1) throw new TypeError('response lost')
      return ack
    })
    await expect(recovery.execute('77', async () => original, submit)).rejects.toThrow(
      'response lost',
    )
    original.diagnostics!.accuracyMeters = 500
    if (original.geo.kind === 'COORDINATES') original.geo.latitude = 56
    const replacement = vi.fn(async () => command)
    await expect(recovery.execute('77', replacement, submit)).resolves.toEqual(ack)
    expect(replacement).not.toHaveBeenCalled()
    expect(sent[1]).toBe(sent[0])
    expect(sent[1]?.diagnostics?.accuracyMeters).toBe(20)
    expect(sent[1]?.geo).toMatchObject({ latitude: 55 })
    expect(Object.isFrozen(sent[1]?.diagnostics)).toBe(true)
  })
  it('replays the exact logical command after a response is lost and single-flights concurrent acquisition', async () => {
    const recovery = new CheckinCommandRecovery(() => 'key-1')
    let acquired = 0
    const sent: Array<{ command: StudentCheckinCommand; key: string }> = []
    let failResponse = true
    const acquire = async () => {
      acquired += 1
      return command
    }
    const send = async (attempt: { command: StudentCheckinCommand; key: string }) => {
      sent.push(attempt)
      if (failResponse) throw new TypeError('response lost after commit')
      return ack
    }

    await expect(
      Promise.all([recovery.execute('77', acquire, send), recovery.execute('77', acquire, send)]),
    ).rejects.toThrow('response lost after commit')
    failResponse = false
    await expect(recovery.execute('77', acquire, send)).resolves.toEqual(ack)

    expect(acquired).toBe(1)
    expect(sent).toEqual([
      { lessonId: '77', command, key: 'key-1' },
      { lessonId: '77', command, key: 'key-1' },
    ])
  })

  it('releases a command after an ACK or a definitive 4xx response', async () => {
    const keys = ['key-1', 'key-2', 'key-3']
    const recovery = new CheckinCommandRecovery(() => keys.shift() ?? 'unexpected')
    const acquired: string[] = []
    const acquire = async () =>
      ({
        ...command,
        geo: { ...command.geo, latitude: acquired.push('acquired') },
      }) as StudentCheckinCommand

    await expect(recovery.execute('77', acquire, async () => ack)).resolves.toEqual(ack)
    await expect(
      recovery.execute('77', acquire, async () => {
        throw new StudentApiError(new Response(null, { status: 429 }), null)
      }),
    ).rejects.toMatchObject({ response: expect.objectContaining({ status: 429 }) })
    await expect(recovery.execute('77', acquire, async () => ack)).resolves.toEqual(ack)

    expect(acquired).toHaveLength(3)
  })

  it('keeps a command after a 5xx response', async () => {
    const recovery = new CheckinCommandRecovery(() => 'key-1')
    let acquired = 0
    const acquire = async () => {
      acquired += 1
      return command
    }
    await expect(
      recovery.execute('77', acquire, async () => {
        throw new StudentApiError(new Response(null, { status: 503 }), null)
      }),
    ).rejects.toMatchObject({ response: expect.objectContaining({ status: 503 }) })
    await expect(recovery.execute('77', acquire, async () => ack)).resolves.toEqual(ack)
    expect(acquired).toBe(1)
  })

  it('discards unsent geolocation but retains a previously ambiguous command across route cancellation', async () => {
    const recovery = new CheckinCommandRecovery(vi.fn().mockReturnValueOnce('discard').mockReturnValueOnce('keep'),
    )
    const acquire = vi.fn(async () => command)
    const cancel = async () => { throw new CheckinBeforeSendCancelled() }
    const failAfterSend = async () => { throw new TypeError('response lost') }
    await expect(recovery.execute('77', acquire, cancel)).rejects.toBeInstanceOf(CheckinBeforeSendCancelled,
    )
    await expect(recovery.execute('77', acquire, failAfterSend)).rejects.toThrow('response lost')
    await expect(recovery.execute('77', acquire, cancel)).rejects.toBeInstanceOf(CheckinBeforeSendCancelled,
    )
    const send = vi.fn(async () => ack)
    await expect(recovery.execute('77', acquire, send)).resolves.toEqual(ack)
    expect(acquire).toHaveBeenCalledTimes(2)
    expect(send).toHaveBeenCalledExactlyOnceWith({ lessonId: '77', command, key: 'keep' })
  })
})
