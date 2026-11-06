import { createRenderer, h, markRaw, nextTick, shallowReactive } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { BrowserLoginPreview } from '../../features/profile/browser-login-approval'
import {
  DEFAULT_PASSWORD_POLICY,
  type ProfilePort,
  type ProfileSnapshot,
} from '../../features/profile/profile-types'
import AdminProfileOwner from './AdminProfileOwner.vue'

class TestNode {
  children: TestNode[] = []
  parent: TestNode | null = null
  props: Record<string, unknown> = {}
  text = ''
  readyState = 2
  srcObject: unknown = null

  constructor(readonly tag: string) {
    markRaw(this)
  }

  play(): Promise<void> {
    return Promise.resolve()
  }
}

function insert(node: TestNode, parent: TestNode, anchor: TestNode | null = null): void {
  const index = anchor ? parent.children.indexOf(anchor) : parent.children.length
  parent.children.splice(index, 0, node)
  node.parent = parent
}

const renderer = createRenderer<TestNode, TestNode>({
  createElement: (tag) => new TestNode(tag),
  createText: (text) => Object.assign(new TestNode('#text'), { text }),
  createComment: (text) => Object.assign(new TestNode('#comment'), { text }),
  setText: (node, text) => {
    node.text = text
  },
  setElementText: (node, text) => {
    node.text = text
    node.children = []
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _old, value) => {
    node.props[key] = value
  },
  insert,
  insertStaticContent(content, parent, anchor) {
    const node = Object.assign(new TestNode('#static'), { text: content })
    insert(node, parent, anchor)
    return [node, node]
  },
  remove: (node) => {
    if (!node.parent) return
    node.parent.children.splice(node.parent.children.indexOf(node), 1)
    node.parent = null
  },
})

function nodes(node: TestNode): TestNode[] {
  return [node, ...node.children.flatMap(nodes)]
}
function text(node: TestNode): string {
  return node.text + node.children.map(text).join(' ')
}
function button(root: TestNode, label: string): TestNode | undefined {
  return nodes(root).find(
    (node) =>
      node.tag === 'button' && (node.props['aria-label'] === label || text(node).trim() === label),
  )
}
function click(root: TestNode, label: string): void {
  const target = button(root, label)
  expect(target).toBeDefined()
  ;(target?.props.onClick as () => void)()
}
async function settle(): Promise<void> {
  for (let step = 0; step < 14; step++) {
    await Promise.resolve()
    await nextTick()
  }
}

const profile: ProfileSnapshot = {
  sessionId: 'synthetic-session',
  userId: 'synthetic-admin',
  displayName: 'Синтетический профиль администратора',
  sessionVersion: '1',
  rolesVersion: '1',
  activeRole: 'ADMIN',
  roles: [{ grantId: 'admin', role: 'ADMIN', status: 'ACTIVE', selectable: true, readOnly: false }],
  readOnly: false,
  passwordPolicy: DEFAULT_PASSWORD_POLICY,
}

afterEach(() => vi.unstubAllGlobals())

describe('administrator browser login authority', () => {
  it.each(['sessionVersion', 'rolesVersion'] as const)(
    'drops an outstanding scan on %s drift and restores the scanner only after verification',
    async (version) => {
      const refreshed = { ...profile, [version]: '2' }
      let finishReconnect!: (snapshot: ProfileSnapshot) => void
      const reconnect = new Promise<ProfileSnapshot>((resolve) => {
        finishReconnect = resolve
      })
      let finishPreview!: (preview: BrowserLoginPreview) => void
      const preview = vi.fn(
        () =>
          new Promise<BrowserLoginPreview>((resolve) => {
            finishPreview = resolve
          }),
      )
      const decide = vi.fn()
      const port: ProfilePort = {
        isOnline: () => true,
        browserLoginApproval: { isOnline: () => true, preview, decide },
        getSnapshot: vi
          .fn()
          .mockResolvedValueOnce(profile)
          .mockResolvedValueOnce(profile)
          .mockReturnValueOnce(reconnect),
        selectRole: vi.fn(),
        listSessions: vi.fn(),
        listHistory: vi.fn(),
        changePassword: vi.fn(),
        logoutAll: vi.fn(),
      }
      const proof = {
        version: 1,
        purpose: 'LOGIN',
        challengeId: '00000000-0000-4000-8000-000000000004',
        approvalToken: 'x'.repeat(43),
      }
      vi.stubGlobal(
        'BarcodeDetector',
        class {
          async detect() {
            return [{ rawValue: JSON.stringify(proof) }]
          }
        },
      )
      const stop = vi.fn()
      const acquire = vi.fn().mockResolvedValue({ getTracks: () => [{ stop }] })
      vi.stubGlobal('navigator', { mediaDevices: { getUserMedia: acquire } })
      vi.stubGlobal('window', new EventTarget())
      vi.stubGlobal('document', Object.assign(new EventTarget(), { visibilityState: 'visible' }))

      const state = shallowReactive({ profile, offline: false })
      const root = new TestNode('root')
      const app = renderer.createApp({
        render: () => h(AdminProfileOwner, { ...state, profilePort: port }),
      })
      app.mount(root)
      try {
        await settle()
        expect(text(root)).toContain('Администратор')
        click(root, 'Вход в браузере')
        await settle()
        click(root, 'Сканировать QR')
        await settle()
        expect(preview).toHaveBeenCalledOnce()
        expect(stop).toHaveBeenCalledOnce()

        state.profile = refreshed
        await settle()
        expect(button(root, 'Сканировать QR')).toBeUndefined()
        state.offline = true
        await settle()
        state.offline = false
        await settle()
        expect(button(root, 'Сканировать QR')).toBeUndefined()
        finishReconnect(refreshed)
        await settle()
        expect(button(root, 'Сканировать QR')?.props.disabled).toBe(false)

        finishPreview({
          challengeId: proof.challengeId,
          purpose: 'LOGIN',
          status: 'pending',
          browserLabel: 'Устаревший запрос',
          issuedAt: new Date().toISOString(),
          expiresAt: new Date(Date.now() + 60_000).toISOString(),
          warning: 'Синтетическая проверка',
        })
        await settle()
        expect(text(root)).not.toContain('Устаревший запрос')
        expect(decide).not.toHaveBeenCalled()
        expect(acquire).toHaveBeenCalledOnce()
        click(root, 'Назад')
        await settle()
        expect(button(root, 'Вход в браузере')).toBeDefined()
      } finally {
        app.unmount()
      }
    },
  )
})
