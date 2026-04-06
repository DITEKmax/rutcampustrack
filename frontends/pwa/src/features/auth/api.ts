import { apiClient } from '@/shared/lib/axios'

export interface LoginRequest {
  login: string
  password: string
}

export interface AccessTokenResponse {
  accessToken: string
  expiresIn: number
}

export interface AuthUser {
  id: number
  role: string
  groupId?: number
}

export async function loginApi(credentials: LoginRequest): Promise<AccessTokenResponse> {
  const { data } = await apiClient.post<AccessTokenResponse>('/auth/login', credentials)
  return data
}

export async function logoutApi(): Promise<void> {
  await apiClient.post('/auth/logout', {})
}

export async function refreshApi(): Promise<AccessTokenResponse> {
  const { data } = await apiClient.post<AccessTokenResponse>('/auth/refresh', {})
  return data
}