import axios from 'axios'
import { attachProblemDetails, parseProblemDetails } from '@/api/problemDetails'
import { APP_UPDATE_REQUIRED_EVENT, APP_VERSION } from './appVersion'

let isRefreshing = false
let pendingQueue: Array<{ resolve: (token: string) => void; reject: (err: unknown) => void }> = []

const flushQueue = (token: string | null, error: unknown = null) => {
  pendingQueue.forEach(({ resolve, reject }) => {
    if (token) resolve(token)
    else reject(error)
  })
  pendingQueue = []
}

/**
 * Базовый axios-клиент PWA. M03b Группа 6 — `withCredentials: true` ставится
 * per-call на `/auth/*` endpoints (см. api.ts), чтобы HttpOnly cookie
 * `rct_refresh` уходила. На остальных запросах access token в
 * `Authorization: Bearer` хватает — cookie туда не нужна.
 */
export const apiClient = axios.create({
  baseURL: '/api',
})

// Token getter — set by AuthProvider
let getAccessToken: () => string | null = () => null
export const setAccessTokenGetter = (fn: () => string | null) => {
  getAccessToken = fn
}

// Token setter — called by interceptor after refresh; accessToken only.
// Refresh-token теперь живёт в HttpOnly cookie, JS его не видит.
let onTokenRefreshed: ((accessToken: string) => void) | null = null
export const setTokenRefreshCallback = (fn: (accessToken: string) => void) => {
  onTokenRefreshed = fn
}

// Logout callback — called when refresh fails
let onAuthLogout: (() => void) | null = null
export const setAuthLogoutCallback = (fn: () => void) => {
  onAuthLogout = fn
}

// Request interceptor: attach Bearer token
apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  config.headers['X-PWA-Version'] = APP_VERSION
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// Response interceptor: silent refresh on 401 (cookie-based)
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
    if (error.response?.status === 426) {
      const detail = parseProblemDetails(error)
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent(APP_UPDATE_REQUIRED_EVENT, {
          detail: {
            message: detail?.detail ?? detail?.title,
          },
        }))
      }
      return Promise.reject(error)
    }

    if (error.response?.status === 401 && !original._retry) {
      original._retry = true

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingQueue.push({
            resolve: (token) => {
              original.headers['Authorization'] = `Bearer ${token}`
              resolve(apiClient(original))
            },
            reject,
          })
        })
      }

      isRefreshing = true
      try {
        // Cookie-based refresh — body empty, withCredentials передаст cookie.
        const { data } = await axios.post(
          '/api/auth/refresh',
          null,
          { withCredentials: true }
        )
        const newAccess = data.accessToken
        onTokenRefreshed?.(newAccess)
        flushQueue(newAccess)
        original.headers['Authorization'] = `Bearer ${newAccess}`
        return apiClient(original)
      } catch (refreshError) {
        flushQueue(null, refreshError)
        onAuthLogout?.()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }
    // M07 G4 (QC3): парсим RFC 9457 ProblemDetails + нормализуем
    // fieldErrors → invalidParams единожды на interceptor уровне, чтобы
    // call-sites читали через getProblemDetails() без повторного парсинга.
    attachProblemDetails(error, parseProblemDetails(error))
    return Promise.reject(error)
  }
)
