import { describe, expect, it } from 'vitest'
import { journalScopeCurrent, type JournalMutationScope } from './headman-journal-operation'

describe('journal destructive confirmation and late ACK ownership', () => {
  const scope: JournalMutationScope = { api: {}, groupId: 4, lessonId: 30, generation: 1 }
  it('allows the unchanged captured account/group/lesson generation', () => {
    expect(journalScopeCurrent(scope, { ...scope })).toBe(true)
  })
  it.each([
    ['account', { ...scope, api: {} }],
    ['group', { ...scope, groupId: 5 }],
    ['lesson', { ...scope, lessonId: 31 }],
    ['rights or offline cycle', { ...scope, generation: 2 }],
    ['disconnected owner', null],
  ])('rejects an old confirmation and ACK after changing %s', (_label, next) => {
    expect(journalScopeCurrent(scope, next)).toBe(false)
  })
  it('does not publish a late destructive ACK into the newly selected lesson', async () => {
    let current: JournalMutationScope | null = { ...scope }
    let finish!: () => void
    const ack = new Promise<void>((resolve) => { finish = resolve })
    let visibleMark = 'EXCUSED'
    const run = async () => {
      await ack
      if (journalScopeCurrent(scope, current)) visibleMark = 'EMPTY'
    }
    const pending = run()
    current = { ...scope, lessonId: 31, generation: 2 }
    finish()
    await pending
    expect(visibleMark).toBe('EXCUSED')
  })
})
