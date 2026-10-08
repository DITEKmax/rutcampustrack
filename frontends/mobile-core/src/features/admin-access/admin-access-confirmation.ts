import type { AdminPermission } from './admin-access-client'
export interface AdminAccessConfirmationContext {
  readonly ownerKey: string
  readonly userId: number
  readonly userName: string
  readonly expectedVersion: number
  readonly mode: 'FULL' | 'LIMITED' | null
  readonly permissions: readonly string[]
  readonly before: readonly string[]
  readonly after: readonly string[]
}
export interface AdminAccessConfirmationStep { readonly kind: 'revoke' | 'grant'; readonly rights: readonly {code:string;label:string}[] }
export interface AdminAccessConfirmationState { readonly snapshot: AdminAccessConfirmationContext; readonly steps: readonly AdminAccessConfirmationStep[]; readonly stepIndex:number; readonly step:AdminAccessConfirmationStep }
function same(left: readonly string[],right: readonly string[]): boolean { return left.length === right.length && left.every((code,index) => code === right[index]) }
function matches(snapshot:AdminAccessConfirmationContext,current:AdminAccessConfirmationContext): boolean {
  return snapshot.ownerKey === current.ownerKey && snapshot.userId === current.userId && snapshot.userName === current.userName && snapshot.expectedVersion === current.expectedVersion && snapshot.mode === current.mode && same(snapshot.permissions,current.permissions) && same(snapshot.before,current.before) && same(snapshot.after,current.after)
}
/** Consume one immutable draft once, only after every revoke/grant confirmation. */
export class AdminAccessConfirmationFlow {
  private state:AdminAccessConfirmationState | null = null
  start(context:AdminAccessConfirmationContext,catalog:readonly AdminPermission[]):AdminAccessConfirmationState | null {
    this.cancel()
    const steps:AdminAccessConfirmationStep[] = []
    for(const kind of ['revoke','grant'] as const) {
      const codes = kind === 'revoke' ? context.before.filter(code => !context.after.includes(code)) : context.after.filter(code => !context.before.includes(code))
      const rights = codes.map(code => catalog.find(permission => permission.code === code))
      if(rights.some(permission => !permission)) return null
      if(codes.length) steps.push(Object.freeze({kind,rights:Object.freeze(rights.map(permission => Object.freeze({code:permission!.code,label:permission!.label})))}))
    }
    if(!steps.length) return null
    const snapshot = Object.freeze({...context,permissions:Object.freeze([...context.permissions]),before:Object.freeze([...context.before]),after:Object.freeze([...context.after])})
    this.state = Object.freeze({snapshot,steps:Object.freeze(steps),stepIndex:0,step:steps[0]!})
    return this.state
  }
  confirm(current:AdminAccessConfirmationContext):{kind:'invalid'} | {kind:'next';state:AdminAccessConfirmationState} | {kind:'commit';snapshot:AdminAccessConfirmationContext} {
    const state=this.state
    if(!state || !matches(state.snapshot,current)) { this.cancel();return {kind:'invalid'} }
    const step=state.steps[state.stepIndex+1]
    if(step) { this.state=Object.freeze({...state,stepIndex:state.stepIndex+1,step});return {kind:'next',state:this.state} }
    this.cancel();return {kind:'commit',snapshot:state.snapshot}
  }
  cancel():void { this.state=null }
}
