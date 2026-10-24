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
) {
  const owner = { current: true }
  let durable = saved
  const flow = createGroupDeletionFlow({
    userId: '1',
    client,
    saved,
    newOperationId: () => key,
    assertCurrent() {
      if (!owner.current) throw new Error('retired owner')
    },
    persist(value) {
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

  it.each([400, 401, 403, 409, 429])(
    'requires a fresh preview after definite POST rejection %s',
    async (status) => {
      const model = fixture({
        async confirmGroupDeletion() {
          throw new AdminGroupsApiError(new Response(null, { status }), null)
        },
        getGroupDeletion: unused,
      })
      model.flow.prepare(preview)
      await expect(model.flow.submit('synthetic-password')).rejects.toThrow()
      expect(model.flow.state).toEqual({ preview: null, operation: null, reference: null })
      expect(model.durable).toBeNull()
      await expect(model.flow.submit('another-password')).rejects.toThrow()
    },
  )

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
    expect(model.flow.state).toEqual({ preview: null, operation: null, reference })
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
})
