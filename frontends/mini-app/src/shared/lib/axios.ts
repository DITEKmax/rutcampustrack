import axios from 'axios'

// Bare instance for auth calls (bypasses 401 interceptor — prevents infinite loop)
export const bareAxios = axios.create()

// Main API client — NO withCredentials (WebView drops cookies)
export const apiClient = axios.create({ baseURL: '/api' })

// Token getter — set by AuthProvider
let getAccessToken: () => string | null = () => null
export const setAccessTokenGetter = (fn: () => string | null) => {
  getAccessToken = fn
}

// Re-auth callback — set by AuthProvider (D-06: re-auth via initData, NOT cookie /auth/refresh)
let onReAuth: (() => Promise<void>) | null = null
export const setReAuthCallback = (fn: () => Promise<void>) => {
  onReAuth = fn
}

// Request interceptor: attach Bearer token
apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

// Response interceptor: re-auth on 401 (D-06)
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true
      if (onReAuth) {
        await onReAuth()
        original.headers['Authorization'] = `Bearer ${getAccessToken()}`
        return apiClient(original)
      }
    }
    return Promise.reject(error)
  }
)
