import { afterEach, expect, it, vi } from 'vitest'
import { APP_UPDATE_REQUIRED_EVENT, APP_VERSION, createPwaFetcher } from './pwa-version'
afterEach(() => vi.unstubAllGlobals())
it('keeps version headers without opening a blocking update flow for a failed optional report', async () => {
  const events = new EventTarget(),
    update = vi.fn()
  events.addEventListener(APP_UPDATE_REQUIRED_EVENT, update)
  vi.stubGlobal('window', events)
  const fetcher = vi.fn<typeof fetch>().mockResolvedValue(Response.json({}, { status: 426 }))
  const response = await createPwaFetcher(fetcher)('/api/v1/student/checkin/acquisition-reports', {
    method: 'POST',
  })
  await Promise.resolve()
  expect(response.status).toBe(426)
  expect(new Headers(fetcher.mock.calls[0]?.[1]?.headers).get('X-PWA-Version')).toBe(APP_VERSION)
  expect(update).not.toHaveBeenCalled()
})
it('keeps the existing mandatory update signal for normal business requests', async () => {
  const events = new EventTarget(),
    update = vi.fn()
  events.addEventListener(APP_UPDATE_REQUIRED_EVENT, update)
  vi.stubGlobal('window', events)
  await createPwaFetcher(async () => Response.json({}, { status: 426 }))('/api/v1/student/today')
  await vi.waitFor(() => expect(update).toHaveBeenCalledOnce())
})
