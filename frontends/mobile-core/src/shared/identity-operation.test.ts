import { describe, expect, it, vi } from 'vitest'
import { IdentityOperationError, IdentityOperations } from './identity-operation'

const id = '11111111-1111-4111-8111-111111111111'
const path = '/api/academic/users'
const init = { method: 'POST', body: JSON.stringify({ firstName: 'Иван' }) }
function receipt(status = 'PENDING', retryAfterSeconds = 0): Response {
  return new Response(JSON.stringify({ operationId: id, status, retryAfterSeconds }), {
    status: status === 'PENDING' ? 202 : 200,
    headers: {
      'X-Operation-Id': id,
      Location: `/academic/identity-operations/${id}`,
    },
  })
}
function failure(status: number): Error & { response: Response } {
  return Object.assign(new Error(`HTTP ${status}`), {
    response: new Response(null, { status }),
  })
}

describe('authenticated identity operation intent', () => {
  it('returns the final original body only after status success and same-key replay', async () => {
    const calls: { path: string; init?: RequestInit }[] = []
    const transport = vi.fn(async (requestPath: string, requestInit?: RequestInit) => {
      calls.push({
        path: requestPath,
        ...(requestInit ? { init: requestInit } : {}),
      })
      if (calls.length === 1) return receipt()
      if (calls.length === 2) return receipt('SUCCEEDED')
      return new Response(JSON.stringify({ id: 42, initialPassword: 'fresh-authorized-proof' }), {
        status: 201,
      })
    })
    const operation = new IdentityOperations(transport, () => {})
    const response = await operation.run(path, init, id)
    expect(await response.json()).toEqual({
      id: 42,
      initialPassword: 'fresh-authorized-proof',
    })
    expect(calls.map((call) => call.path)).toEqual([
      path,
      `/api/academic/identity-operations/${id}`,
      path,
    ])
    expect(new Headers(calls[0]?.init?.headers).get('Idempotency-Key')).toBe(id)
    expect(new Headers(calls[2]?.init?.headers).get('Idempotency-Key')).toBe(id)
    expect(operation.pending).toBe(false)
  })

  it('bounds pending work and resumes the accepted archive without retaining its password', async () => {
    const bodies: unknown[] = []
    let succeeded = false
    const operation = new IdentityOperations(
      async (requestPath, requestInit) => {
        if (requestPath.includes('identity-operations'))
          return receipt(succeeded ? 'SUCCEEDED' : 'PENDING')
        bodies.push(JSON.parse(String(requestInit?.body)))
        return bodies.length === 1 ? receipt() : new Response(null, { status: 204 })
      },
      () => {},
    )
    const archive = {
      method: 'POST',
      body: JSON.stringify({
        operationId: id,
        previewDigest: 'digest',
        password: 'proof',
      }),
    }
    await expect(operation.run(`${path}/42/archive`, archive, id)).rejects.toMatchObject({
      state: 'PENDING',
    })
    expect(operation.pending).toBe(true)
    succeeded = true
    expect((await operation.resume()).response.status).toBe(204)
    expect(bodies).toEqual([
      { operationId: id, previewDigest: 'digest', password: 'proof' },
      { operationId: id, previewDigest: 'digest' },
    ])
  })

  it('preserves the UUID and original proof after a lost response and unknown status', async () => {
    const keys: (string | null)[] = []
    const bodies: unknown[] = []
    let requests = 0
    const operation = new IdentityOperations(
      async (requestPath, requestInit) => {
        if (requestPath.includes('identity-operations')) throw failure(404)
        keys.push(new Headers(requestInit?.headers).get('Idempotency-Key'))
        bodies.push(JSON.parse(String(requestInit?.body)))
        if (++requests === 1) throw new TypeError('network unavailable')
        return new Response(null, { status: 204 })
      },
      () => {},
    )
    const archive = {
      method: 'POST',
      body: JSON.stringify({ operationId: id, password: 'proof' }),
    }
    await expect(operation.run(`${path}/42/archive`, archive, id)).rejects.toMatchObject({
      state: 'UNKNOWN',
    })
    await operation.resume()
    expect(keys).toEqual([id, id])
    expect(bodies).toEqual([
      { operationId: id, password: 'proof' },
      { operationId: id, password: 'proof' },
    ])
  })

  it('blocks changed input while unknown and never silently retries with a new key', async () => {
    const transport = vi.fn(async () => {
      throw new TypeError('network unavailable')
    })
    const operation = new IdentityOperations(transport, () => {})
    await expect(operation.run(path, init, id)).rejects.toBeInstanceOf(IdentityOperationError)
    await expect(
      operation.run(path, {
        ...init,
        body: JSON.stringify({ firstName: 'Пётр' }),
      }),
    ).rejects.toMatchObject({ state: 'UNKNOWN' })
    expect(transport).toHaveBeenCalledTimes(1)
    expect(operation.pending).toBe(true)
  })

  it('respects Retry-After without holding the screen busy for a long interval', async () => {
    vi.useFakeTimers()
    try {
      const transport = vi.fn(async () => receipt('PENDING', 30))
      const operation = new IdentityOperations(transport, () => {})
      await expect(operation.run(path, init, id)).rejects.toMatchObject({
        state: 'PENDING',
      })
      await expect(operation.resume()).rejects.toMatchObject({
        state: 'PENDING',
      })
      expect(transport).toHaveBeenCalledTimes(1)
      await vi.advanceTimersByTimeAsync(30_000)
      await expect(operation.resume()).rejects.toMatchObject({
        state: 'PENDING',
      })
      expect(transport).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it.each(['REJECTED', 'FAILED_SAFE'])(
    'requires a fresh authorized action after %s',
    async (status) => {
      let calls = 0
      const transport = vi.fn(async () => (++calls === 1 ? receipt() : receipt(status)))
      const operation = new IdentityOperations(transport, () => {})
      await expect(operation.run(path, init, id)).rejects.toMatchObject({
        state: status,
        operationId: id,
      })
      expect(operation.pending).toBe(false)
      expect(transport).toHaveBeenCalledTimes(2)
    },
  )

  it('keeps a detached final success resumable without caching its response or credentials', async () => {
    const keys: (string | null)[] = []
    const operation = new IdentityOperations(
      async (_path, requestInit) => {
        keys.push(new Headers(requestInit?.headers).get('Idempotency-Key'))
        return new Response(
          JSON.stringify({
            id: 42,
            initialPassword: keys.length === 1 ? 'first' : 'fresh',
          }),
          { status: 201 },
        )
      },
      () => {},
    )
    const observer = new AbortController()
    const first = await operation.run(path, { ...init, signal: observer.signal }, id)
    observer.abort()
    expect(await first.json()).toMatchObject({ initialPassword: 'first' })
    expect(operation.pending).toBe(true)
    const replay = await operation.resume()
    expect(await replay.response.json()).toMatchObject({
      id: 42,
      initialPassword: 'fresh',
    })
    expect(keys).toEqual([id, id])
    operation.acknowledge()
    expect(operation.pending).toBe(false)
  })

  it('lets a resumed observer share an in-flight result without consuming the first response', async () => {
    let respond!: (response: Response) => void
    const transport = vi.fn(
      () =>
        new Promise<Response>((resolve) => {
          respond = resolve
        }),
    )
    const operation = new IdentityOperations(transport, () => {})
    const first = operation.run(path, { ...init, signal: new AbortController().signal }, id)
    const second = operation.resume()
    respond(new Response(JSON.stringify({ id: 42 }), { status: 201 }))
    const [firstResponse, secondResult] = await Promise.all([first, second])
    expect(await firstResponse.json()).toEqual({ id: 42 })
    expect(await secondResult.response.json()).toEqual({ id: 42 })
    expect(transport).toHaveBeenCalledTimes(1)
    operation.acknowledge()
    expect(operation.pending).toBe(false)
  })

  it('keeps a timed-out first request unknown until the same operation can be checked', async () => {
    vi.useFakeTimers()
    try {
      const operation = new IdentityOperations(
        async (_path, requestInit) =>
          new Promise<Response>((_resolve, reject) => {
            requestInit?.signal?.addEventListener('abort', () =>
              reject(new DOMException('timeout', 'AbortError')),
            )
          }),
        () => {},
      )
      const pending = operation.run(path, init, id)
      const assertion = expect(pending).rejects.toMatchObject({ state: 'UNKNOWN' })
      await vi.advanceTimersByTimeAsync(10_000)
      await assertion
      expect(operation.pending).toBe(true)
    } finally {
      vi.useRealTimers()
    }
  })

  it('retains an ambiguous conflict without automatically creating another intent', async () => {
    const conflict = failure(409)
    const transport = vi.fn(async () => {
      throw conflict
    })
    const operation = new IdentityOperations(transport, () => {})
    await expect(operation.run(path, init, id)).rejects.toMatchObject({ state: 'UNKNOWN' })
    expect(transport).toHaveBeenCalledTimes(1)
    expect(operation.pending).toBe(true)
    await expect(
      operation.run(path, { ...init, body: JSON.stringify({ firstName: 'Пётр' }) }),
    ).rejects.toMatchObject({ state: 'UNKNOWN' })
    expect(transport).toHaveBeenCalledTimes(1)
  })

  it('drops transient state and rejects a late response after the actor generation changes', async () => {
    let current = true
    let respond!: (response: Response) => void
    const transport = vi.fn(
      () =>
        new Promise<Response>((resolve) => {
          respond = resolve
        }),
    )
    const operation = new IdentityOperations(transport, () => {
      if (!current) throw new Error('stale actor')
    })
    const pending = operation.run(path, init, id)
    current = false
    respond(receipt())
    await expect(pending).rejects.toThrow('stale actor')
    current = true
    expect(operation.pending).toBe(false)
    expect(transport).toHaveBeenCalledTimes(1)
  })

  it('retains an accepted UUID through GET403 and blocks a changed intent until durable rejection', async () => {
    const calls: string[] = []
    let authorized = false
    const operation = new IdentityOperations(
      async (requestPath) => {
        calls.push(requestPath)
        if (calls.length === 1) return receipt()
        if (!authorized) throw failure(403)
        return receipt('REJECTED')
      },
      () => {},
    )
    await expect(operation.run(path, init, id)).rejects.toMatchObject({ state: 'PENDING' })
    expect(operation.pending).toBe(true)
    await expect(
      operation.run(path, { ...init, body: JSON.stringify({ firstName: 'Пётр' }) }),
    ).rejects.toMatchObject({ state: 'PENDING' })
    expect(calls).toEqual([path, `/api/academic/identity-operations/${id}`])
    authorized = true
    await expect(operation.resume()).rejects.toMatchObject({ state: 'REJECTED' })
    expect(calls[2]).toBe(`/api/academic/identity-operations/${id}`)
    expect(operation.pending).toBe(false)
  })

  it('preserves an unconfirmed key after an initial HTTP403 until status can be observed', async () => {
    let observed = false
    const calls: string[] = []
    const operation = new IdentityOperations(
      async (requestPath) => {
        calls.push(requestPath)
        if (!observed) throw failure(403)
        return receipt('FAILED_SAFE')
      },
      () => {},
    )
    await expect(operation.run(path, init, id)).rejects.toMatchObject({ state: 'UNKNOWN' })
    expect(operation.pending).toBe(true)
    observed = true
    await expect(operation.resume()).rejects.toMatchObject({ state: 'FAILED_SAFE' })
    expect(calls).toEqual([path, `/api/academic/identity-operations/${id}`])
    expect(operation.pending).toBe(false)
  })

  it.each(['accepted-receipt', 'status', 'final-result'])(
    'bounds a stalled %s body and releases the observer for same-key resume',
    async (stage) => {
      vi.useFakeTimers()
      let bodyController!: ReadableStreamDefaultController<Uint8Array>
      try {
        let stalled = false
        const calls: { path: string; key: string | null }[] = []
        const operation = new IdentityOperations(
          async (requestPath, requestInit) => {
            calls.push({
              path: requestPath,
              key: new Headers(requestInit?.headers).get('Idempotency-Key'),
            })
            const isStatus = requestPath.includes('identity-operations')
            const shouldStall =
              !stalled &&
              ((stage === 'accepted-receipt' && calls.length === 1) ||
                (stage === 'status' && isStatus) ||
                (stage === 'final-result' && !isStatus && calls.length > 1))
            if (shouldStall) {
              stalled = true
              return new Response(
                new ReadableStream<Uint8Array>({
                  start(controller) {
                    bodyController = controller
                  },
                }),
                {
                  status: stage === 'accepted-receipt' ? 202 : 200,
                  headers: { 'X-Operation-Id': id },
                },
              )
            }
            if (calls.length === 1) return receipt()
            if (isStatus) return receipt('SUCCEEDED')
            return new Response(JSON.stringify({ id: 42 }), { status: 201 })
          },
          () => {},
        )
        const first = operation.run(path, init, id)
        const assertion = expect(first).rejects.toMatchObject({ state: 'PENDING' })
        await vi.advanceTimersByTimeAsync(10_000)
        await assertion
        expect(operation.pending).toBe(true)
        bodyController.error(new DOMException('synthetic stream closed', 'AbortError'))
        const result = await operation.resume()
        expect(await result.response.json()).toEqual({ id: 42 })
        expect(calls.filter((call) => call.path === path).every((call) => call.key === id)).toBe(
          true,
        )
        expect(operation.pending).toBe(false)
      } finally {
        vi.useRealTimers()
      }
    },
  )

  it('retains an accepted operation when status checking loses connectivity', async () => {
    let calls = 0
    const operation = new IdentityOperations(
      async () => {
        if (++calls === 1) return receipt()
        throw new TypeError('connection lost')
      },
      () => {},
    )
    await expect(operation.run(path, init, id)).rejects.toMatchObject({
      state: 'PENDING',
    })
    expect(operation.pending).toBe(true)
  })

  it('rejects an untrusted Location without sending credentials to it', async () => {
    const response = receipt()
    response.headers.set('Location', `https://foreign.example/academic/identity-operations/${id}`)
    const transport = vi.fn(async () => response)
    const operation = new IdentityOperations(transport, () => {})
    await expect(operation.run(path, init, id)).rejects.toMatchObject({
      state: 'UNKNOWN',
    })
    expect(transport).toHaveBeenCalledTimes(1)
  })
})
