import { describe, expect, it } from 'vitest'
import {
  AdminGroupDeletionUnknownError,
  AdminGroupsClient,
  type AdminGroupDeletionPhase,
} from './admin-groups-client'

const groupId = '9007199254740993'
const previewDigest = 'a'.repeat(64)
const password = ['synthetic', 'confirmation'].join(' ')
const counts = () => ({
  accountsAffected: 3,
  memberships: 3,
  headmanAssignments: 1,
  assistantAssignments: 0,
  academicRows: 7,
  scheduleRows: 2,
  attendanceRows: 0,
  authGrants: 4,
  'academic.homework': 0,
})
function operation(operationId: string, phase: AdminGroupDeletionPhase = 'COMPLETED') {
  return {
    operationId,
    groupId,
    phase,
    retryable: phase !== 'COMPLETED' && phase !== 'CANCELLED',
    counts: counts(),
    failure: phase === 'CANCELLED' ? 'PREPARATION_CANCELLED' : null,
  }
}
function input(operationId = crypto.randomUUID()) {
  return { operationId, password, previewDigest }
}

describe('final group deletion strict domain contract', () => {
  it('preserves opaque group IDs beyond Number precision and rejects invalid preview consequences', async () => {
    let patch: Record<string, unknown> = {}
    const client = new AdminGroupsClient({
      accessToken: () => null,
      fetcher: async (path, init) => {
        expect(String(path)).toBe(`/api/academic/groups/${groupId}/deletion-preview`)
        expect(init?.cache).toBe('no-store')
        return Response.json({
          groupId,
          groupName: 'Группа',
          previewDigest,
          counts: counts(),
          ...patch,
        })
      },
    })
    await expect(client.previewGroupDeletion(groupId)).resolves.toEqual({
      groupId,
      groupName: 'Группа',
      previewDigest,
      counts: counts(),
    })
    for (const malformed of [
      { groupId: 9007199254740992 },
      { groupId: 'foreign-group' },
      { previewDigest: 'unknown' },
      { counts: { ...counts(), authGrants: undefined } },
      { counts: { ...counts(), attendanceRows: -1 } },
      { counts: { ...counts(), academicRows: 1.5 } },
      { counts: { ...counts(), authGrants: Number.MAX_SAFE_INTEGER + 1 } },
      { counts: { ...counts(), memberships: '3' } },
      { counts: { ...counts(), 'schedule.extra': -1 } },
    ]) {
      patch = malformed
      await expect(client.previewGroupDeletion(groupId)).rejects.toThrow()
    }
  })

  it('admits all explicit saved phases and rejects unknown, contradictory or foreign receipts', async () => {
    const key = crypto.randomUUID()
    let saved: Record<string, unknown> = operation(key)
    const client = new AdminGroupsClient({
      accessToken: () => 'current-owner',
      fetcher: async (path, init) => {
        expect(String(path)).toBe(`/api/academic/group-deletions/${key}`)
        expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer current-owner')
        expect(init?.cache).toBe('no-store')
        return Response.json(saved)
      },
    })
    for (const phase of [
      'PREPARING',
      'COMMITTING',
      'COMPLETED',
      'RELEASING',
      'CANCELLED',
    ] as const) {
      saved = operation(key, phase)
      await expect(client.getGroupDeletion(key, groupId)).resolves.toEqual(saved)
    }
    for (const patch of [
      { phase: 'DONE' },
      { retryable: true },
      { retryable: 'false' },
      { failure: undefined },
      { failure: {} },
      { counts: {} },
      { groupId: 'foreign-group' },
      { operationId: crypto.randomUUID() },
    ]) {
      saved = { ...operation(key), ...patch }
      await expect(client.getGroupDeletion(key, groupId)).rejects.toThrow()
    }
  })
})

describe('password-bearing group deletion admission and recovery', () => {
  it.each<AdminGroupDeletionPhase>([
    'COMPLETED',
    'PREPARING',
    'COMMITTING',
    'RELEASING',
    'CANCELLED',
  ])(
    'sends one confirmation with a header-only key and preserves its %s receipt',
    async (phase) => {
      const intent = input()
      const client = new AdminGroupsClient({
        accessToken: () => 'owner-token',
        fetcher: async (path, init) => {
          expect(String(path)).toBe(`/api/academic/groups/${groupId}/deletions`)
          expect(init?.method).toBe('POST')
          const headers = new Headers(init?.headers)
          expect(headers.get('Idempotency-Key')).toBe(intent.operationId)
          expect(headers.get('Authorization')).toBe('Bearer owner-token')
          expect(JSON.parse(String(init?.body))).toEqual({ password, previewDigest })
          return Response.json(operation(intent.operationId, phase), {
            status: phase === 'COMPLETED' ? 200 : 202,
          })
        },
      })
      await expect(client.confirmGroupDeletion(groupId, intent)).resolves.toEqual(
        operation(intent.operationId, phase),
      )
    },
  )

  it('recovers a lost acceptance response by original-key GET without a second password command', async () => {
    const intent = input()
    const saved = operation(intent.operationId)
    let erasedGroups = 0
    let recorded: typeof saved | null = null
    const client = new AdminGroupsClient({
      accessToken: () => 'owner-session',
      fetcher: async (path, init) => {
        if (init?.method === 'POST') {
          erasedGroups++
          recorded = saved
          throw new TypeError(password)
        }
        expect(String(path)).toBe(`/api/academic/group-deletions/${intent.operationId}`)
        return Response.json(recorded)
      },
    })
    let unknown: unknown
    try {
      await client.confirmGroupDeletion(groupId, intent)
    } catch (cause) {
      unknown = cause
    }
    expect(unknown).toBeInstanceOf(AdminGroupDeletionUnknownError)
    expect(unknown).toMatchObject({
      state: 'UNKNOWN',
      operationId: intent.operationId,
      groupId,
      status: null,
    })
    expect(String(unknown)).not.toContain(password)
    expect(JSON.stringify(unknown)).not.toContain(password)
    await expect(client.getGroupDeletion(intent.operationId, groupId)).resolves.toEqual(saved)
    expect(erasedGroups).toBe(1)
  })

  it('retains the original key for ambiguous HTTP denials without echoing a password or retrying POST', async () => {
    for (const status of [400, 401, 403, 404, 409, 429]) {
      const intent = input()
      let token = 'expired-owner'
      let erasedGroups = 0
      const client = new AdminGroupsClient({
        accessToken: () => token,
        onUnauthorized: async () => {
          token = 'refreshed-owner'
        },
        fetcher: async (_path, init) => {
          if (new Headers(init?.headers).get('Authorization') === 'Bearer refreshed-owner') {
            erasedGroups++
            return Response.json(operation(intent.operationId))
          }
          return Response.json(
            { detail: password, title: password, extras: { password } },
            { status },
          )
        },
      })
      let failure: unknown
      try {
        await client.confirmGroupDeletion(groupId, intent)
      } catch (cause) {
        failure = cause
      }
      expect(failure).toBeInstanceOf(AdminGroupDeletionUnknownError)
      const error = failure as AdminGroupDeletionUnknownError
      expect(error).toMatchObject({
        state: 'UNKNOWN',
        operationId: intent.operationId,
        groupId,
        status,
      })
      expect(error).not.toHaveProperty('response')
      expect(error).not.toHaveProperty('problem')
      expect(String(error)).not.toContain(password)
      expect(JSON.stringify(error)).not.toContain(password)
      expect(erasedGroups).toBe(0)
    }
  })

  it('retains an accepted original operation through late403 and temporarily missing status', async () => {
    const intent = input()
    const accepted = operation(intent.operationId, 'COMMITTING')
    let recorded: typeof accepted | null = null
    let statusVisible = false
    const client = new AdminGroupsClient({
      accessToken: () => 'same-actor',
      fetcher: async (path, init) => {
        if (init?.method === 'POST') {
          recorded = accepted
          // Durable accept succeeded before the live authority check denied the response.
          return Response.json({ detail: password }, { status: 403 })
        }
        expect(String(path)).toBe(`/api/academic/group-deletions/${intent.operationId}`)
        if (!statusVisible) return new Response(null, { status: 404 })
        return Response.json(recorded)
      },
    })
    let unknown: unknown
    try {
      await client.confirmGroupDeletion(groupId, intent)
    } catch (cause) {
      unknown = cause
    }
    expect(unknown).toBeInstanceOf(AdminGroupDeletionUnknownError)
    const reference = unknown as AdminGroupDeletionUnknownError
    expect(reference).toMatchObject({
      state: 'UNKNOWN',
      operationId: intent.operationId,
      groupId,
      status: 403,
    })
    await expect(
      client.getGroupDeletion(reference.operationId, reference.groupId),
    ).rejects.toMatchObject({
      response: { status: 404 },
    })
    // A missing read does not change the original reference or authorize another command.
    expect(reference.operationId).toBe(intent.operationId)
    statusVisible = true
    await expect(
      client.getGroupDeletion(reference.operationId, reference.groupId),
    ).resolves.toEqual(accepted)
  })

  it('keeps the original recovery key for unavailable, malformed or contradictory ACKs', async () => {
    const cases = [
      { status: 408, patch: {} },
      { status: 503, patch: {} },
      { status: 200, patch: { phase: 'PREPARING', retryable: true } },
      { status: 202, patch: {} },
      { status: 200, patch: { phase: 'FUTURE_PHASE' } },
      { status: 200, patch: { groupId: 'foreign-group' } },
      { status: 200, patch: { counts: {} } },
    ]
    for (const { status, patch } of cases) {
      const intent = input()
      const client = new AdminGroupsClient({
        accessToken: () => null,
        fetcher: async () =>
          Response.json({ ...operation(intent.operationId), ...patch }, { status }),
      })
      await expect(client.confirmGroupDeletion(groupId, intent)).rejects.toMatchObject({
        state: 'UNKNOWN',
        operationId: intent.operationId,
        groupId,
        status,
      })
    }
  })

  it('rejects invalid confirmation inputs before destructive admission', async () => {
    const client = new AdminGroupsClient({
      accessToken: () => null,
      fetcher: async () => {
        throw new Error('No destructive request is allowed')
      },
    })
    await expect(
      client.confirmGroupDeletion(groupId, { ...input(), password: '  ' }),
    ).rejects.toBeInstanceOf(RangeError)
    await expect(
      client.confirmGroupDeletion(groupId, { ...input(), password: 'x'.repeat(257) }),
    ).rejects.toBeInstanceOf(RangeError)
    await expect(
      client.confirmGroupDeletion(groupId, { ...input(), previewDigest: '' }),
    ).rejects.toBeInstanceOf(TypeError)
    await expect(
      client.confirmGroupDeletion(groupId, { ...input(), operationId: '' }),
    ).rejects.toBeInstanceOf(TypeError)
    await expect(client.previewGroupDeletion(1 as unknown as string)).rejects.toBeInstanceOf(
      TypeError,
    )
    await expect(client.previewGroupDeletion('..')).rejects.toBeInstanceOf(TypeError)
  })

  it('does not expose a late completion to an obsolete owner and permits a fresh scoped status read', async () => {
    const intent = input()
    const saved = operation(intent.operationId)
    let current = true
    const client = new AdminGroupsClient({
      accessToken: () => 'old-session',
      assertCurrent: () => {
        if (!current) throw new Error('Authority changed')
      },
      fetcher: async () => {
        current = false
        return Response.json(saved)
      },
    })
    await expect(client.confirmGroupDeletion(groupId, intent)).rejects.toMatchObject({
      state: 'UNKNOWN',
      operationId: intent.operationId,
      groupId,
      status: 200,
    })
    const fresh = new AdminGroupsClient({
      accessToken: () => 'fresh-session',
      fetcher: async (path) => {
        expect(String(path)).toBe(`/api/academic/group-deletions/${intent.operationId}`)
        return Response.json(saved)
      },
    })
    await expect(fresh.getGroupDeletion(intent.operationId, groupId)).resolves.toEqual(saved)
  })

  it('keeps a permission withdrawal visible during original-key status recovery', async () => {
    const client = new AdminGroupsClient({
      accessToken: () => 'current-owner',
      fetcher: async () => Response.json({ title: 'Forbidden' }, { status: 403 }),
    })
    await expect(client.getGroupDeletion(crypto.randomUUID(), groupId)).rejects.toMatchObject({
      response: { status: 403 },
    })
  })
})

describe('explicit original group deletion resolution', () => {
  it('admits an absent-intent CANCELLED receipt using a bodyless original-ID request', async () => {
    const key = crypto.randomUUID()
    const cancelled = {
      ...operation(key, 'CANCELLED'),
      counts: Object.fromEntries(Object.keys(counts()).map((name) => [name, 0])),
      failure: 'NOT_ACCEPTED',
    }
    const client = new AdminGroupsClient({
      accessToken: () => 'current-owner',
      fetcher: async (path, init) => {
        expect(String(path)).toBe(`/api/academic/groups/${groupId}/deletions/${key}/resolve`)
        expect(init?.method).toBe('POST')
        expect(init).not.toHaveProperty('body')
        const headers = new Headers(init?.headers)
        expect(headers.has('Idempotency-Key')).toBe(false)
        expect(headers.has('Content-Type')).toBe(false)
        expect(headers.get('Authorization')).toBe('Bearer current-owner')
        expect(init?.credentials).toBe('include')
        expect(init?.cache).toBe('no-store')
        return Response.json(cancelled)
      },
    })
    await expect(client.resolveGroupDeletion(key, groupId)).resolves.toEqual(cancelled)
  })

  it('resolves a lost accepted command to its existing pending receipt without cancelling or repeating it', async () => {
    const intent = input()
    const accepted = operation(intent.operationId, 'COMMITTING')
    let recorded: typeof accepted | null = null
    let acceptedCommands = 0
    const client = new AdminGroupsClient({
      accessToken: () => 'same-actor-current-session',
      fetcher: async (path, init) => {
        if (String(path).endsWith('/resolve')) {
          expect(init).not.toHaveProperty('body')
          return Response.json(recorded)
        }
        acceptedCommands++
        recorded = accepted
        throw new TypeError('Lost acceptance response')
      },
    })
    await expect(client.confirmGroupDeletion(groupId, intent)).rejects.toMatchObject({
      state: 'UNKNOWN',
      operationId: intent.operationId,
      groupId,
    })
    await expect(client.resolveGroupDeletion(intent.operationId, groupId)).resolves.toEqual(
      accepted,
    )
    expect(acceptedCommands).toBe(1)
    expect(recorded).toEqual(accepted)
  })

  it('recovers a lost resolve reply with the same original GET and no automatic resolve retry', async () => {
    const key = crypto.randomUUID()
    const cancelled = {
      ...operation(key, 'CANCELLED'),
      counts: Object.fromEntries(Object.keys(counts()).map((name) => [name, 0])),
      failure: 'NOT_ACCEPTED',
    }
    let recorded: typeof cancelled | null = null
    const client = new AdminGroupsClient({
      accessToken: () => 'current-owner',
      fetcher: async (path, init) => {
        if (init?.method === 'POST') {
          recorded = cancelled
          throw new TypeError('Lost resolve response')
        }
        expect(String(path)).toBe(`/api/academic/group-deletions/${key}`)
        return Response.json(recorded)
      },
    })
    await expect(client.resolveGroupDeletion(key, groupId)).rejects.toMatchObject({
      state: 'UNKNOWN',
      operationId: key,
      groupId,
      status: null,
    })
    await expect(client.getGroupDeletion(key, groupId)).resolves.toEqual(cancelled)
  })

  it('retains UNKNOWN on denied, mismatched, malformed or unsupported-status resolve replies', async () => {
    for (const { status, patch } of [
      { status: 401, patch: {} },
      { status: 403, patch: {} },
      { status: 404, patch: {} },
      { status: 409, patch: {} },
      { status: 503, patch: {} },
      { status: 202, patch: {} },
      { status: 200, patch: { groupId: '1' } },
      { status: 200, patch: { operationId: crypto.randomUUID() } },
      { status: 200, patch: { phase: 'UNKNOWN' } },
      { status: 200, patch: { counts: {} } },
    ]) {
      const key = crypto.randomUUID()
      let token = 'original-session'
      const client = new AdminGroupsClient({
        accessToken: () => token,
        onUnauthorized: async () => {
          token = 'new-session'
        },
        fetcher: async () => {
          if (token === 'new-session') return Response.json(operation(key))
          return Response.json({ ...operation(key), ...patch }, { status })
        },
      })
      await expect(client.resolveGroupDeletion(key, groupId)).rejects.toMatchObject({
        state: 'UNKNOWN',
        operationId: key,
        groupId,
        status,
      })
    }
  })

  it('rejects an otherwise valid resolved receipt after its captured owner changed', async () => {
    const key = crypto.randomUUID()
    let current = true
    const client = new AdminGroupsClient({
      accessToken: () => 'old-owner',
      assertCurrent: () => {
        if (!current) throw new Error('Authority changed')
      },
      fetcher: async () => {
        current = false
        return Response.json(operation(key, 'CANCELLED'))
      },
    })
    await expect(client.resolveGroupDeletion(key, groupId)).rejects.toMatchObject({
      state: 'UNKNOWN',
      operationId: key,
      groupId,
      status: 200,
    })
  })
})
