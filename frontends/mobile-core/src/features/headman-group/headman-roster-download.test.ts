import { createRenderer, createSSRApp, markRaw, nextTick } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { describe, expect, it, vi } from 'vitest'
import HeadmanGroupScreen from './HeadmanGroupScreen.vue'
import {
  createGenerationBoundHeadmanGroupApi,
  HeadmanGroupApi,
  type HeadmanRosterFormat,
} from './headman-group-client'
import { StaleSessionGenerationError } from '../../shared/session-owner'

class HostNode {
  children: HostNode[] = []
  parent: HostNode | null = null
  props: Record<string, unknown> = {}
  text = ''
  value: unknown = ''
  open = false
  get options(): HostNode[] {
    return this.children.filter((child) => child.tag === 'option')
  }
  constructor(readonly tag: string) {
    markRaw(this)
  }
  close(): void {
    this.open = false
  }
  showModal(): void {
    this.open = true
  }
  focus(): void {}
  addEventListener(): void {}
  getRootNode(): Document {
    return document
  }
  querySelectorAll(): HostNode[] {
    return []
  }
}
const renderer = createRenderer<HostNode, HostNode>({
  createElement: (tag) => new HostNode(tag),
  createText: (text) => Object.assign(new HostNode('#text'), { text }),
  createComment: () => new HostNode('#comment'),
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
  insert: (node, parent, anchor = null) => {
    if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    node.parent = parent
  },
  remove: (node) => {
    node.parent?.children.splice(node.parent.children.indexOf(node), 1)
    node.parent = null
  },
})
function text(root: HostNode): string {
  return root.text + root.children.map(text).join(' ')
}
function find(root: HostNode, predicate: (node: HostNode) => boolean): HostNode | undefined {
  if (predicate(root)) return root
  for (const child of root.children) {
    const match = find(child, predicate)
    if (match) return match
  }
  return undefined
}
async function settle(): Promise<void> {
  for (let index = 0; index < 30; index++) {
    await Promise.resolve()
    await nextTick()
  }
}

describe('personal roster session boundary', () => {
  it('lets the current headman increase their own student budget without configuring their own helper rights', async () => {
    const api = markRaw(new HeadmanGroupApi({ accessToken: () => 'synthetic' }))
    vi.spyOn(api, 'listMembers').mockResolvedValue([
      {
        id: 101,
        fullName: 'Тестовый староста',
        login: 'synthetic',
        status: 'ACTIVE',
        groupId: 8,
        roles: [{ role: 'HEADMAN', status: 'ACTIVE', groupId: 8 }],
      },
    ])
    vi.spyOn(api, 'listPermissionCatalog').mockResolvedValue([])
    vi.spyOn(api, 'listAssistants').mockResolvedValue([])
    vi.spyOn(api, 'listRosterFormats').mockResolvedValue([])
    const before = { studentId: 101, semesterId: 30, limit: 5, used: 5, remaining: 0 }
    const after = { ...before, limit: 7, remaining: 2 }
    const read = vi
      .spyOn(api, 'getLateCheckinBudget')
      .mockResolvedValueOnce(before)
      .mockResolvedValueOnce(after)
    const increase = vi.spyOn(api, 'increaseLateCheckinBudget').mockResolvedValue(after)
    class HostDocument {
      activeElement = null
      addEventListener(): void {}
      removeEventListener(): void {}
    }
    vi.stubGlobal('Document', HostDocument)
    vi.stubGlobal('document', new HostDocument())
    const root = new HostNode('root')
    const app = renderer.createApp(HeadmanGroupScreen, {
      api,
      groupId: 8,
      actorUserId: '101',
      actorHeadman: true,
      assistantPermissions: null,
    })
    try {
      app.mount(root)
      await settle()
      const member = find(root, (node) => node.props['data-member-id'] === 101)
      expect(member).toBeDefined()
      ;(member!.props.onClick as () => void)()
      await settle()
      expect(text(root)).toContain('Осталось попыток: 0 из 5')
      expect(text(root)).not.toContain('Настроить права')
      const input = find(root, (node) => node.props.inputmode === 'numeric')
      expect(input).toBeDefined()
      expect(input!.props.disabled).toBe(false)
      ;(input!.props['onUpdate:modelValue'] as (value: string) => void)('2')
      await settle()
      const button = find(
        root,
        (node) => node.tag === 'button' && text(node).trim() === 'Увеличить лимит',
      )
      expect(button).toBeDefined()
      expect(button!.props.disabled).toBe(false)
      ;(button!.props.onClick as () => void)()
      await settle()
      expect(increase).toHaveBeenCalledExactlyOnceWith(101, {
        semesterId: 30,
        increase: 2,
        requestKey: expect.any(String),
      })
      expect(read).toHaveBeenCalledTimes(2)
      expect(text(root)).toContain('Осталось попыток: 2 из 7')
      expect(text(root)).not.toContain('Настроить права')
    } finally {
      app.unmount()
      await nextTick()
      vi.unstubAllGlobals()
    }
  })

  it('reuses the exact budget command after session refresh and reads the current counter separately', async () => {
    const command = { semesterId: 30, increase: 2, requestKey: 'budget-retry-command-01' }
    const snapshot = { studentId: 100, semesterId: 30, limit: 7, used: 5, remaining: 2 }
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(Response.json(snapshot))
      .mockResolvedValueOnce(Response.json({ ...snapshot, used: 6, remaining: 1 }))
    const refresh = vi.fn(async () => undefined)
    const api = new HeadmanGroupApi({
      accessToken: () => 'synthetic',
      onUnauthorized: refresh,
      fetcher,
    })
    expect(await api.increaseLateCheckinBudget(100, command)).toEqual(snapshot)
    expect(refresh).toHaveBeenCalledOnce()
    expect(fetcher.mock.calls[0]![1]?.body).toBe(JSON.stringify(command))
    expect(fetcher.mock.calls[1]![1]?.body).toBe(fetcher.mock.calls[0]![1]?.body)
    expect(await api.getLateCheckinBudget(100)).toMatchObject({ used: 6, remaining: 1 })
    expect(fetcher.mock.calls[2]![0]).toBe(
      '/api/attendance/requests/students/100/late-checkin-budget',
    )
  })

  it('does not send malformed increases or accept another student budget', async () => {
    const fetcher = vi.fn<typeof fetch>(async () =>
      Response.json({
        studentId: 101,
        semesterId: 30,
        limit: 7,
        used: 5,
        remaining: 2,
      }),
    )
    const api = new HeadmanGroupApi({ accessToken: () => 'synthetic', fetcher })
    for (const increase of [0, -1, 1.5, 2147483648])
      await expect(
        api.increaseLateCheckinBudget(100, {
          semesterId: 30,
          increase,
          requestKey: 'budget-invalid-command',
        }),
      ).rejects.toThrow(RangeError)
    expect(fetcher).not.toHaveBeenCalled()
    await expect(api.getLateCheckinBudget(100)).rejects.toThrow('некорректный лимит')
  })

  it('does not expose roster controls or request personal formats for a statistics assistant', async () => {
    const api = new HeadmanGroupApi({ accessToken: () => 'synthetic' })
    vi.spyOn(api, 'listMembers').mockResolvedValue([])
    vi.spyOn(api, 'listPermissionCatalog').mockResolvedValue([])
    vi.spyOn(api, 'listAssistants').mockResolvedValue([])
    const formats = vi.spyOn(api, 'listRosterFormats')
    const html = await renderToString(
      createSSRApp(HeadmanGroupScreen, {
        api,
        groupId: 8,
        assistantPermissions: ['VIEW_STATS'],
      }),
    )
    expect(html).not.toContain('Скачать состав группы')
    expect(html).not.toContain('Формат файла')
    expect(formats).not.toHaveBeenCalled()
  })

  it('rejects personal roster bytes when the account changes during the body read', async () => {
    let generation = 1
    let releaseBlob!: (blob: Blob) => void
    const pendingBlob = new Promise<Blob>((resolve) => {
      releaseBlob = resolve
    })
    const response = new Response(null, {
      headers: {
        'Content-Type': 'application/pdf',
        'Content-Disposition': 'attachment; filename="roster.pdf"',
      },
    })
    const blobRead = vi.spyOn(response, 'blob').mockImplementation(() => pendingBlob)
    const fetcher = vi.fn<typeof fetch>(async () => response)
    const api = createGenerationBoundHeadmanGroupApi(
      {
        currentGeneration: () => generation,
        accessTokenFor: () => 'synthetic-current-bearer',
        refreshFor: async () => undefined,
      },
      fetcher,
    )
    const format: HeadmanRosterFormat = {
      code: 'pdf',
      label: 'PDF',
      contentType: 'application/pdf',
      extension: 'pdf',
    }
    const pending = api.downloadRoster(format)
    const rejected = expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    await vi.waitFor(() => expect(blobRead).toHaveBeenCalledOnce())
    generation = 2
    releaseBlob(new Blob(['synthetic personal roster']))
    await rejected
    expect(fetcher).toHaveBeenCalledOnce()
    expect(fetcher.mock.calls[0]![0]).toBe(
      '/api/attendance/reports/headman/group-composition/export?format=pdf',
    )
  })
})
