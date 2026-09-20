import path from 'node:path'
import { pathToFileURL } from 'node:url'
const { createServer } = await import(pathToFileURL(path.resolve('frontends/node_modules/vite/dist/node/index.js')).href)
const { createRenderer, h, nextTick } = await import(pathToFileURL(path.resolve('frontends/node_modules/vue/index.js')).href)
const { default: vue } = await import(pathToFileURL(path.resolve('frontends/node_modules/@vitejs/plugin-vue/dist/index.mjs')).href)

const appRoot = path.resolve('frontends/mobile-core')
const vite = await createServer({
  root: appRoot,
  appType: 'custom',
  logLevel: 'error',
  plugins: [vue()],
  server: { middlewareMode: true },
})

const vueModuleUrl = pathToFileURL(path.resolve('frontends/node_modules/vue/index.js')).href
const navigationModuleUrl = pathToFileURL(path.resolve('frontends/mobile-core/src/shared/navigation.ts')).href
const shellContractModuleUrl = pathToFileURL(path.resolve('frontends/mobile-core/src/shared/shell-contract.ts')).href
const { rootRoute } = await import(navigationModuleUrl)
const transformed = await vite.transformRequest('/src/shared/components/MobileShell.vue')
let componentCode = transformed.code
  .replace(/^import \{ createHotContext[\s\S]*?;import /, 'import ')
  .replace('import MobileBottomNav from "/src/shared/components/MobileBottomNav.vue";', 'const MobileBottomNav = { render: () => null };')
  .replace('import {\n  rootRoute\n} from "/src/shared/navigation.ts";', `import { rootRoute } from "${navigationModuleUrl}";`)
  .replace('import { shouldShowHostBack, shouldShowMobileDock, shouldShowProductBack } from "/src/shared/shell-contract.ts";', `import { shouldShowHostBack, shouldShowMobileDock, shouldShowProductBack } from "${shellContractModuleUrl}";`)
  .replace('import "/src/shared/components/mobile-shell.pcss";', '')
  .replace(/from "\/node_modules\/.vite\/deps\/vue\.js\?v=[^"]+"/g, `from "${vueModuleUrl}"`)
const hmrStart = componentCode.indexOf('_sfc_main.__hmrId')
componentCode = `${componentCode.slice(0, hmrStart)}export default Object.assign(_sfc_main, { render: _sfc_render });`
const componentModuleUrl = `data:text/javascript,${encodeURIComponent(componentCode)}`
const { default: MobileShell } = await import(componentModuleUrl)
let keyboardListener
const host = {
  backOwner: 'none',
  subscribeKeyboard(listener) {
    keyboardListener = listener
    return () => { keyboardListener = undefined }
  },
}
const navItems = [
  { id: 'today', label: 'Сегодня', icon: 'today.svg', route: 'today' },
  { id: 'homework', label: 'Задания', icon: 'homework.svg', route: 'homework', disabled: true },
  { id: 'attendance', label: 'Учёт', icon: 'attendance.svg', route: 'attendance', disabled: true },
  { id: 'more', label: 'Ещё', icon: 'more.svg', route: 'more', disabled: true },
  { id: 'profile', label: 'Профиль', icon: 'profile.svg', route: 'profile', disabled: true },
]
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
const app = renderer.createApp({ render: () => h(MobileShell, { navItems, activeId: 'today', route: rootRoute('today'), host }) })
app.mount(root)
await nextTick()
const shell = root.children[0]
const before = shell?.props?.['data-dock-visible']
keyboardListener?.(true)
await nextTick()
const after = shell?.props?.['data-dock-visible']
const prop = MobileShell.props?.keyboardVisible
const propDefaultResult = typeof prop?.default === 'function' ? prop.default() : prop?.default
console.log(JSON.stringify({ before, after, propType: prop?.type?.name, propDefault: propDefaultResult === undefined ? 'undefined' : typeof propDefaultResult }))
app.unmount()
await vite.close()
process.exit(0)
