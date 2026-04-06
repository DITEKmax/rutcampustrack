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
  withCredentials: true,
})

// Token getter — set by AuthProvider
let getAccessToken: () => string | null = () => null
export const setAccessTokenGetter = (fn: () => string | null) => {
  getAccessToken = fn
}

// Token setter — called by interceptor after refresh
let onTokenRefreshed: ((token: string) => void) | null = null
export const setTokenRefreshCallback = (fn: (token: string) => void) => {
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
        // withCredentials: true sends the httpOnly refresh_token cookie automatically
        const { data } = await axios.post('/api/auth/refresh', {}, { withCredentials: true })
        const newToken = data.accessToken
        onTokenRefreshed?.(newToken)
        flushQueue(newToken)
        original.headers['Authorization'] = `Bearer ${newToken}`
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
