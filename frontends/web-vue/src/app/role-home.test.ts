import { describe, expect, it } from 'vitest'
import type { RouteRecordRaw } from 'vue-router'
import type { ProfileRole } from '../auth/session'
import { chooseRoleHome, internalReturnPath } from './role-home'

function route(name: string, roles: readonly ProfileRole[], extra = {}): RouteRecordRaw {
  return {
    name,
    path: '/' + name.replace('-', '/'),
    component: {},
    meta: { requiredRoles: roles, ...extra },
  }
}
const adminRoutes = [
  route('admin-dashboard', ['ADMIN']),
  route('admin-users', ['ADMIN']),
  route('admin-groups', ['ADMIN']),
  route('admin-semesters', ['ADMIN']),
]
describe('canonical admitted role landing', () => {
  it('chooses the student own home rather than an earlier delegated headman page or shared feature', () => {
    const routes = [
      route('map', ['STUDENT']),
      route('headman-attendance', ['STUDENT', 'HEADMAN']),
      route('student-schedule', ['STUDENT']),
      route('student-home', ['STUDENT']),
    ]
    expect(chooseRoleHome('STUDENT', routes)?.name).toBe('student-home')
    expect(chooseRoleHome('STUDENT', routes.slice(0, 2))).toBeUndefined()
  })
  it('prefers each own canonical home over the registry order and excludes hidden detail routes', () => {
    const routes = [
      route('headman-statistics', ['HEADMAN']),
      route('teacher-stats', ['TEACHER']),
      route('headman-home', ['HEADMAN']),
      route('teacher-home', ['TEACHER']),
      route('student-home', ['STUDENT'], { navHidden: true }),
    ]
    expect(chooseRoleHome('HEADMAN', routes)?.name).toBe('headman-home')
    expect(chooseRoleHome('TEACHER', routes)?.name).toBe('teacher-home')
    expect(chooseRoleHome('STUDENT', routes)).toBeUndefined()
  })
  it('enters dashboard only with current assigned dashboard access, otherwise the first permitted ADMIN page', () => {
    const limited = {
      userId: 11,
      assigned: true,
      mode: 'LIMITED' as const,
      version: 1,
      permissions: ['GROUPS_VIEW'],
    }
    expect(chooseRoleHome('ADMIN', adminRoutes, limited)?.name).toBe('admin-groups')
    expect(
      chooseRoleHome('ADMIN', adminRoutes, { ...limited, permissions: ['DASHBOARD_VIEW'] })?.name,
    ).toBe('admin-dashboard')
    expect(chooseRoleHome('ADMIN', adminRoutes, { ...limited, mode: 'FULL' })?.name).toBe(
      'admin-dashboard',
    )
    expect(
      chooseRoleHome('ADMIN', adminRoutes, { ...limited, assigned: false, mode: 'FULL' }),
    ).toBeUndefined()
    expect(
      chooseRoleHome('ADMIN', adminRoutes, { ...limited, permissions: ['DATA_DELETE'] }),
    ).toBeUndefined()
    expect(chooseRoleHome('ADMIN', adminRoutes)).toBeUndefined()
  })
  it('retains internal path query/hash but rejects external, ambiguous and control-character return targets', () => {
    expect(internalReturnPath('/student/homework?date=2026-10-24&mode=day#homework-1')).toBe(
      '/student/homework?date=2026-10-24&mode=day#homework-1',
    )
    for (const target of [
      'https://example.org',
      '//example.org',
      '/\\example.org',
      '/\n/host',
      'javascript:alert(1)',
      ['/student/home'],
    ])
      expect(internalReturnPath(target)).toBeNull()
  })
})
