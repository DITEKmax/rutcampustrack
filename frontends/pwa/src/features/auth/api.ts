import { apiClient } from '@/shared/lib/axios'

export interface LoginRequest {
  login: string
  password: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface AuthUser {
  id: number
  role: string
  groupId?: number
  isHeadman: boolean  // derived from JWT is_headman claim
}

export async function loginApi(credentials: LoginRequest): Promise<TokenResponse> {
  const { data } = await apiClient.post<TokenResponse>('/auth/login', credentials)
  return data
}

export async function logoutApi(refreshToken: string | null): Promise<void> {
  if (!refreshToken) return
  await apiClient.post('/auth/logout', { refreshToken })
}

export async function refreshApi(refreshToken: string): Promise<TokenResponse> {
  const { data } = await apiClient.post<TokenResponse>('/auth/refresh', { refreshToken })
  return data
}
