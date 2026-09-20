import assert from 'node:assert/strict'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const { createServer } = await import(pathToFileURL(path.resolve('frontends/node_modules/vite/dist/node/index.js')).href)
const { createRenderer, h, nextTick, ref } = await import(pathToFileURL(path.resolve('frontends/node_modules/vue/index.js')).href)
const { default: vue } = await import(pathToFileURL(path.resolve('frontends/node_modules/@vitejs/plugin-vue/dist/index.mjs')).href)

const appRoot = path.resolve('frontends/mobile-core')
const vueModuleUrl = pathToFileURL(path.resolve('frontends/node_modules/vue/index.js')).href
const navigationModuleUrl = pathToFileURL(path.resolve('frontends/mobile-core/src/shared/navigation.ts')).href
const shellContractModuleUrl = pathToFileURL(path.resolve('frontends/mobile-core/src/shared/shell-contract.ts')).href
const vite = await createServer({
  root: appRoot,
  appType: 'custom',
  logLevel: 'error',
  plugins: [vue()],
  server: { middlewareMode: true },
})

function prepareVueModule(code) {
  let moduleCode = code
    .replace(/^import \{ createHotContext[\s\S]*?;import /, 'import ')
    .replace(/from "\/node_modules\/.vite\/deps\/vue\.js\?v=[^"]+"/g, `from "${vueModuleUrl}"`)
  const hmrStart = moduleCode.indexOf('_sfc_main.__hmrId')
  return `${moduleCode.slice(0, hmrStart)}export default Object.assign(_sfc_main, { render: _sfc_render });`
}

function compileVueModule(code) {
  return import(`data:text/javascript,${encodeURIComponent(prepareVueModule(code))}`)
}

function findNodes(node, type, result = []) {
  if (!node) return result
  if (node.type === type) result.push(node)
  for (const child of node.children ?? []) findNodes(child, type, result)
  return result
}

function createHost(name) {
  let keyboardListener
  let backListener
  const state = {
    name,
    keyboardSubscriptions: 0,
    keyboardUnsubscriptions: 0,
    backSubscriptions: 0,
    backUnsubscriptions: 0,
    backVisible: [],
  }
  const adapter = {
    backOwner: 'host',
    subscribeKeyboard(listener) {
      state.keyboardSubscriptions += 1
      keyboardListener = listener
      return () => {
        state.keyboardUnsubscriptions += 1
        if (keyboardListener === listener) keyboardListener = undefined
      }
    },
    subscribeBack(listener) {
      state.backSubscriptions += 1
      backListener = listener
      return () => {
        state.backUnsubscriptions += 1
        if (backListener === listener) backListener = undefined
      }
    },
    setBackVisible(visible) {
      state.backVisible.push(visible)
    },
  }
  return {
    adapter,
    state,
    keyboard(visible) { keyboardListener?.(visible) },
    back() { backListener?.() },
  }
}

const bottomNavTransformed = await vite.transformRequest('/src/shared/components/MobileBottomNav.vue')
const bottomNavCode = bottomNavTransformed.code.replace(/import "[^"]*mobile-bottom-nav\.pcss";/, '')
const shellTransformed = await vite.transformRequest('/src/shared/components/MobileShell.vue')
const bottomNavModuleUrl = `data:text/javascript,${encodeURIComponent(prepareVueModule(bottomNavCode))}`
let shellCode = shellTransformed.code
  .replace(/import MobileBottomNav from "[^"]+";/, `import MobileBottomNav from ${JSON.stringify(bottomNavModuleUrl)};`)
  .replace(/import "[^"]*mobile-shell\.pcss";/, '')
  .replace('import {\n  rootRoute\n} from "/src/shared/navigation.ts";', `import { rootRoute } from "${navigationModuleUrl}";`)
  .replace('import { shouldShowHostBack, shouldShowMobileDock, shouldShowProductBack } from "/src/shared/shell-contract.ts";', `import { shouldShowHostBack, shouldShowMobileDock, shouldShowProductBack } from "${shellContractModuleUrl}";`)
const { default: MobileShell } = await compileVueModule(shellCode)
const { createMobileNavigationStack, nestedRoute, rootRoute } = await import(navigationModuleUrl)

const hostA = createHost('A')
const hostB = createHost('B')
const navigation = createMobileNavigationStack(rootRoute('today'))
const navItems = [
  { id: 'today', label: 'Сегодня', icon: 'today.svg', route: 'today' },
  { id: 'homework', label: 'Задания', icon: 'homework.svg', route: 'homework', disabled: true, disabledReason: 'Раздел пока недоступен' },
  { id: 'attendance', label: 'Учёт', accessibleLabel: 'Посещаемость', icon: 'attendance.svg', route: 'attendance', disabled: true, disabledReason: 'Раздел пока недоступен' },
  { id: 'more', label: 'Ещё', icon: 'more.svg', route: 'more', disabled: true, disabledReason: 'Раздел пока недоступен' },
  { id: 'profile', label: 'Профиль', icon: 'profile.svg', route: 'profile', disabled: true, disabledReason: 'Раздел пока недоступен' },
]
const currentHost = ref(hostA.adapter)

const renderer = createRenderer({
  patchProp(element, key, _previous, next) { element.props[key] = next },
  insert(element, parent, anchor) {
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, element)
    element.parent = parent
  },
  remove(element) { element.parent?.children.splice(element.parent.children.indexOf(element), 1) },
  createElement(type) { return { type, props: {}, children: [], parent: null } },
  createText(text) { return { type: '#text', text, parent: null } },
  createComment(text) { return { type: '#comment', text, parent: null } },
  setText(node, text) { node.text = text },
  setElementText(node, text) { node.children = [{ type: '#text', text, parent: node }] },
  parentNode(node) { return node.parent },
  nextSibling(node) {
    const siblings = node.parent?.children ?? []
    return siblings[siblings.indexOf(node) + 1] ?? null
  },
})
const root = { type: 'root', props: {}, children: [], parent: null }
const app = renderer.createApp({
  render: () => h(MobileShell, {
    navItems,
    activeId: 'today',
    navigation,
    host: currentHost.value,
  }),
})

const snapshot = () => {
  const shell = root.children[0]
  return {
    surface: shell?.props?.['data-surface'],
    dockVisible: shell?.props?.['data-dock-visible'],
    keyboardVisible: shell?.props?.['data-keyboard-visible'],
  }
}

try {
  app.mount(root)
  await nextTick()
  const initial = snapshot()
  const buttons = findNodes(root.children[0], 'button')
  const attendance = buttons.find((button) => button.props['aria-label']?.startsWith('Посещаемость'))
  assert.deepEqual(initial, { surface: 'root', dockVisible: true, keyboardVisible: false })
  assert.equal(attendance?.props['aria-label'], 'Посещаемость. Раздел пока недоступен')

  navigation.push(nestedRoute('today', 'today/checkin', 'task'))
  await nextTick()
  const afterExternalPush = snapshot()
  assert.deepEqual(afterExternalPush, { surface: 'task', dockVisible: false, keyboardVisible: false })

  navigation.replace(rootRoute('today'))
  await nextTick()
  assert.deepEqual(snapshot(), { surface: 'root', dockVisible: true, keyboardVisible: false })

  navigation.push(nestedRoute('today', 'today/checkin', 'task'))
  await nextTick()
  hostA.keyboard(true)
  await nextTick()
  assert.equal(snapshot().keyboardVisible, true)

  currentHost.value = hostB.adapter
  await nextTick()
  assert.equal(snapshot().keyboardVisible, false)
  hostA.keyboard(true)
  await nextTick()
  assert.equal(snapshot().keyboardVisible, false)

  hostA.back()
  await nextTick()
  assert.equal(snapshot().surface, 'task')
  hostB.back()
  await nextTick()
  assert.equal(snapshot().surface, 'root')
  assert.equal(hostA.state.keyboardSubscriptions, 1)
  assert.equal(hostA.state.keyboardUnsubscriptions, 1)
  assert.equal(hostA.state.backSubscriptions, 1)
  assert.equal(hostA.state.backUnsubscriptions, 1)
  assert.equal(hostB.state.keyboardSubscriptions, 1)
  assert.equal(hostB.state.backSubscriptions, 1)
  assert.ok(hostA.state.backVisible.includes(false))

  app.unmount()
  assert.equal(hostB.state.keyboardUnsubscriptions, 1)
  assert.equal(hostB.state.backUnsubscriptions, 1)
  assert.equal(hostB.state.backVisible.at(-1), false)

  console.log(JSON.stringify({
    initial,
    afterExternalPush,
    afterExternalReplace: { surface: 'root', dockVisible: true },
    accessibleName: attendance?.props['aria-label'],
    hostA: hostA.state,
    hostB: hostB.state,
  }))
} finally {
  app.unmount()
  await vite.close()
}
