import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import type {
  AdminUser,
  AdminManagedRole,
  AdminRoleStatus,
} from '../../../../mobile-core/src/features/admin-users/admin-users-client'

export interface UserRegistryFilters {
  search: string
  role: AdminManagedRole | ''
  roleStatus: AdminRoleStatus | ''
  status: AdminUser['status'] | ''
  groupId?: number
  sortDirection: 'asc' | 'desc'
}
const one = (value: LocationQuery[string] | undefined) => (typeof value === 'string' ? value : '')
const positive = (value: string) =>
  /^[1-9][0-9]*$/.test(value) && Number.isSafeInteger(Number(value)) ? Number(value) : undefined
export function readUserRegistryQuery(query: LocationQuery): {
  filters: UserRegistryFilters
  page: number
} {
  const candidateRole = one(query.role),
    candidateRoleStatus = one(query.roleStatus),
    candidateStatus = one(query.status)
  const role = ['STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN'].includes(candidateRole)
    ? (candidateRole as AdminManagedRole)
    : ''
  const groupId = positive(one(query.groupId))
  return {
    filters: {
      search: one(query.search),
      role,
      roleStatus:
        role &&
        ['ACTIVE', 'EXPELLED', 'GRADUATED', 'SUSPENDED', 'DISMISSED', 'ARCHIVED'].includes(
          candidateRoleStatus,
        )
          ? (candidateRoleStatus as AdminRoleStatus)
          : '',
      status: ['ACTIVE', 'EXPELLED', 'SUSPENDED', 'ARCHIVED'].includes(candidateStatus)
        ? (candidateStatus as AdminUser['status'])
        : '',
      ...(groupId ? { groupId } : {}),
      sortDirection: one(query.sortDirection) === 'desc' ? 'desc' : 'asc',
    },
    page: positive(one(query.page)) ?? 0,
  }
}
export function userRegistryLocation(filters: UserRegistryFilters, page = 0): LocationQueryRaw {
  return {
    ...(filters.search ? { search: filters.search } : {}),
    ...(filters.role ? { role: filters.role } : {}),
    ...(filters.role && filters.roleStatus ? { roleStatus: filters.roleStatus } : {}),
    ...(filters.status ? { status: filters.status } : {}),
    ...(filters.groupId ? { groupId: String(filters.groupId) } : {}),
    ...(filters.sortDirection === 'desc' ? { sortDirection: 'desc' } : {}),
    ...(page ? { page: String(page) } : {}),
  }
}
export function userRegistryExportSelector(filters: UserRegistryFilters) {
  return {
    search: filters.search,
    ...(filters.role ? { role: filters.role } : {}),
    ...(filters.role && filters.roleStatus ? { roleStatus: filters.roleStatus } : {}),
    ...(filters.status ? { status: filters.status } : {}),
    ...(filters.groupId ? { groupId: filters.groupId } : {}),
    sorts: ['lastName', 'firstName', 'middleName', 'id'].map((field) => ({
      field,
      descending: filters.sortDirection === 'desc',
    })),
  }
}
