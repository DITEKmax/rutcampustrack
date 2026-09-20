import assert from 'node:assert/strict'
import test from 'node:test'
import { nestedRoute, createMobileNavigationStack, rootRoute } from '../src/shared/navigation.ts'
import { shouldShowHostBack, shouldShowMobileDock, shouldShowProductBack } from '../src/shared/shell-contract.ts'

test('navigation stack returns from nested route to its root and then stops', () => {
  const stack = createMobileNavigationStack(rootRoute('today'))

  stack.push(nestedRoute('today', 'today/checkin', 'task'))
  assert.equal(stack.current.id, 'today/checkin')
  assert.equal(stack.back()?.id, 'today')
  assert.equal(stack.back(), null)
  assert.equal(stack.current.id, 'today')
})

test('initial nested route seeds its owning root before the detail', () => {
  const stack = createMobileNavigationStack(nestedRoute('homework', 'homework/detail', 'detail'))

  assert.deepEqual(stack.entries.map((route) => route.id), ['homework', 'homework/detail'])
  assert.equal(stack.back()?.id, 'homework')
  assert.equal(stack.back(), null)
})

test('cross-root nested navigation starts the owning root history', () => {
  const stack = createMobileNavigationStack(rootRoute('today'))

  stack.push(nestedRoute('homework', 'homework/detail', 'detail'))

  assert.deepEqual(stack.entries.map((route) => route.id), ['homework', 'homework/detail'])
  assert.equal(stack.back()?.id, 'homework')
  assert.equal(stack.back(), null)
})

test('replacing a root with a nested route also preserves its owning root', () => {
  const stack = createMobileNavigationStack(rootRoute('today'))

  stack.replace(nestedRoute('homework', 'homework/detail', 'detail'))

  assert.deepEqual(stack.entries.map((route) => route.id), ['homework', 'homework/detail'])
  assert.equal(stack.back()?.id, 'homework')
})

test('external stack mutations notify subscribers once and can be unsubscribed', () => {
  const stack = createMobileNavigationStack(rootRoute('today'))
  let notifications = 0
  const unsubscribe = stack.subscribe(() => { notifications += 1 })

  stack.push(nestedRoute('today', 'today/checkin', 'task'))
  stack.replace(nestedRoute('today', 'today/checkin-confirm', 'task'))
  assert.equal(notifications, 2)

  unsubscribe()
  stack.back()
  assert.equal(notifications, 2)
})

test('root navigation clears the previous nested route', () => {
  const stack = createMobileNavigationStack(rootRoute('today'))
  stack.push(nestedRoute('homework', 'homework/detail', 'detail'))

  stack.goRoot('profile')

  assert.deepEqual(stack.entries.map((route) => route.id), ['profile'])
})

test('profile detail navigation returns to profile root and keeps one owning stack', () => {
  const stack = createMobileNavigationStack(rootRoute('today'))

  stack.goRoot('profile')
  stack.push(nestedRoute('profile', 'profile/sessions', 'detail'))

  assert.deepEqual(stack.entries.map((route) => route.id), ['profile', 'profile/sessions'])
  assert.equal(stack.back()?.id, 'profile')
  assert.equal(stack.back(), null)
})

test('shell visibility owns dock and Back by route surface and keyboard', () => {
  const root = rootRoute('today')
  const overview = nestedRoute('homework', 'homework/overview', 'overview')
  const detail = nestedRoute('homework', 'homework/detail', 'detail')
  const profileRoot = rootRoute('profile')
  const profileDetail = nestedRoute('profile', 'profile/history', 'detail')

  assert.equal(shouldShowMobileDock(root, false), true)
  assert.equal(shouldShowMobileDock(root, true), false)
  assert.equal(shouldShowMobileDock(overview, false), true)
  assert.equal(shouldShowMobileDock(detail, false), false)
  assert.equal(shouldShowProductBack(detail, 'product'), true)
  assert.equal(shouldShowProductBack(detail, 'host'), false)
  assert.equal(shouldShowHostBack(detail, 'host'), true)
  assert.equal(shouldShowHostBack(root, 'host'), false)
  assert.equal(shouldShowMobileDock(profileRoot, false), true)
  assert.equal(shouldShowMobileDock(profileRoot, true), false)
  assert.equal(shouldShowMobileDock(profileDetail, false), false)
  assert.equal(shouldShowHostBack(profileDetail, 'host'), true)
})
