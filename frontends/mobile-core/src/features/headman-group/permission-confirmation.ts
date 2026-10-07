import type { HeadmanAssistantPermission, HeadmanPermissionOption } from './headman-group-client'

export interface PermissionConfirmationContext {
  readonly ownerRevision: number
  readonly actorUserId: string | null
  readonly groupId: number
  readonly memberId: number
  readonly memberName: string
  readonly assistantId: number | null
  readonly helperIdentity: string
  readonly before: readonly HeadmanAssistantPermission[]
  readonly after: readonly HeadmanAssistantPermission[]
}
export interface PermissionConfirmationStep {
  readonly kind: 'revoke' | 'grant'
  readonly rights: readonly HeadmanPermissionOption[]
}
export interface PermissionConfirmationState {
  readonly snapshot: PermissionConfirmationContext
  readonly steps: readonly PermissionConfirmationStep[]
  readonly stepIndex: number
  readonly step: PermissionConfirmationStep
}
export type PermissionConfirmationResult =
  | { readonly kind: 'invalid' }
  | { readonly kind: 'next'; readonly state: PermissionConfirmationState }
  | { readonly kind: 'commit'; readonly snapshot: PermissionConfirmationContext }

function matches(snapshot: PermissionConfirmationContext, current: PermissionConfirmationContext): boolean {
  return snapshot.ownerRevision === current.ownerRevision && snapshot.actorUserId === current.actorUserId
    && snapshot.groupId === current.groupId && snapshot.memberId === current.memberId
    && snapshot.memberName === current.memberName && snapshot.assistantId === current.assistantId
    && snapshot.helperIdentity === current.helperIdentity
    && snapshot.before.length === current.before.length && snapshot.before.every((code, index) => code === current.before[index])
    && snapshot.after.length === current.after.length && snapshot.after.every((code, index) => code === current.after[index])
}

/** A single immutable draft must survive every required confirmation before it can be consumed once. */
export class PermissionConfirmationFlow {
  private state: PermissionConfirmationState | null = null

  start(context: PermissionConfirmationContext, catalog: readonly HeadmanPermissionOption[]): PermissionConfirmationState | null {
    this.cancel()
    const revokes = context.before.filter((code) => !context.after.includes(code))
    const grants = context.after.filter((code) => !context.before.includes(code))
    const rights = (codes: readonly HeadmanAssistantPermission[]) => codes.map((code) => catalog.find((option) => option.code === code))
    const revokeRights = rights(revokes), grantRights = rights(grants)
    if ((!revokes.length && !grants.length) || [...revokeRights, ...grantRights].some((option) => !option)) return null
    const snapshot = Object.freeze({ ...context, before: Object.freeze([...context.before]), after: Object.freeze([...context.after]) })
    const steps: PermissionConfirmationStep[] = []
    if (revokes.length) steps.push(Object.freeze({ kind: 'revoke', rights: Object.freeze(revokeRights.map((option) => Object.freeze({ ...option! }))) }))
    if (grants.length) steps.push(Object.freeze({ kind: 'grant', rights: Object.freeze(grantRights.map((option) => Object.freeze({ ...option! }))) }))
    this.state = Object.freeze({ snapshot, steps: Object.freeze(steps), stepIndex: 0, step: steps[0]! })
    return this.state
  }

  confirm(context: PermissionConfirmationContext): PermissionConfirmationResult {
    const state = this.state
    if (!state || !matches(state.snapshot, context)) { this.cancel(); return { kind: 'invalid' } }
    const next = state.steps[state.stepIndex + 1]
    if (next) {
      this.state = Object.freeze({ ...state, stepIndex: state.stepIndex + 1, step: next })
      return { kind: 'next', state: this.state }
    }
    this.cancel()
    return { kind: 'commit', snapshot: state.snapshot }
  }

  cancel(): void { this.state = null }
}
