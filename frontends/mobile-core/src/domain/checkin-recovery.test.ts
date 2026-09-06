import { describe, expect, it } from 'vitest'
import { CheckinCommandRecovery } from './checkin'
import type { StudentCheckinAck, StudentCheckinCommand } from '../api/types'
import { StudentApiError } from '../api/student-client'

const command: StudentCheckinCommand = { geo: { kind: 'COORDINATES', latitude: 55.75, longitude: 37.62 } }
const ack: StudentCheckinAck = {
  outcome: 'PRESENT', lessonId: '77', attendance: { status: 'PRESENT', source: 'STUDENT_GEO', markedAt: '2026-09-06T08:30:00Z' }, request: null, retryAt: null, serverNow: '2026-09-06T08:30:00Z', _links: {},
}

describe('check-in command recovery', () => {
  it('replays the exact logical command after a response is lost and single-flights concurrent acquisition', async () => {
    const recovery = new CheckinCommandRecovery(() => 'key-1')
    let acquired = 0
    const sent: Array<{ command: StudentCheckinCommand; key: string }> = []
    let failResponse = true
    const acquire = async () => { acquired += 1; return command }
    const send = async (attempt: { command: StudentCheckinCommand; key: string }) => {
      sent.push(attempt)
      if (failResponse) throw new TypeError('response lost after commit')
      return ack
    }

    await expect(Promise.all([
      recovery.execute('77', acquire, send),
      recovery.execute('77', acquire, send),
    ])).rejects.toThrow('response lost after commit')
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
    const acquire = async () => ({ ...command, geo: { ...command.geo, latitude: acquired.push('acquired') } } as StudentCheckinCommand)

    await expect(recovery.execute('77', acquire, async () => ack)).resolves.toEqual(ack)
    await expect(recovery.execute('77', acquire, async () => { throw new StudentApiError(new Response(null, { status: 429 }), null) })).rejects.toMatchObject({ response: expect.objectContaining({ status: 429 }) })
    await expect(recovery.execute('77', acquire, async () => ack)).resolves.toEqual(ack)

    expect(acquired).toHaveLength(3)
  })

  it('keeps a command after a 5xx response', async () => {
    const recovery = new CheckinCommandRecovery(() => 'key-1')
    let acquired = 0
    const acquire = async () => { acquired += 1; return command }
    await expect(recovery.execute('77', acquire, async () => { throw new StudentApiError(new Response(null, { status: 503 }), null) })).rejects.toMatchObject({ response: expect.objectContaining({ status: 503 }) })
    await expect(recovery.execute('77', acquire, async () => ack)).resolves.toEqual(ack)
    expect(acquired).toBe(1)
  })
})
