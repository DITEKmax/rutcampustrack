import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { describe, expect, it, vi } from 'vitest'
import HeadmanGroupScreen from './HeadmanGroupScreen.vue'
import { createGenerationBoundHeadmanGroupApi, HeadmanGroupApi, type HeadmanRosterFormat } from './headman-group-client'
import { StaleSessionGenerationError } from '../../shared/session-owner'

describe('personal roster session boundary', () => {
  it('does not expose roster controls or request personal formats for a statistics assistant', async () => {
    const api = new HeadmanGroupApi({ accessToken: () => 'synthetic' })
    vi.spyOn(api, 'listMembers').mockResolvedValue([])
    vi.spyOn(api, 'listPermissionCatalog').mockResolvedValue([])
    vi.spyOn(api, 'listAssistants').mockResolvedValue([])
    const formats = vi.spyOn(api, 'listRosterFormats')
    const html = await renderToString(createSSRApp(HeadmanGroupScreen, {
      api, groupId: 8, assistantPermissions: ['VIEW_STATS'],
    }))
    expect(html).not.toContain('Скачать состав группы')
    expect(html).not.toContain('Формат файла')
    expect(formats).not.toHaveBeenCalled()
  })

  it('rejects personal roster bytes when the account changes during the body read', async () => {
    let generation = 1
    let releaseBlob!: (blob: Blob) => void
    const pendingBlob = new Promise<Blob>((resolve) => { releaseBlob = resolve })
    const response = new Response(null, { headers: {
      'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="roster.pdf"',
    } })
    const blobRead = vi.spyOn(response, 'blob').mockImplementation(() => pendingBlob)
    const fetcher = vi.fn<typeof fetch>(async () => response)
    const api = createGenerationBoundHeadmanGroupApi({
      currentGeneration: () => generation,
      accessTokenFor: () => 'synthetic-current-bearer',
      refreshFor: async () => undefined,
    }, fetcher)
    const format: HeadmanRosterFormat = { code: 'pdf', label: 'PDF', contentType: 'application/pdf', extension: 'pdf' }
    const pending = api.downloadRoster(format)
    const rejected = expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    await vi.waitFor(() => expect(blobRead).toHaveBeenCalledOnce())
    generation = 2
    releaseBlob(new Blob(['synthetic personal roster']))
    await rejected
    expect(fetcher).toHaveBeenCalledOnce()
    expect(fetcher.mock.calls[0]![0]).toBe('/api/attendance/reports/headman/group-composition/export?format=pdf')
  })
})
