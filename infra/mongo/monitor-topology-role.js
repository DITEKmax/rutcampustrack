// Explicit operator action for an initialized database; never run on app startup.
// Authenticate separately. This script reads no credentials or application data.
// Before installation capture getUser('rct_monitor').roles and getRole below.
// Rollback: revoke only the newly granted rct_monitor_topology role; drop that
// role only if it was created by this installation and has no other grantees.
(() => {
  const monitorAdmin = db.getSiblingDB('admin')
  const monitorUser = monitorAdmin.getUser('rct_monitor')
  if (!monitorUser || !monitorUser.roles.some((r) => r.role === 'clusterMonitor' && r.db === 'admin')) {
    throw new Error('Expected existing diagnostic monitor user is absent')
  }
  const topologyRoleName = 'rct_monitor_topology'
  const topologyRole = monitorAdmin.getRole(topologyRoleName, { showPrivileges: true })
  if (topologyRole) {
    const privileges = topologyRole.privileges
    const privilege = privileges && privileges[0]
    if (!Array.isArray(topologyRole.roles) || topologyRole.roles.length !== 0
        || !Array.isArray(privileges) || privileges.length !== 1
        || privilege.resource.db !== 'admin' || privilege.resource.collection !== 'system.version'
        || Object.keys(privilege.resource).length !== 2
        || privilege.actions.length !== 1 || privilege.actions[0] !== 'find') {
      throw new Error('Existing topology role differs from the approved metadata-only privilege')
    }
  } else {
    monitorAdmin.createRole({
      role: topologyRoleName,
      privileges: [{ resource: { db: 'admin', collection: 'system.version' }, actions: ['find'] }],
      roles: [],
    })
  }
  if (!monitorUser.roles.some((r) => r.role === topologyRoleName && r.db === 'admin')) {
    monitorAdmin.grantRolesToUser('rct_monitor', [{ role: topologyRoleName, db: 'admin' }])
  }
  print('PASS diagnostic monitor has the approved metadata-only topology role')
})()
