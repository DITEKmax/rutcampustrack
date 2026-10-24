import { describe, expect, it, vi } from 'vitest'
import { CredentialsClient } from './credentials-client'
import type { WebTransport } from '../../app/api'

const view = {
  userId: '12',
  credentialsVersion: '9007199254740999',
  state: 'GENERATED',
  origin: 'admin_reset',
  password: 'test-only-generated-value',
}
function setup(fetcher: typeof fetch, assertCurrent = () => {}) {
  const transport: WebTransport = {
    fetcher,
    assertCurrent,
    accessToken: () => null,
    onUnauthorized: async () => {},
  }
  return new CredentialsClient(transport)
}
describe('admin credentials authority and CAS', () => {
  it('keeps the decimal version exact and never repeats an ambiguous generation', async () => {
    const fetcher = vi.fn<typeof fetch>().mockRejectedValueOnce(new TypeError('network'))
    const client = setup(fetcher)
    await expect(client.generate(12, view.credentialsVersion)).rejects.toThrow('network')
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({
      expectedCredentialsVersion: view.credentialsVersion,
    })
  })
  it('rejects a decoded secret when its admitted session has changed', async () => {
    const response = new Response(JSON.stringify(view))
    const client = setup(
      async () => response,
      () => {
        throw new Error('retired authority')
      },
    )
    await expect(client.read(12)).rejects.toThrow('retired authority')
  })
  it('never accepts another target or a chosen password as disclosure', async () => {
    for (const value of [
      { ...view, userId: '13' },
      { ...view, state: 'USER_CHANGED' },
    ]) {
      const client = setup(async () => new Response(JSON.stringify(value)))
      await expect(client.read(12)).rejects.toThrow('подтвердить')
    }
  })
  it('keeps revoked-session HTTP401 identifiable even without a JSON error body', async () => {
    const client = setup(async () => new Response('', { status: 401 }))
    await expect(client.read(12)).rejects.toMatchObject({ status: 401 })
  })
})
