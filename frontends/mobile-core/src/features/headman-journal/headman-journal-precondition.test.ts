import { describe, expect, it } from 'vitest'
import {
  HeadmanJournalApi,
  type HeadmanJournalMarkCondition,
  type HeadmanJournalClearCondition,
} from './headman-journal-client'
const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
describe('journal authoritative mark preconditions', () => {
  it('sends EMPTY/EMPTY and the exact existing opaque revision in JSON and multipart request part', async () => {
    const bodies: unknown[] = []
    const api = new HeadmanJournalApi({
      accessToken: () => null,
      fetcher: async (_input, init) => {
        if (init?.body instanceof FormData)
          bodies.push(JSON.parse(await (init.body.get('request') as Blob).text()))
        else bodies.push(JSON.parse(String(init?.body)))
        return json({ status: 'EXCUSED', lessonId: 30, userId: 2 })
      },
    })
    await api.mark(
      30,
      2,
      { status: 'PRESENT' },
      { expectedStatus: 'EMPTY', expectedRevision: 'EMPTY' },
    )
    await api.mark(
      30,
      2,
      { status: 'EXCUSED', excuseType: 'ILLNESS', file: new File(['fixture'], 'test.txt') },
      { expectedStatus: 'EXCUSED', expectedRevision: 'opaque+/same-status-reason' },
    )
    expect(bodies).toEqual([
      { status: 'PRESENT', expectedStatus: 'EMPTY', expectedRevision: 'EMPTY' },
      {
        status: 'EXCUSED',
        excuseType: 'ILLNESS',
        expectedStatus: 'EXCUSED',
        expectedRevision: 'opaque+/same-status-reason',
      },
    ])
  })
  it('encodes both DELETE preconditions without altering the revision, propagates409', async () => {
    let sent = ''
    const api = new HeadmanJournalApi({
      accessToken: () => null,
      fetcher: async (input) => {
        sent = String(input)
        return json({ detail: 'Changed' }, 409)
      },
    })
    await expect(
      api.clear(30, 2, { expectedStatus: 'EXCUSED', expectedRevision: 'x+/= &' }),
    ).rejects.toMatchObject({ response: { status: 409 } })
    const url = new URL(sent, 'https://fixture.invalid')
    expect(url.searchParams.get('expectedStatus')).toBe('EXCUSED')
    expect(url.searchParams.get('expectedRevision')).toBe('x+/= &')
  })
  it('refuses a malformed paired condition before any writePort delegation', () => {
    let writes = 0
    const api = new HeadmanJournalApi(
      { accessToken: () => null },
      {
        mark: async () => {
          writes++
          throw new Error('unexpected')
        },
        clear: async () => {
          writes++
        },
      },
    )
    expect(() =>
      api.mark(
        30,
        2,
        { status: 'PRESENT' },
        { expectedStatus: 'EMPTY', expectedRevision: 'some-token' },
      ),
    ).toThrow(TypeError)
    expect(() =>
      api.mark(30, 2, { status: 'PRESENT' }, {
        expectedStatus: 'EXCUSED',
      } as HeadmanJournalMarkCondition),
    ).toThrow(TypeError)
    expect(() =>
      api.clear(30, 2, {
        expectedStatus: 'EMPTY',
        expectedRevision: 'EMPTY',
      } as unknown as HeadmanJournalClearCondition),
    ).toThrow(TypeError)
    expect(writes).toBe(0)
  })
  it('keeps the exact approved ticket after clear, preserves row write denial and opaque token', async () => {
    const api = new HeadmanJournalApi({
      accessToken: () => null,
      fetcher: async () =>
        json({
          lessonId: 30,
          editable: true,
          lessonStatus: 'ACTIVE',
          entries: [
            {
              userId: 2,
              displayName: 'Мария',
              status: null,
              markRevision: null,
              editable: false,
              approvedTicket: { kind: 'EXCUSE', id: 'actual-ticket-id', status: 'APPROVED' },
            },
            {
              userId: 3,
              displayName: 'Анна',
              status: 'EXCUSED',
              markRevision: 'server-token',
              approvedTicket: null,
            },
          ],
        }),
    })
    const report = await api.getLessonAttendance(30)
    expect(report.entries.find((entry) => entry.userId === 2)).toMatchObject({
      status: 'EMPTY',
      editable: false,
      approvedTicket: { id: 'actual-ticket-id', status: 'APPROVED' },
    })
    expect(report.entries.find((entry) => entry.userId === 3)?.markRevision).toBe('server-token')
  })
})
