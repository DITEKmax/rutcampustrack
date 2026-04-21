import { apiClient } from '@/shared/lib/axios'
import type {
  LoginRequest,
  TokenResponse,
  WsTicketResponse,
} from '@/api/schema'

export type { LoginRequest, TokenResponse }
/** Kept legacy alias — `WsTicket` is PWA-facing name for `WsTicketResponse`. */
export type WsTicket = WsTicketResponse

/**
 * Frontend-derived claims из JWT access token — не backend DTO,
 * не присутствует в OpenAPI. Остаётся ручным типом (M07 G3b, D3).
 */
export interface AuthUser {
  id: number
  role: string
  groupId?: number
  isHeadman: boolean  // derived from JWT is_headman claim
}

/**
 * POST /auth/login — credentials auto-send cookie afterwards via axios
 * `withCredentials: true`. Возвращает access token (in-memory) +
 * устанавливает HttpOnly cookie `rct_refresh`.
 */
export async function loginApi(credentials: LoginRequest): Promise<TokenResponse> {
  const { data } = await apiClient.post<TokenResponse>('/auth/login', credentials, {
    withCredentials: true,
  })
  return data
}

/**
 * POST /auth/logout — revoke refresh (cookie-based), clear cookie.
 * Запрос должен быть c credentials, чтобы cookie долетела.
 */
export async function logoutApi(): Promise<void> {
  await apiClient.post('/auth/logout', null, { withCredentials: true })
}

/**
 * POST /auth/refresh — cookie-based (без body). Возвращает новый access +
 * rotates cookie (HttpOnly, автоматически сохраняется браузером).
 */
export async function refreshApi(): Promise<TokenResponse> {
  const { data } = await apiClient.post<TokenResponse>('/auth/refresh', null, {
    withCredentials: true,
  })
  return data
}

/**
 * POST /auth/ws-ticket — issue short-lived (30s, single-use) ticket для
 * WebSocket handshake. Защищён access-JWT (Bearer). Клиент использует как
 * `new WebSocket('/api/ws?ticket=<uuid>')`.
 */
export async function acquireWsTicketApi(): Promise<WsTicket> {
  const { data } = await apiClient.post<WsTicket>('/auth/ws-ticket', null)
  return data
}
