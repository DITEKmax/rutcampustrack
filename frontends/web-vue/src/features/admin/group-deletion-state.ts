import {
  type AdminGroupDeletionOperation,
  type AdminGroupDeletionPreview,
  type AdminGroupsClient,
} from '../../../../mobile-core/src/features/admin-groups/admin-groups-client'

/** Only this nonsecret intent survives reload or a new session of the same actor. */
export interface GroupDeletionReference {
  readonly userId: string
  readonly groupId: string
  readonly operationId: string
  readonly previewDigest: string
}

export interface GroupDeletionState {
  readonly preview: AdminGroupDeletionPreview | null
  readonly reference: GroupDeletionReference | null
  readonly operation: AdminGroupDeletionOperation | null
  readonly recoveryUnavailable: boolean
}

export class GroupDeletionStorageError extends Error {
  constructor(readonly operation: 'READ' | 'WRITE') {
    super(
      operation === 'WRITE'
        ? 'Не удалось сохранить исходную операцию. Запрос удаления не отправлен.'
        : 'Не удалось прочитать исходную операцию. Новое удаление заблокировано.',
    )
    this.name = 'GroupDeletionStorageError'
  }
}

export function bindGroupDeletionRecovery(
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>,
  key: string,
  userId: string,
) {
  return {
    read() {
      try {
        const raw = storage.getItem(key)
        if (raw === null) return null
        const reference = restoreGroupDeletion(JSON.parse(raw), userId)
        if (!reference) throw new GroupDeletionStorageError('READ')
        return reference
      } catch {
        throw new GroupDeletionStorageError('READ')
      }
    },
    persist(reference: GroupDeletionReference | null) {
      if (reference) {
        try {
          storage.setItem(key, JSON.stringify(reference))
        } catch {
          throw new GroupDeletionStorageError('WRITE')
        }
      } else {
        // A retained terminal hint is safe: a later mount can only GET that receipt.
        try {
          storage.removeItem(key)
        } catch {
          /* Best-effort terminal cleanup. */
        }
      }
    },
  }
}

export function restoreGroupDeletion(
  value: unknown,
  userId: string,
): GroupDeletionReference | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const saved = value as Record<string, unknown>
  if (
    saved.userId !== userId ||
    typeof saved.groupId !== 'string' ||
    !/^[1-9]\d*$/.test(saved.groupId) ||
    typeof saved.operationId !== 'string' ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(saved.operationId) ||
    typeof saved.previewDigest !== 'string' ||
    !/^[0-9a-f]{64}$/.test(saved.previewDigest)
  )
    return null
  // Copy the allowlist; never carry arbitrary saved fields (especially credentials).
  return {
    userId,
    groupId: saved.groupId,
    operationId: saved.operationId,
    previewDigest: saved.previewDigest,
  }
}

export function createGroupDeletionFlow(options: {
  userId: string
  client: Pick<
    AdminGroupsClient,
    'confirmGroupDeletion' | 'getGroupDeletion' | 'resolveGroupDeletion'
  >
  assertCurrent(): void
  persist(reference: GroupDeletionReference | null): void
  changed(state: GroupDeletionState): void
  saved?: GroupDeletionReference | null
  recoveryUnavailable?: boolean
  newOperationId?: () => string
}) {
  let state: GroupDeletionState = {
    preview: null,
    reference: restoreGroupDeletion(options.saved, options.userId),
    operation: null,
    recoveryUnavailable: options.recoveryUnavailable ?? false,
  }
  const change = (next: GroupDeletionState) => {
    options.assertCurrent()
    state = next
    options.changed(state)
  }
  const save = (reference: GroupDeletionReference | null) => {
    options.assertCurrent()
    options.persist(reference)
  }
  const accept = (operation: AdminGroupDeletionOperation, reference: GroupDeletionReference) => {
    options.assertCurrent()
    if (operation.operationId !== reference.operationId || operation.groupId !== reference.groupId)
      throw new Error('Не удалось подтвердить исходную операцию удаления.')
    const terminal = operation.phase === 'COMPLETED' || operation.phase === 'CANCELLED'
    if (terminal) save(null)
    change({
      preview: null,
      reference: terminal ? null : reference,
      operation,
      recoveryUnavailable: false,
    })
    return operation
  }
  return {
    get state() {
      return state
    },
    prepare(preview: AdminGroupDeletionPreview) {
      if (state.reference || state.recoveryUnavailable)
        throw new Error('Сначала проверь исходную операцию удаления.')
      change({ preview, reference: null, operation: null, recoveryUnavailable: false })
    },
    restoreRecovery(saved: GroupDeletionReference) {
      const reference = restoreGroupDeletion(saved, options.userId)
      if (
        !reference ||
        (state.reference &&
          (state.reference.operationId !== reference.operationId ||
            state.reference.groupId !== reference.groupId ||
            state.reference.previewDigest !== reference.previewDigest))
      )
        throw new GroupDeletionStorageError('READ')
      change({ preview: null, reference, operation: null, recoveryUnavailable: false })
    },
    clearPrivate() {
      // Retiring an owner must still erase private projections during unmount.
      state = { ...state, preview: null, operation: null }
      options.changed(state)
    },
    async submit(password: string, signal?: AbortSignal) {
      options.assertCurrent()
      const preview = state.preview
      if (state.reference || state.recoveryUnavailable || !preview)
        throw new Error('Сначала получи новый предпросмотр.')
      if (!password.trim() || password.length > 256)
        throw new RangeError('Введи свой текущий пароль.')
      const reference: GroupDeletionReference = {
        userId: options.userId,
        groupId: preview.groupId,
        operationId: (options.newOperationId ?? (() => crypto.randomUUID()))(),
        previewDigest: preview.previewDigest,
      }
      // Persist BEFORE sending: a lost response or reload must keep the original key.
      save(reference)
      change({ preview: null, reference, operation: null, recoveryUnavailable: false })
      try {
        const operation = await options.client.confirmGroupDeletion(
          reference.groupId,
          {
            operationId: reference.operationId,
            previewDigest: reference.previewDigest,
            password,
          },
          signal,
        )
        return accept(operation, reference)
      } catch (cause) {
        options.assertCurrent()
        // HTTP status alone cannot prove that a command was never durably accepted.
        // Preserve the original intent until an authoritative terminal GET/ACK.
        throw cause
      }
    },
    async recover(signal?: AbortSignal) {
      options.assertCurrent()
      const reference = state.reference
      if (!reference) throw new Error('Нет незавершённой операции удаления.')
      const operation = await options.client.getGroupDeletion(
        reference.operationId,
        reference.groupId,
        signal,
      )
      return accept(operation, reference)
    },
    async resolve(signal?: AbortSignal) {
      options.assertCurrent()
      const reference = state.reference
      if (!reference) throw new Error('Нет незавершённой операции удаления.')
      // Explicitly close only a server-proven absent attempt, using its ORIGINAL key.
      // Existing accepted operations keep their phase; failures keep the reference.
      const operation = await options.client.resolveGroupDeletion(
        reference.operationId,
        reference.groupId,
        signal,
      )
      return accept(operation, reference)
    },
  }
}
