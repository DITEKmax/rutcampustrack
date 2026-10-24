import { describe, expect, it } from 'vitest'
import {
  PermissionConfirmationFlow,
  type PermissionConfirmationContext,
} from './permission-confirmation'
import type { HeadmanPermissionOption } from './headman-group-client'
import { HeadmanGroupApi, HeadmanGroupApiError } from './headman-group-client'

const catalog: readonly HeadmanPermissionOption[] = [
  { code: 'MARK_ATTENDANCE', label: 'Посещаемость' },
  { code: 'VIEW_STATS', label: 'Статистика' },
  { code: 'MANAGE_HOMEWORK', label: 'ДЗ' },
]
const context: PermissionConfirmationContext = {
  ownerRevision: 1,
  actorUserId: 'headman-1',
  groupId: 4,
  memberId: 2,
  memberName: 'Синтетический студент',
  assistantId: 11,
  helperIdentity: 'assistant11:active',
  before: ['MARK_ATTENDANCE', 'VIEW_STATS'],
  after: ['MARK_ATTENDANCE', 'MANAGE_HOMEWORK'],
}

describe('frozen permission confirmation sequence', () => {
  it('asks revoke then grant and only consumes the same exact update after both', () => {
    const flow = new PermissionConfirmationFlow()
    const first = flow.start(context, catalog)!
    expect(first.step).toEqual({ kind: 'revoke', rights: [catalog[1]] })
    expect(Object.isFrozen(first.snapshot.after)).toBe(true)
    const second = flow.confirm(context)
    expect(second.kind).toBe('next')
    if (second.kind !== 'next') throw new Error('Expected grant step')
    expect(second.state.step).toEqual({ kind: 'grant', rights: [catalog[2]] })
    expect(second.state.snapshot).toBe(first.snapshot)
    expect(flow.confirm(context)).toEqual({ kind: 'commit', snapshot: first.snapshot })
    expect(flow.confirm(context)).toEqual({ kind: 'invalid' })
  })

  it.each(['grant', 'revoke'] as const)(
    'uses a single confirmation for %s-only updates',
    (kind) => {
      const flow = new PermissionConfirmationFlow()
      const single = {
        ...context,
        after:
          kind === 'grant'
            ? [...context.before, 'MANAGE_HOMEWORK' as const]
            : ['MARK_ATTENDANCE' as const],
      }
      expect(flow.start(single, catalog)?.step.kind).toBe(kind)
      expect(flow.confirm(single).kind).toBe('commit')
    },
  )

  it.each([0, 1])(
    'cancel at step %s consumes nothing and a fresh attempt must restart at revoke',
    (step) => {
      const flow = new PermissionConfirmationFlow()
      flow.start(context, catalog)
      if (step) expect(flow.confirm(context).kind).toBe('next')
      flow.cancel()
      expect(flow.confirm(context).kind).toBe('invalid')
      expect(flow.start(context, catalog)?.step.kind).toBe('revoke')
      expect(flow.confirm(context).kind).toBe('next')
    },
  )

  it.each([
    { ownerRevision: 2 },
    { actorUserId: 'other' },
    { groupId: 5 },
    { memberId: 3 },
    { memberName: 'Переименован' },
    { assistantId: 12 },
    { helperIdentity: 'assistant11:revoked' },
    { before: ['VIEW_STATS' as const] },
    { after: ['MANAGE_HOMEWORK' as const] },
  ])('invalidates the entire two-step approval for context change %j', (change) => {
    const flow = new PermissionConfirmationFlow()
    flow.start(context, catalog)
    expect(flow.confirm(context).kind).toBe('next')
    expect(flow.confirm({ ...context, ...change }).kind).toBe('invalid')
    expect(flow.confirm(context).kind).toBe('invalid')
    expect(flow.start(context, catalog)?.step.kind).toBe('revoke')
  })

  it('rejects an undisclosed right and copies inputs before later mutation', () => {
    const flow = new PermissionConfirmationFlow()
    expect(flow.start(context, catalog.slice(0, 2))).toBeNull()
    const mutable = { ...context, after: [...context.after] }
    const state = flow.start(mutable, catalog)!
    mutable.after.pop()
    expect(state.snapshot.after).toEqual(context.after)
    expect(flow.confirm(mutable).kind).toBe('invalid')
  })
})

describe('conditional assistant permissions transport', () => {
  const assistant = {
    id: 11,
    studentId: 2,
    groupId: 4,
    permissions: ['MANAGE_HOMEWORK'],
    active: true,
  }
  it('submits the frozen before-set and preserves a conflict without replaying the stale grant', async () => {
    const flow = new PermissionConfirmationFlow()
    const draft = {
      ...context,
      before: ['MANAGE_HOMEWORK' as const],
      after: ['MANAGE_HOMEWORK' as const, 'VIEW_STATS' as const],
    }
    flow.start(draft, catalog)
    const approved = flow.confirm(draft)
    if (approved.kind !== 'commit') throw new Error('Expected explicit grant')
    let writes = 0
    const api = new HeadmanGroupApi({
      accessToken: () => null,
      fetcher: async (_path, init) => {
        writes++
        expect(JSON.parse(String(init?.body))).toEqual({
          permissions: approved.snapshot.after,
          expectedPermissions: approved.snapshot.before,
        })
        return Response.json(
          { detail: 'Права уже изменились', code: 'ASSISTANT_PERMISSIONS_CONFLICT' },
          { status: 409 },
        )
      },
    })
    await expect(
      api.updatePermissions(11, approved.snapshot.after, approved.snapshot.before),
    ).rejects.toBeInstanceOf(HeadmanGroupApiError)
    expect(writes).toBe(1)
  })
  it('uses conditional empty PATCH for last-right revoke and retains the inactive historical response', async () => {
    const api = new HeadmanGroupApi({
      accessToken: () => null,
      fetcher: async (_path, init) => {
        expect(init?.method).toBe('PATCH')
        expect(JSON.parse(String(init?.body))).toEqual({
          permissions: [],
          expectedPermissions: ['MANAGE_HOMEWORK'],
        })
        return Response.json({ ...assistant, active: false })
      },
    })
    const result = await api.updatePermissions(11, [], ['MANAGE_HOMEWORK'])
    expect(result.active).toBe(false)
    expect(result.permissions).toEqual(['MANAGE_HOMEWORK'])
  })
  it('preserves the legacy nonempty body and rejects legacy empty changes before transport', async () => {
    let writes = 0
    const api = new HeadmanGroupApi({
      accessToken: () => null,
      fetcher: async (_path, init) => {
        writes++
        expect(JSON.parse(String(init?.body))).toEqual({ permissions: ['VIEW_STATS'] })
        return Response.json({ ...assistant, permissions: ['VIEW_STATS'] })
      },
    })
    await api.updatePermissions(11, ['VIEW_STATS'])
    await expect(api.updatePermissions(11, [])).rejects.toThrow('expected snapshot')
    expect(writes).toBe(1)
  })
})
