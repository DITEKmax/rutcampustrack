import { describe, expect, it } from 'vitest'
import {
  AdminGroupDeletionUnknownError,
  AdminGroupsApiError,
  type AdminGroupDeletionOperation,
  type AdminGroupDeletionPreview,
  type AdminGroupsClient,
} from '../../../../mobile-core/src/features/admin-groups/admin-groups-client'
import {
  createGroupDeletionFlow,
  bindGroupDeletionRecovery,
  GroupDeletionStorageError,
  restoreGroupDeletion,
  type GroupDeletionReference,
} from './group-deletion-state'

const key = 'd16d6bf8-f57b-4ab5-aa43-05f3c1d7e9c0'
const preview: AdminGroupDeletionPreview = {
  groupId: '42',
  groupName: 'ИВТ-111',
  previewDigest: 'a'.repeat(64),
  counts: {
    accountsAffected: 2,
    memberships: 2,
    headmanAssignments: 1,
    assistantAssignments: 1,
    authGrants: 3,
    academicRows: 12,
    scheduleRows: 3,
    attendanceRows: 4,
  },
}
const completed: AdminGroupDeletionOperation = {
  operationId: key,
  groupId: '42',
  phase: 'COMPLETED',
  retryable: false,
  counts: preview.counts,
  failure: null,
}
const reference: GroupDeletionReference = {
  userId: '1',
  groupId: '42',
  operationId: key,
  previewDigest: preview.previewDigest,
}
function fixture(
  client: Pick<AdminGroupsClient, 'confirmGroupDeletion' | 'getGroupDeletion'>,
  saved: GroupDeletionReference | null = null,
  persist?: (value: GroupDeletionReference | null) => void,
  recoveryUnavailable = false,
) {
  const owner = { current: true }
  let durable = saved
  const flow = createGroupDeletionFlow({
    userId: '1',
    client,
    saved,
    recoveryUnavailable,
    newOperationId: () => key,
    assertCurrent() {
      if (!owner.current) throw new Error('retired owner')
    },
    persist(value) {
      persist?.(value)
      durable = value
    },
    changed() {},
  })
  return {
    flow,
    owner,
    get durable() {
      return durable
    },
  }
}
function unused(): never {
  throw new Error('Unexpected command')
}

describe('final group deletion recovery', () => {
  it('recovers a lost command after reload without deleting later data', async () => {
    const receipts = new Map<string, AdminGroupDeletionOperation>()
    const target = { exists: true, laterData: false }
    const client: Pick<AdminGroupsClient, 'confirmGroupDeletion' | 'getGroupDeletion'> = {
      async confirmGroupDeletion(groupId, input) {
        expect(groupId).toBe('42')
        expect(input.previewDigest).toBe(preview.previewDigest)
        target.exists = false
        target.laterData = false
        receipts.set(input.operationId, completed)
        throw new AdminGroupDeletionUnknownError(input.operationId, groupId, null)
      },
      async getGroupDeletion(operationId, groupId) {
        const receipt = receipts.get(operationId)
        if (!receipt || receipt.groupId !== groupId) throw new Error('not found')
        return receipt
      },
    }
    const first = fixture(client)
    first.flow.prepare(preview)
    await expect(first.flow.submit('synthetic-password')).rejects.toBeInstanceOf(
      AdminGroupDeletionUnknownError,
    )
    expect(first.durable).toEqual(reference)
    expect(first.flow.state.reference).toEqual(reference)
    expect(first.flow.state.preview).toBeNull()
    expect(JSON.stringify(first.durable)).not.toContain('synthetic-password')
    // Changed selectors and another password must never create a replacement intent.
    expect(() => first.flow.prepare({ ...preview, groupId: '43' })).toThrow()
    await expect(first.flow.submit('another-password')).rejects.toThrow()
    target.laterData = true
    const reloaded = fixture(client, restoreGroupDeletion(first.durable, '1'))
    await expect(reloaded.flow.recover()).resolves.toEqual(completed)
    expect(target).toEqual({ exists: false, laterData: true })
    expect(reloaded.durable).toBeNull()
    expect(reloaded.flow.state.reference).toBeNull()
  })

  it.each([404, 403, 503])(
    'retains the original intent after GET %s, then accepts terminal cancellation',
    async (status) => {
      let available = false
      const model = fixture(
        {
          confirmGroupDeletion: unused,
          async getGroupDeletion() {
            if (!available) throw new AdminGroupsApiError(new Response(null, { status }), null)
            return { ...completed, phase: 'CANCELLED' }
          },
        },
        reference,
      )
      await expect(model.flow.recover()).rejects.toThrow()
      expect(model.flow.state.reference).toEqual(reference)
      expect(model.durable).toEqual(reference)
      available = true
      await model.flow.recover()
      expect(model.flow.state.operation?.phase).toBe('CANCELLED')
      expect(model.durable).toBeNull()
    },
  )

  it('keeps pending and retryable operations read-only until the server confirms completion', async () => {
    let operation: AdminGroupDeletionOperation = {
      ...completed,
      phase: 'PREPARING',
      retryable: true,
    }
    const model = fixture({
      async confirmGroupDeletion() {
        return operation
      },
      async getGroupDeletion() {
        return operation
      },
    })
    model.flow.prepare(preview)
    await model.flow.submit('synthetic-password')
    expect(model.flow.state.reference).toEqual(reference)
    await expect(model.flow.submit('another-password')).rejects.toThrow()
    operation = { ...operation, phase: 'RELEASING' }
    await model.flow.recover()
    expect(model.flow.state.reference).toEqual(reference)
    operation = completed
    await model.flow.recover()
    expect(model.flow.state.reference).toBeNull()
  })

  it('preserves an accepted command after a late403 and recovers only its original receipt', async () => {
    const data = { groupExists: true, laterData: false }
    const model = fixture({
      async confirmGroupDeletion(groupId, input) {
        data.groupExists = false
        data.laterData = false
        throw new AdminGroupDeletionUnknownError(input.operationId, groupId, 403)
      },
      async getGroupDeletion(operationId, groupId) {
        if (operationId !== key || groupId !== preview.groupId) throw new Error('unknown receipt')
        return completed
      },
    })
    model.flow.prepare(preview)
    await expect(model.flow.submit('synthetic-password')).rejects.toThrow()
    expect(model.flow.state.reference).toEqual(reference)
    expect(model.durable).toEqual(reference)
    expect(() => model.flow.prepare(preview)).toThrow()
    await expect(model.flow.submit('another-password')).rejects.toThrow()
    data.laterData = true
    await model.flow.recover()
    expect(data).toEqual({ groupExists: false, laterData: true })
    expect(model.durable).toBeNull()
  })

  it('does not replace a post-send403 intent when its saved receipt still returns404', async () => {
    const model = fixture({
      async confirmGroupDeletion(groupId, input) {
        throw new AdminGroupDeletionUnknownError(input.operationId, groupId, 403)
      },
      async getGroupDeletion() {
        throw new AdminGroupsApiError(new Response(null, { status: 404 }), null)
      },
    })
    model.flow.prepare(preview)
    await expect(model.flow.submit('synthetic-password')).rejects.toThrow()
    await expect(model.flow.recover()).rejects.toThrow()
    expect(model.durable).toEqual(reference)
    expect(model.flow.state.reference).toEqual(reference)
    expect(() => model.flow.prepare(preview)).toThrow()
    await expect(model.flow.submit('another-password')).rejects.toThrow()
  })

  it('discards private preview and progress on permission withdrawal but preserves the recovery key', async () => {
    const model = fixture({
      async confirmGroupDeletion() {
        return { ...completed, phase: 'COMMITTING' }
      },
      getGroupDeletion: unused,
    })
    model.flow.prepare(preview)
    model.flow.clearPrivate()
    expect(model.flow.state.preview).toBeNull()
    model.flow.prepare(preview)
    await model.flow.submit('synthetic-password')
    model.owner.current = false
    model.flow.clearPrivate()
    expect(model.flow.state).toEqual({
      preview: null,
      operation: null,
      reference,
      recoveryUnavailable: false,
    })
    expect(model.durable).toEqual(reference)
  })

  it('does not apply a completed response after the admitted owner changes', async () => {
    let resolve!: (value: AdminGroupDeletionOperation) => void
    const model = fixture({
      confirmGroupDeletion: () =>
        new Promise((done) => {
          resolve = done
        }),
      getGroupDeletion: unused,
    })
    model.flow.prepare(preview)
    const sending = model.flow.submit('synthetic-password')
    model.owner.current = false
    resolve(completed)
    await expect(sending).rejects.toThrow('retired owner')
    expect(model.durable).toEqual(reference)
    expect(model.flow.state.operation).toBeNull()
  })

  it('refuses a receipt for a different group or operation and keeps the original intent', async () => {
    const model = fixture(
      {
        confirmGroupDeletion: unused,
        async getGroupDeletion() {
          return { ...completed, groupId: '43' }
        },
      },
      reference,
    )
    expect(() => model.flow.restoreRecovery({ ...reference, groupId: '43' })).toThrow()
    expect(() =>
      model.flow.restoreRecovery({ ...reference, previewDigest: 'b'.repeat(64) }),
    ).toThrow()
    await expect(model.flow.recover()).rejects.toThrow()
    expect(model.durable).toEqual(reference)
    expect(model.flow.state.operation).toBeNull()
  })

  it('restores only the same actor and allowlisted nonsecret fields', () => {
    expect(restoreGroupDeletion(reference, '2')).toBeNull()
    expect(restoreGroupDeletion({ ...reference, operationId: 'invalid' }, '1')).toBeNull()
    expect(restoreGroupDeletion({ ...reference, password: 'never-restore' }, '1')).toEqual(
      reference,
    )
  })

  it('keeps group data untouched if the original intent cannot be saved before dispatch', async () => {
    const data = { exists: true }
    const recovery = bindGroupDeletionRecovery(
      {
        getItem: () => null,
        setItem() {
          throw new Error('storage denied')
        },
        removeItem() {},
      },
      'actor-key',
      '1',
    )
    const model = fixture(
      {
        async confirmGroupDeletion() {
          data.exists = false
          return completed
        },
        getGroupDeletion: unused,
      },
      null,
      recovery.persist,
    )
    model.flow.prepare(preview)
    await expect(model.flow.submit('synthetic-password')).rejects.toBeInstanceOf(
      GroupDeletionStorageError,
    )
    expect(data.exists).toBe(true)
    expect(model.flow.state.reference).toBeNull()
    expect(model.flow.state.preview).toEqual(preview)
    expect(model.durable).toBeNull()
  })

  it('blocks new commands after an unreadable saved intent until its original key is restored', async () => {
    let readable = false
    const data = { laterData: true }
    const recovery = bindGroupDeletionRecovery(
      {
        getItem() {
          if (!readable) throw new Error('storage denied')
          return JSON.stringify(reference)
        },
        setItem: unused,
        removeItem() {},
      },
      'actor-key',
      '1',
    )
    expect(() => recovery.read()).toThrow(GroupDeletionStorageError)
    const model = fixture(
      {
        async confirmGroupDeletion() {
          data.laterData = false
          return completed
        },
        async getGroupDeletion(operationId, groupId) {
          if (operationId !== key || groupId !== reference.groupId)
            throw new Error('unknown receipt')
          return completed
        },
      },
      null,
      recovery.persist,
      true,
    )
    expect(() => model.flow.prepare(preview)).toThrow()
    await expect(model.flow.submit('synthetic-password')).rejects.toThrow()
    readable = true
    model.flow.restoreRecovery(recovery.read()!)
    await model.flow.recover()
    expect(data.laterData).toBe(true)
    expect(model.flow.state.reference).toBeNull()
    expect(model.flow.state.recoveryUnavailable).toBe(false)
  })

  it.each(['invalid JSON', JSON.stringify({ ...reference, operationId: 'invalid' })])(
    'does not treat a malformed saved intent as an empty recovery slot',
    (raw) => {
      const recovery = bindGroupDeletionRecovery(
        {
          getItem: () => raw,
          setItem: unused,
          removeItem: unused,
        },
        'actor-key',
        '1',
      )
      expect(() => recovery.read()).toThrow(GroupDeletionStorageError)
    },
  )

  it('allows confirmed terminal completion despite unavailable storage cleanup', async () => {
    const recovery = bindGroupDeletionRecovery(
      {
        getItem: () => JSON.stringify(reference),
        setItem: unused,
        removeItem() {
          throw new Error('storage denied')
        },
      },
      'actor-key',
      '1',
    )
    const model = fixture(
      {
        confirmGroupDeletion: unused,
        async getGroupDeletion() {
          return completed
        },
      },
      recovery.read(),
      recovery.persist,
    )
    await model.flow.recover()
    expect(model.flow.state.reference).toBeNull()
    // A reload retains only the old, already-terminal GET-only hint.
    expect(recovery.read()).toEqual(reference)
  })
})
