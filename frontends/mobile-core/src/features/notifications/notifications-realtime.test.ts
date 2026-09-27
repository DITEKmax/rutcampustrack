import { describe, expect, it, vi } from 'vitest'
import { Client, type IMessage, type StompConfig, type StompSubscription } from '@stomp/stompjs'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import { createGenerationBoundNotificationsRealtime } from './notifications-realtime'

describe('generation-bound notifications realtime lifecycle', () => {
  it('refreshes on reconnect after missed frames and drops events after the owner changes', async () => {
    vi.useFakeTimers()
    try {
      let generation = 4
      const owner = {
        currentGeneration: () => generation,
        accessTokenFor: (captured: number) => {
          if (captured !== generation) throw new StaleSessionGenerationError()
          return 'session-token'
        },
        refreshFor: vi.fn(async () => undefined),
      }
      const fetcher = vi.fn()
        .mockRejectedValueOnce(new Error('offline'))
        .mockResolvedValueOnce(new Response(JSON.stringify({ ticket: '123e4567-e89b-42d3-a456-426614174000' })))
        .mockResolvedValueOnce(new Response(JSON.stringify({ ticket: '123e4567-e89b-42d3-a456-426614174001' })))
      const sockets: string[] = []
      const eventHandlers: Array<() => void> = []
      const subscribedDestinations: string[] = []
      const fakeClient: Pick<Client, 'activate' | 'deactivate' | 'subscribe'> = {
        activate: vi.fn(),
        deactivate: vi.fn(async () => undefined),
        subscribe(destination: string, callback: (message: IMessage) => void): StompSubscription {
          subscribedDestinations.push(destination)
          eventHandlers.push(() => callback({} as IMessage))
          return { id: String(subscribedDestinations.length), unsubscribe: vi.fn() }
        },
      }
      const configurationCapture: { value?: StompConfig } = {}
      const onChanged = vi.fn()
      const lifecycle = createGenerationBoundNotificationsRealtime({
        owner,
        scope: { userId: '9007199254740993', groupId: '27', headman: true },
        fetcher,
        onChanged,
        createClient: (value) => {
          configurationCapture.value = value
          return fakeClient
        },
        createSocket: (url) => {
          sockets.push(url)
          return {} as WebSocket
        },
      })
      const clientConfiguration = configurationCapture.value
      if (!clientConfiguration) throw new Error('STOMP configuration was not captured')

      expect(clientConfiguration.reconnectDelay).toBe(1_500)
      expect(clientConfiguration.maxReconnectDelay).toBe(30_000)
      await clientConfiguration.beforeConnect?.(fakeClient as Client)
      await vi.advanceTimersByTimeAsync(1_500)
      expect(fakeClient.activate).toHaveBeenCalledTimes(2)

      await clientConfiguration.beforeConnect?.(fakeClient as Client)
      clientConfiguration.webSocketFactory?.()

      expect(fetcher).toHaveBeenCalledTimes(2)
      expect(sockets).toEqual([
        '/api/ws?ticket=123e4567-e89b-42d3-a456-426614174000',
      ])
      const requestInit = fetcher.mock.calls[0]?.[1] as RequestInit
      expect((requestInit.headers as Headers).get('Authorization')).toBe('Bearer session-token')
      expect(sockets[0]).not.toContain('session-token')

      clientConfiguration.onConnect?.({} as never)
      await vi.advanceTimersByTimeAsync(0)
      expect(onChanged).toHaveBeenCalledTimes(1)
      expect(subscribedDestinations).toEqual([
        '/topic/user/9007199254740993',
        '/topic/group/27',
        '/topic/group/27/headman',
      ])
      eventHandlers[0]?.()
      eventHandlers[1]?.()
      await vi.advanceTimersByTimeAsync(750)
      expect(onChanged).toHaveBeenCalledTimes(2)

      clientConfiguration.onWebSocketClose?.({} as never)
      await clientConfiguration.beforeConnect?.(fakeClient as Client)
      clientConfiguration.webSocketFactory?.()
      clientConfiguration.onConnect?.({} as never)
      expect(fetcher).toHaveBeenCalledTimes(3)
      expect(sockets).toEqual([
        '/api/ws?ticket=123e4567-e89b-42d3-a456-426614174000',
        '/api/ws?ticket=123e4567-e89b-42d3-a456-426614174001',
      ])
      expect(subscribedDestinations.slice(3)).toEqual([
        '/topic/user/9007199254740993',
        '/topic/group/27',
        '/topic/group/27/headman',
      ])
      // No second-connection event was received; the onConnect refresh closes
      // the disconnect window for frames that were not replayed by the broker.
      await vi.advanceTimersByTimeAsync(750)
      expect(onChanged).toHaveBeenCalledTimes(3)

      generation += 1
      eventHandlers[0]?.()
      eventHandlers[5]?.()
      await vi.advanceTimersByTimeAsync(1_000)
      expect(onChanged).toHaveBeenCalledTimes(3)

      lifecycle.dispose()
      eventHandlers[5]?.()
      expect(fakeClient.deactivate).toHaveBeenCalledWith({ force: true })
      await vi.advanceTimersByTimeAsync(1_000)
      expect(onChanged).toHaveBeenCalledTimes(3)
    } finally {
      vi.useRealTimers()
    }
  })
})
