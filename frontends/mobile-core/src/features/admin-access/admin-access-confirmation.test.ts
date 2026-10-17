import { describe, expect, it } from 'vitest'
import {
  AdminAccessConfirmationFlow,
  type AdminAccessConfirmationContext,
} from './admin-access-confirmation'
import type { AdminPermission } from './admin-access-client'
const context: AdminAccessConfirmationContext = {
  ownerKey: 'actor99-version5-target3-version4',
  userId: 3,
  userName: 'Синтетический пользователь',
  expectedVersion: 4,
  mode: 'LIMITED',
  permissions: ['USERS_VIEW', 'USERS_MANAGE'],
  before: ['USERS_VIEW', 'GROUPS_VIEW'],
  after: ['USERS_VIEW', 'USERS_MANAGE'],
}
const catalog: AdminPermission[] = ['USERS_VIEW', 'GROUPS_VIEW', 'USERS_MANAGE'].map((code) => ({
  code,
  label: code,
  group: 'users',
  description: 'Synthetic',
  requires: [],
  fullOnly: false,
}))
describe('Admin access immutable confirmation', () => {
  it.each([
    { ownerKey: 'new-authority' },
    { userId: 42 },
    { expectedVersion: 5 },
    { permissions: ['USERS_VIEW'] },
    { after: ['GROUPS_VIEW'] },
  ])('invalidates a changed authority/target/version/draft snapshot %#', (delta) => {
    const flow = new AdminAccessConfirmationFlow()
    expect(flow.start(context, catalog)?.step.kind).toBe('revoke')
    expect(flow.confirm({ ...context, ...delta })).toEqual({ kind: 'invalid' })
    expect(flow.confirm(context)).toEqual({ kind: 'invalid' })
  })
  it('consumes the frozen command once after both steps and refuses a missing catalogue right', () => {
    const flow = new AdminAccessConfirmationFlow()
    expect(flow.start(context, catalog.slice(0, 2))).toBe(null)
    expect(flow.start(context, catalog)?.snapshot.permissions).not.toBe(context.permissions)
    expect(flow.confirm(context).kind).toBe('next')
    const final = flow.confirm(context)
    expect(final.kind).toBe('commit')
    if (final.kind === 'commit') expect(final.snapshot.expectedVersion).toBe(4)
    expect(flow.confirm(context).kind).toBe('invalid')
  })
})
