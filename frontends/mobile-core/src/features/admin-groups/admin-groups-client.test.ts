import { afterEach, describe, expect, it, vi } from 'vitest'
import { AdminGroupsClient, adminGroupProgramTypes } from './admin-groups-client'
import { createAdminGroupsFixture } from '../../dev/admin-groups-fixtures'

afterEach(() => vi.unstubAllGlobals())
function fixture() {
  vi.stubGlobal('document', { documentElement: { dataset: {} } })
  vi.stubGlobal('window', { location: { origin: 'https://synthetic.invalid' } })
  vi.stubGlobal('fetch', () => {
    throw new Error('Unexpected real network')
  })
  return createAdminGroupsFixture('active')
}
describe('registry server filtering and ordering contract', () => {
  it('uses the same nine programme mappings across filters, metadata and paginated results', async () => {
    const client = fixture()
    for (const program of adminGroupProgramTypes) {
      const result = await client.listGroups({
        status: 'ACTIVE',
        studyMode: program.studyMode,
        educationLevel: program.educationLevel,
        size: 1,
      })
      expect(result.items[0]?.programTypeDigit).toBe(program.digit)
      expect(result.totalElements).toBeGreaterThan(1)
      const next = await client.listGroups({
        status: 'ACTIVE',
        studyMode: program.studyMode,
        educationLevel: program.educationLevel,
        size: 1,
        page: 1,
      })
      expect(next.items[0]?.programTypeDigit).toBe(program.digit)
      expect(next.items[0]?.id).not.toBe(result.items[0]?.id)
      expect(next.totalElements).toBe(result.totalElements)
      expect(next.activeCount).toBe(result.totalElements)
    }
  })
  it('orders the complete registry before slicing pages and combines course with real programme predicates', async () => {
    const client = fixture()
    const first = await client.listGroups({
      status: 'ACTIVE',
      sort: ['programType,desc', 'currentCourse,asc'],
      size: 3,
    })
    const next = await client.listGroups({
      status: 'ACTIVE',
      sort: ['programType,desc', 'currentCourse,asc'],
      size: 3,
      page: 1,
    })
    expect(first.items.map((item) => item.programTypeDigit)).toEqual([9, 9, 8])
    expect(next.items.map((item) => item.programTypeDigit)).toEqual([8, 7, 7])
    expect(new Set([...first.items, ...next.items].map((item) => item.id)).size).toBe(6)
    const filtered = await client.listGroups({
      course: 3,
      studyMode: 'EXTRAMURAL',
      educationLevel: 'BACHELOR',
      size: 20,
    })
    expect(
      filtered.items.every((item) => item.currentCourse === 3 && item.programTypeDigit === 3),
    ).toBe(true)
    expect(filtered.activeCount).toBe(filtered.items.length)
  })
  it('keeps unknown additive metadata unknown when reading an older accepted response', async () => {
    const original = await fixture().listGroups({ size: 1 })
    const legacy = {
      ...original,
      items: original.items.map((item) => {
        const legacyItem: Record<string, unknown> = { ...item }
        delete legacyItem.programTypeDigit
        delete legacyItem.studyMode
        delete legacyItem.educationLevel
        return legacyItem
      }),
    }
    const client = new AdminGroupsClient({
      accessToken: () => null,
      fetcher: async () => Response.json(legacy),
    })
    const result = await client.listGroups()
    expect(result.items[0]?.programTypeDigit).toBeNull()
    expect(result.items[0]?.studyMode).toBeNull()
    expect(result.items[0]?.educationLevel).toBeNull()
  })
})

describe('course limits preserve programme types and legacy reads', () => {
  it('accepts course5, rejects course6 before requests, and keeps programme9 independent', async () => {
    const client = fixture()
    await expect(client.listGroups({ course: 5 })).resolves.toBeDefined()
    await expect(client.listGroups({ course: 6 })).rejects.toThrow('от 1 до 5')
    await expect(
      client.createGroup({ alphabeticCode: 'УИТ', numericCode: '591', trainingDurationYears: 5 }),
    ).resolves.toMatchObject({ currentCourse: 5, programTypeDigit: 9 })
    expect(() =>
      client.createGroup({ alphabeticCode: 'УИТ', numericCode: '691', trainingDurationYears: 9 }),
    ).toThrow('от 1 до 5')
  })
  it('retains a historical course6 response without rewriting or archiving it', async () => {
    const page = await fixture().listGroups({ size: 1 })
    const legacy = {
      ...page,
      items: page.items.map((item) => ({ ...item, currentCourse: 6, numericCode: '611' })),
    }
    const client = new AdminGroupsClient({
      accessToken: () => null,
      fetcher: async () => Response.json(legacy),
    })
    await expect(client.listGroups()).resolves.toMatchObject({
      items: [{ currentCourse: 6, numericCode: '611', status: 'ACTIVE' }],
    })
  })
})

describe('explicit active group metadata correction', () => {
  it('preserves a known duration by omission and allows explicit unknown-duration correction', async () => {
    const current = (await fixture().listGroups({ size: 1 })).items[0]!
    const fetcher = vi.fn<typeof fetch>(async () => new Response(null, { status: 204 }))
    const client = new AdminGroupsClient({ accessToken: () => null, fetcher })
    await client.renameGroup(current, 'УИТ-191')
    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({
      name: 'УИТ-191',
      active: true,
    })
    const legacy = {
      ...current,
      durationStatus: 'LEGACY_UNKNOWN' as const,
      trainingDurationYears: null,
    }
    await expect(client.renameGroup(legacy, 'УИТ-591')).rejects.toThrow()
    await client.renameGroup(legacy, 'УИТ-591', 5)
    expect(JSON.parse(String(fetcher.mock.calls[1]?.[1]?.body))).toEqual({
      name: 'УИТ-591',
      active: true,
      trainingDurationYears: 5,
    })
    await expect(client.renameGroup(legacy, 'УИТ-691', 5)).rejects.toBeInstanceOf(RangeError)
    await expect(client.renameGroup(legacy, 'УИТ-591', 6)).rejects.toThrow('от 1 до 5')
    await expect(client.renameGroup({ ...current, status: 'ARCHIVED' }, 'УИТ-191')).rejects.toThrow(
      'активной',
    )
    expect(fetcher).toHaveBeenCalledTimes(2)
  })
})

const commandKinds = ['ARCHIVE', 'RESTORE', 'PROMOTION'] as const
const failedId = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
function operationReceipt(id: string, status = 'PENDING'): Response {
  return Response.json(
    { operationId: id, status, retryAfterSeconds: 0 },
    {
      status: status === 'PENDING' ? 202 : 200,
      headers: { 'X-Operation-Id': id, Location: `/academic/identity-operations/${id}` },
    },
  )
}

describe('durable group lifecycle consumers', () => {
  it.each(commandKinds)(
    'keeps %s pending until same-key replay returns its original success body',
    async (kind) => {
      const preview = await fixture().previewPromotion()
      const success = { ...preview, dryRun: false, executed: true }
      const path =
        kind === 'PROMOTION'
          ? '/api/academic/groups/promote'
          : `/api/academic/groups/1${kind === 'RESTORE' ? '/restore' : ''}`
      const keys: string[] = []
      const bodies: (BodyInit | null | undefined)[] = []
      let id = '',
        finished = false
      const client = new AdminGroupsClient({
        accessToken: () => null,
        fetcher: async (input, init) => {
          if (String(input).includes('/identity-operations/'))
            return operationReceipt(id, finished ? 'SUCCEEDED' : 'PENDING')
          expect(String(input)).toBe(path)
          id = new Headers(init?.headers).get('Idempotency-Key')!
          keys.push(id)
          bodies.push(init?.body)
          if (keys.length === 1) return operationReceipt(id)
          if (kind === 'RESTORE') return new Response(null, { status: 204 })
          return Response.json(
            kind === 'PROMOTION' ? success : { id: 1, name: 'УИТ-110', active: false },
          )
        },
      })
      const signal = new AbortController().signal
      const command =
        kind === 'ARCHIVE'
          ? client.archiveGroup(1, 'УИТ-110', signal)
          : kind === 'RESTORE'
            ? client.restoreGroup(1, signal)
            : client.executePromotion(
                {
                  cycleSemesterId: preview.cycleSemesterId,
                  previewVersion: preview.previewVersion,
                  groupId: null,
                },
                signal,
              )
      await expect(command).rejects.toMatchObject({ state: 'PENDING' })
      expect(client.hasPendingIdentityOperation).toBe(true)
      finished = true
      const result = await client.resumeIdentityOperation()
      expect(result.kind).toBe(kind)
      if (result.kind === 'PROMOTION')
        expect(result.summary).toMatchObject({ executed: true, dryRun: false, groupId: null })
      else if (result.kind !== 'HEADMAN' && result.kind !== 'RECOVERY')
        expect(result.groupId).toBe(1)
      expect(keys).toEqual([id, id])
      expect(bodies[1]).toBe(bodies[0])
      if (kind === 'RESTORE') expect(bodies).toEqual([undefined, undefined])
      client.acknowledgeIdentityOperation()
      expect(client.hasPendingIdentityOperation).toBe(false)
    },
  )

  it.each(commandKinds)(
    'retains the %s key after losing the first response, including GET404',
    async (kind) => {
      const preview = await fixture().previewPromotion()
      const keys: string[] = []
      let applied = 0
      const appliedKeys = new Set<string>()
      const client = new AdminGroupsClient({
        accessToken: () => null,
        fetcher: async (input, init) => {
          if (String(input).includes('/identity-operations/'))
            return new Response(null, { status: 404 })
          const key = new Headers(init?.headers).get('Idempotency-Key')!
          keys.push(key)
          if (!appliedKeys.has(key)) {
            appliedKeys.add(key)
            applied++
          }
          if (keys.length === 1) throw new TypeError('lost response')
          return kind === 'RESTORE'
            ? new Response(null, { status: 204 })
            : Response.json(
                kind === 'PROMOTION'
                  ? { ...preview, dryRun: false, executed: true }
                  : { id: 1, active: false },
              )
        },
      })
      const signal = new AbortController().signal
      const command =
        kind === 'ARCHIVE'
          ? client.archiveGroup(1, 'УИТ-110', signal)
          : kind === 'RESTORE'
            ? client.restoreGroup(1, signal)
            : client.executePromotion(
                {
                  cycleSemesterId: preview.cycleSemesterId,
                  previewVersion: preview.previewVersion,
                  groupId: null,
                },
                signal,
              )
      await expect(command).rejects.toMatchObject({ state: 'UNKNOWN' })
      expect((await client.resumeIdentityOperation()).kind).toBe(kind)
      expect(keys[1]).toBe(keys[0])
      expect(applied).toBe(1)
      client.acknowledgeIdentityOperation()
    },
  )

  it('GET-confirms a failed group before explicit recovery and resumes its new key as Operation success', async () => {
    let originalId = '',
      recoveryId = '',
      recovered = false
    const recoveryKeys: string[] = []
    const client = new AdminGroupsClient({
      accessToken: () => null,
      fetcher: async (input, init) => {
        const path = String(input)
        if (path.endsWith('/reconcile')) {
          expect(path).toBe(`/api/academic/identity-operations/${originalId}/reconcile`)
          expect(init?.body).toBeUndefined()
          recoveryId = new Headers(init?.headers).get('Idempotency-Key')!
          recoveryKeys.push(recoveryId)
          return operationReceipt(recoveryId, recovered ? 'SUCCEEDED' : 'PENDING')
        }
        if (path.includes('/identity-operations/'))
          return operationReceipt(
            path.endsWith(originalId) ? originalId : recoveryId,
            path.endsWith(originalId) ? 'FAILED_SAFE' : recovered ? 'SUCCEEDED' : 'PENDING',
          )
        originalId = new Headers(init?.headers).get('Idempotency-Key')!
        return operationReceipt(originalId)
      },
    })
    await expect(
      client.archiveGroup(1, 'УИТ-110', new AbortController().signal),
    ).rejects.toMatchObject({ state: 'FAILED_SAFE' })
    expect(client.failedGroupOperationId).toBe(originalId)
    await expect(client.reconcileGroupOperation(originalId)).rejects.toThrow('Сначала проверь')
    expect(recoveryKeys).toHaveLength(0)
    expect(await client.confirmGroupRecovery()).toBe(originalId)
    await expect(
      client.reconcileGroupOperation(originalId, new AbortController().signal),
    ).rejects.toMatchObject({ state: 'PENDING' })
    expect(recoveryId).not.toBe(originalId)
    recovered = true
    await expect(client.resumeIdentityOperation()).resolves.toEqual({ kind: 'RECOVERY' })
    expect(recoveryKeys).toEqual([recoveryId, recoveryId])
    client.acknowledgeIdentityOperation()
    expect(client.failedGroupOperationId).toBeNull()
  })

  it.each(['PENDING', 'REJECTED', 'SUCCEEDED', '404'])(
    'does not authorize recovery from an extras selector with current GET %s',
    async (status) => {
      let posts = 0
      const client = new AdminGroupsClient({
        accessToken: () => null,
        fetcher: async (input, init) => {
          if (String(input).includes('/identity-operations/'))
            return status === '404'
              ? new Response(null, { status: 404 })
              : operationReceipt(failedId, status)
          posts++
          expect(init?.method).toBe('PUT')
          return Response.json({ extras: { operationId: failedId } }, { status: 409 })
        },
      })
      await expect(client.archiveGroup(1, 'УИТ-110')).rejects.toMatchObject({ state: 'UNKNOWN' })
      expect(client.failedGroupOperationId).toBe(failedId)
      if (status === '404')
        await expect(client.confirmGroupRecovery()).rejects.toMatchObject({
          response: { status: 404 },
        })
      else expect(await client.confirmGroupRecovery()).toBeNull()
      await expect(client.reconcileGroupOperation(failedId)).rejects.toThrow('Сначала проверь')
      expect(posts).toBe(1)
    },
  )
})
