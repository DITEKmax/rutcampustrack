// Real MongoDB role/idempotence regression; invoke only in the owned scratch DB.
if (process.env.RCT_MONITOR_TOPOLOGY_SCRATCH_ONLY !== '1') {
  throw new Error('Topology role cases require the explicit isolated scratch marker')
}
(() => {
  const assert = require('node:assert/strict')
  const admin = db.getSiblingDB('admin')
  const roleName = 'rct_monitor_topology'
  const roleRef = { role: roleName, db: 'admin' }
  const privilege = { resource: { db: 'admin', collection: 'system.version' }, actions: ['find'] }
  const helper = '/monitor-topology-role.js'
  const roles = () => JSON.stringify(admin.getUser('rct_monitor').roles)
  const metadataRole = () => admin.getRole(roleName, { showPrivileges: true })
  const granted = () => admin.getUser('rct_monitor').roles.some((r) => r.role === roleName && r.db === 'admin')

  // Existing correct role + grant must pass without changing either.
  const originalRoles = roles()
  const originalRole = JSON.stringify(metadataRole())
  load(helper)
  assert.strictEqual(roles(), originalRoles)
  assert.strictEqual(JSON.stringify(metadataRole()), originalRole)
  print('PASS topology_existing_correct')

  // Existing correct role with missing user grant must recover the grant only.
  admin.revokeRolesFromUser('rct_monitor', [roleRef])
  load(helper)
  assert(granted())
  assert.strictEqual(JSON.stringify(metadataRole()), originalRole)
  print('PASS topology_missing_grant')

  // Recover a missing role, then execute again in the same shell context.
  admin.revokeRolesFromUser('rct_monitor', [roleRef])
  admin.dropRole(roleName)
  load(helper)
  const recreatedRole = JSON.stringify(metadataRole())
  const recreatedRoles = roles()
  load(helper)
  assert.strictEqual(JSON.stringify(metadataRole()), recreatedRole)
  assert.strictEqual(roles(), recreatedRoles)
  assert(granted())
  print('PASS topology_repeated_install')

  // Unexpected wider privileges must fail before changing any role or grant.
  admin.revokeRolesFromUser('rct_monitor', [roleRef])
  admin.updateRole(roleName, { privileges: [{ resource: { db: 'admin', collection: '' }, actions: ['find'] }] })
  const unexpectedRole = JSON.stringify(metadataRole())
  const beforeRejectedGrant = roles()
  let rejected = false
  try { load(helper) } catch (error) {
    rejected = error.message.includes('Existing topology role differs from the approved metadata-only privilege')
  }
  assert(rejected)
  assert.strictEqual(JSON.stringify(metadataRole()), unexpectedRole)
  assert.strictEqual(roles(), beforeRejectedGrant)
  admin.updateRole(roleName, { privileges: [privilege], roles: [] })
  load(helper)
  assert(granted())
  print('PASS topology_unexpected_privilege_rejected')
})()
