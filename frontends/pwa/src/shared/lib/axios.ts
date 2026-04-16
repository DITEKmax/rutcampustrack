import axios from 'axios'

let isRefreshing = false
let pendingQueue: Array<{ resolve: (token: string) => void; reject: (err: unknown) => void }> = []

const flushQueue = (token: string | null, error: unknown = null) => {
  pendingQueue.forEach(({ resolve, reject }) => {
    if (token) resolve(token)
    else reject(error)
  })
  pendingQueue = []
}

export const apiClient = axios.create({
  baseURL: '/api',
})

// Token getters — set by AuthProvider
let getAccessToken: () => string | null = () => null
export const setAccessTokenGetter = (fn: () => string | null) => {
  getAccessToken = fn
}

let getRefreshToken: () => string | null = () => null
export const setRefreshTokenGetter = (fn: () => string | null) => {
  getRefreshToken = fn
}

// Token setter — called by interceptor after refresh; delivers both tokens
// so AuthProvider can persist the rotated refresh_token.
let onTokenRefreshed: ((accessToken: string, refreshToken: string) => void) | null = null
export const setTokenRefreshCallback = (
  fn: (accessToken: string, refreshToken: string) => void
) => {
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
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// Response interceptor: silent refresh on 401
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
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
        const refreshToken = getRefreshToken()
        if (!refreshToken) {
          throw new Error('No refresh token available')
        }
        const { data } = await axios.post('/api/auth/refresh', { refreshToken })
        const newAccess = data.accessToken
        const newRefresh = data.refreshToken
        onTokenRefreshed?.(newAccess, newRefresh)
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
    return Promise.reject(error)
  }
)
