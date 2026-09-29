export type PasswordRecoveryOperation = {
  signal: AbortSignal
  isCurrent(): boolean
}

export function createPasswordRecoveryOperationGate(): {
  begin(): PasswordRecoveryOperation
  invalidate(): void
} {
  let revision = 0
  let controller: AbortController | null = null

  return {
    begin() {
      controller?.abort()
      controller = new AbortController()
      const operationRevision = ++revision
      const operationController = controller
      return {
        signal: operationController.signal,
        isCurrent: () => operationRevision === revision && !operationController.signal.aborted,
      }
    },
    invalidate() {
      revision += 1
      controller?.abort()
      controller = null
    },
  }
}