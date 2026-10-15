import { effectScope, nextTick, reactive } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import {
  HeadmanGroupApi,
  HeadmanGroupApiError,
  type HeadmanCredentialView,
} from './headman-group-client'
import { useHeadmanCredentials } from './use-headman-credentials'

const generated = (
  id = 2,
  version = '1',
): Extract<HeadmanCredentialView, { state: 'GENERATED' }> => ({
  studentId: String(id),
  credentialsVersion: version,
  state: 'GENERATED',
  origin: 'initial',
  password: 'synthetic-generated',
})
const settle = async () => {
  for (let i = 0; i < 12; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
function fixture() {
  const api = new HeadmanGroupApi({ accessToken: () => 'synthetic' })
  const context = reactive({ api, groupId: 8, actorUserId: '1', enabled: true, memberIds: [2] })
  const read = vi.spyOn(api, 'readCredential').mockResolvedValue(generated())
  const scope = effectScope()
  const state = scope.run(() => useHeadmanCredentials(() => ({ ...context, api })))!
  return { api, context, read, scope, state }
}

describe('own-group credential RAM and authority boundary', () => {
  it('discards an old member read after the current member changes and clears on offline/unmount', async () => {
    const f = fixture()
    try {
      await settle()
      let release!: (value: HeadmanCredentialView) => void
      f.read.mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            release = resolve
          }),
      )
      const pending = f.state.refresh()
      expect(f.state.values.value).toEqual({})
      f.context.memberIds = [3]
      await settle()
      release(generated(2))
      await pending
      expect(f.state.values.value[2]).toBeUndefined()
      f.context.enabled = false
      expect(f.state.values.value).toEqual({})
      expect(f.state.link.value).toBeNull()
    } finally {
      f.scope.stop()
    }
    expect(f.state.values.value).toEqual({})
  })

  it('requires a fresh read after stale or uncertain generate; never rotates blindly', async () => {
    const f = fixture()
    try {
      await settle()
      const generate = vi
        .spyOn(f.api, 'generatePassword')
        .mockRejectedValue(new HeadmanGroupApiError(new Response(null, { status: 409 }), null))
      await f.state.perform(2, 'generate', '1')
      expect(f.state.uncertain.value).toBe(true)
      expect(f.state.values.value).toEqual({})
      await f.state.perform(2, 'generate', '1')
      expect(generate).toHaveBeenCalledTimes(1)
      f.read.mockResolvedValue({ ...generated(2, '2'), origin: 'headman_reset' })
      await f.state.refresh()
      generate.mockResolvedValue({ ...generated(2, '3'), origin: 'headman_reset' })
      await f.state.perform(2, 'generate', '1')
      expect(generate).toHaveBeenCalledTimes(1)
      await f.state.perform(2, 'generate', '2')
      expect(generate).toHaveBeenLastCalledWith(2, '2')
      expect(f.state.values.value[2]?.credentialsVersion).toBe('3')
    } finally {
      f.scope.stop()
    }
  })

  it('clears all displayed credentials on current denial and ignores a late link from the prior owner', async () => {
    const f = fixture()
    try {
      await settle()
      let release!: (value: { url: string; expiresAt: string; expiresInSeconds: number }) => void
      vi.spyOn(f.api, 'issueRecoveryLink').mockImplementation(
        () =>
          new Promise((resolve) => {
            release = resolve
          }),
      )
      const pending = f.state.perform(2, 'link')
      f.context.actorUserId = '9'
      await settle()
      release({
        url: 'https://client.example.test/password-reset#resetTicket=synthetic',
        expiresAt: '2030-01-01T00:00:00Z',
        expiresInSeconds: 3600,
      })
      await pending
      expect(f.state.link.value).toBeNull()
      f.read.mockRejectedValue(new HeadmanGroupApiError(new Response(null, { status: 403 }), null))
      await f.state.refresh()
      expect(f.state.values.value).toEqual({})
      expect(f.state.error.value).not.toBeNull()
    } finally {
      f.scope.stop()
    }
  })

  it('does not redisplay an old generated value when the student changed it before link issuance', async () => {
    const f = fixture()
    try {
      await settle()
      vi.spyOn(f.api, 'issueRecoveryLink').mockResolvedValue({
        url: 'https://client.example.test/password-reset#resetTicket=synthetic',
        expiresAt: '2030-01-01T00:00:00Z',
        expiresInSeconds: 3600,
      })
      f.read.mockResolvedValue({
        studentId: '2',
        credentialsVersion: '2',
        state: 'STUDENT_CHANGED',
        origin: null,
        password: null,
      })
      await f.state.perform(2, 'link')
      expect(f.state.values.value[2]?.state).toBe('STUDENT_CHANGED')
      expect(f.state.values.value[2]?.password).toBeNull()
      expect(f.state.link.value).not.toBeNull()
    } finally {
      f.scope.stop()
    }
  })

  it('decodes only current exact ID/version/state and never admits chosen plaintext in a changed view', async () => {
    let body: unknown = generated()
    const requests: RequestInit[] = []
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (typeof input !== 'string') throw new Error('unexpected request')
      requests.push(init ?? {})
      return new Response(JSON.stringify(body), { status: 200 })
    })
    const api = new HeadmanGroupApi({ accessToken: () => 'synthetic', fetcher })
    expect((await api.readCredential(2)).state).toBe('GENERATED')
    for (const invalid of [
      { ...generated(), studentId: '3' },
      { ...generated(), credentialsVersion: '0' },
      { ...generated(), credentialsVersion: '9223372036854775808' },
      { ...generated(), state: 'STUDENT_CHANGED' },
      { ...generated(), origin: 'unknown' },
    ]) {
      body = invalid
      await expect(api.readCredential(2)).rejects.toThrow('некорректные данные доступа')
    }
    body = {
      studentId: '2',
      credentialsVersion: '2',
      state: 'STUDENT_CHANGED',
      password: null,
      origin: null,
    }
    expect((await api.readCredential(2)).password).toBeNull()
    expect(requests.every((request) => request.cache === 'no-store')).toBe(true)
  })
})
