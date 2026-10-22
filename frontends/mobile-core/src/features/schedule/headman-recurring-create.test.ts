import { describe, expect, it } from 'vitest'
import {
  freezeRecurringCreate,
  readRecurringCreate,
  recurringCreateScope,
  submitRecurringCreate,
} from './headman-recurring-create'
const owner = { userId: '1', sessionId: 'a', groupId: 4, semesterId: 1 },
  key = '11111111-1111-4111-8111-111111111111'
const input = {
  assignmentId: 101,
  groupId: 4,
  subjectId: 1,
  semesterId: 1,
  dayOfWeek: 7,
  lessonNumber: 8,
  startTime: '19:40:00',
  endTime: '21:10:00',
  weekType: 'ALL' as const,
  room: 'А-401',
}
describe('durable recurring CREATE', () => {
  it('never writes if durable storage refuses', async () => {
    let writes = 0
    const intent = freezeRecurringCreate(owner, key, input)
    await expect(
      submitRecurringCreate(
        {
          setItem: () => {
            throw new Error('storage refused')
          },
        },
        intent,
        async () => {
          writes++
        },
      ),
    ).rejects.toThrow('storage refused')
    expect(writes).toBe(0)
  })
  it('reload retains exact owner/body/key after a lost reply, independent of edited original objects', async () => {
    const rows = new Map<string, string>()
    const storage = {
      getItem: (s: string) => rows.get(s) ?? null,
      setItem: (s: string, v: string) => {
        rows.set(s, v)
      },
    }
    const original = { ...input },
      intent = freezeRecurringCreate(owner, key, original)
    await expect(
      submitRecurringCreate(storage, intent, async () => {
        throw new TypeError('reply lost')
      }),
    ).rejects.toThrow('reply lost')
    original.room = 'Б-204'
    const reloaded = readRecurringCreate(storage, owner)!
    let submitted: unknown
    await submitRecurringCreate(storage, reloaded, async (body, replayKey) => {
      submitted = { body, replayKey }
    })
    expect(submitted).toEqual({ body: input, replayKey: key })
    expect(readRecurringCreate(storage, { ...owner, sessionId: 'b' })).toBeNull()
    expect(rows.has(recurringCreateScope(owner))).toBe(true)
  })
  it('rejects damaged ownership/intent before replay', () => {
    const storage = {
      getItem: () => JSON.stringify({ owner: { ...owner, groupId: 7 }, key, input }),
    }
    expect(() => readRecurringCreate(storage, owner)).toThrow()
  })
})
