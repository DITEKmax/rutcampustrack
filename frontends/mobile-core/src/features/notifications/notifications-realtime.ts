import {
  Client,
  ReconnectionTimeMode,
  type StompConfig,
  type StompSubscription,
} from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { NotificationsSessionOwner } from './notifications-client'

const INITIAL_RECONNECT_DELAY_MS = 1_500
const MAX_RECONNECT_DELAY_MS = 30_000
const MIN_INVALIDATION_INTERVAL_MS = 750
const WS_TICKET_PATH = '/api/auth/ws-ticket'
const WS_ENDPOINT_PATH = '/api/ws'
const MAX_SIGNED_LONG = 9_223_372_036_854_775_807n

export interface NotificationsRealtimeScope {
  /** Exact positive decimal ID from the authenticated profile/session projection. */
  userId: string
  /** Exact active group ID from the authenticated role context, if one exists. */
  groupId: string | null
  /** True only when the active server-backed role is HEADMAN. */
  headman: boolean
}

export interface NotificationsRealtimeLifecycle {
  dispose(): void
}

type StompClientHandle = Pick<Client, 'activate' | 'deactivate' | 'subscribe'>

interface NotificationsRealtimeOptions {
  owner: NotificationsSessionOwner
  scope: NotificationsRealtimeScope
  onChanged(): void
  fetcher?: typeof fetch
  /** Injection seam for the bounded lifecycle test; production uses STOMP/SockJS. */
  createClient?(configuration: StompConfig): StompClientHandle
  createSocket?(url: string): WebSocket
}

/**
 * Opens private notification topics for one authenticated generation. Frames
 * carry no client-authoritative history; they only invalidate REST projections.
 */
export function createGenerationBoundNotificationsRealtime(
  options: NotificationsRealtimeOptions,
): NotificationsRealtimeLifecycle {
  const generation = options.owner.currentGeneration()
  const userId = positiveDecimalId(options.scope.userId)
  const groupId = options.scope.groupId === null ? null : positiveDecimalId(options.scope.groupId)
  const fetcher = options.fetcher ?? globalThis.fetch.bind(globalThis)

  let active = userId !== null
  let ticketUrl: string | null = null
  let ticketRetryTimer: ReturnType<typeof setTimeout> | undefined
  let ticketRetryDelayMs = INITIAL_RECONNECT_DELAY_MS
  let refreshTimer: ReturnType<typeof setTimeout> | undefined
  let lastInvalidationAt = 0
  let client: StompClientHandle | null = null
  let subscriptions: StompSubscription[] = []

  const isCurrent = (): boolean => active && options.owner.currentGeneration() === generation

  const destinations = [
    `/topic/user/${userId ?? ''}`,
    ...(groupId ? [`/topic/group/${groupId}`] : []),
    ...(groupId && options.scope.headman ? [`/topic/group/${groupId}/headman`] : []),
  ]

  function assertCurrent(): void {
    if (!isCurrent()) throw new StaleSessionGenerationError()
  }

  async function requestTicket(retried = false): Promise<string> {
    assertCurrent()
    const token = options.owner.accessTokenFor(generation)
    const headers = new Headers({ Accept: 'application/json' })
    if (token) headers.set('Authorization', `Bearer ${token}`)

    let response: Response
    try {
      response = await fetcher(WS_TICKET_PATH, {
        method: 'POST',
        headers,
        credentials: 'same-origin',
      })
    } catch (error) {
      assertCurrent()
      throw error
    }
    assertCurrent()

    if (response.status === 401 && !retried) {
      await options.owner.refreshFor(generation)
      assertCurrent()
      return requestTicket(true)
    }
    if (!response.ok) throw new Error('Не удалось открыть канал уведомлений')

    let value: unknown
    try {
      value = await response.json()
    } catch {
      assertCurrent()
      throw new Error('Сервер не выдал ticket канала уведомлений')
    }
    assertCurrent()
    const record = asRecord(value)
    const ticket = record?.ticket
    if (typeof ticket !== 'string' || !isUuid(ticket)) {
      throw new Error('Сервер не выдал ticket канала уведомлений')
    }
    return `${WS_ENDPOINT_PATH}?ticket=${encodeURIComponent(ticket)}`
  }

  function scheduleInvalidation(): void {
    if (!isCurrent() || refreshTimer !== undefined) return
    const delay = Math.max(0, MIN_INVALIDATION_INTERVAL_MS - (Date.now() - lastInvalidationAt))
    refreshTimer = setTimeout(() => {
      refreshTimer = undefined
      if (!isCurrent()) return
      lastInvalidationAt = Date.now()
      try {
        options.onChanged()
      } catch {
        // A screen listener cannot change the authenticated socket lifecycle.
      }
    }, delay)
  }

  function scheduleTicketRetry(): void {
    const currentClient = client
    if (!isCurrent() || !currentClient || ticketRetryTimer !== undefined) return
    const delay = ticketRetryDelayMs
    ticketRetryDelayMs = Math.min(ticketRetryDelayMs * 2, MAX_RECONNECT_DELAY_MS)
    void currentClient.deactivate({ force: true }).then(() => {
      if (!isCurrent() || client !== currentClient) return
      ticketRetryTimer = setTimeout(() => {
        ticketRetryTimer = undefined
        if (isCurrent() && client === currentClient) currentClient.activate()
      }, delay)
    }).catch(() => undefined)
  }

  const configuration: StompConfig = {
    reconnectDelay: INITIAL_RECONNECT_DELAY_MS,
    maxReconnectDelay: MAX_RECONNECT_DELAY_MS,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    debug: () => undefined,
    beforeConnect: async () => {
      if (!isCurrent()) return
      ticketUrl = null
      try {
        ticketUrl = await requestTicket()
        ticketRetryDelayMs = INITIAL_RECONNECT_DELAY_MS
      } catch {
        if (isCurrent()) scheduleTicketRetry()
      }
    },
    webSocketFactory: () => {
      if (!isCurrent() || ticketUrl === null) throw new StaleSessionGenerationError()
      const currentTicketUrl = ticketUrl
      ticketUrl = null
      return options.createSocket
        ? options.createSocket(currentTicketUrl)
        : new SockJS(currentTicketUrl)
    },
    onConnect: () => {
      if (!isCurrent()) return
      subscriptions = destinations.map((destination) => client!.subscribe(
        destination,
        () => scheduleInvalidation(),
      ))
      scheduleInvalidation()
    },
    onWebSocketClose: () => {
      subscriptions = []
    },
  }

  client = options.createClient
    ? options.createClient(configuration)
    : new Client(configuration)
  if (active) client.activate()

  return {
    dispose(): void {
      if (!active) return
      active = false
      ticketUrl = null
      if (ticketRetryTimer !== undefined) clearTimeout(ticketRetryTimer)
      ticketRetryTimer = undefined
      if (refreshTimer !== undefined) clearTimeout(refreshTimer)
      refreshTimer = undefined
      const currentClient = client
      client = null
      const currentSubscriptions = subscriptions
      subscriptions = []
      for (const subscription of currentSubscriptions) {
        try {
          subscription.unsubscribe()
        } catch {
          // Closing the transport is still required if UNSUBSCRIBE cannot be sent.
        }
      }
      if (currentClient) void currentClient.deactivate({ force: true }).catch(() => undefined)
    },
  }
}

function positiveDecimalId(value: string): string | null {
  if (!/^[1-9]\d*$/.test(value)) return null
  try {
    const parsed = BigInt(value)
    return parsed > 0n && parsed <= MAX_SIGNED_LONG ? value : null
  } catch {
    return null
  }
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}
