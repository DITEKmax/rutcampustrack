import { retrieveRawInitData } from '@telegram-apps/sdk-react'
import { bareAxios } from '@/shared/lib/axios'
import type { TmaAuthRequest, TokenResponse } from '@/api/schema'

export async function tmaAuthApi(initDataRaw: string): Promise<TokenResponse> {
  // Use bareAxios (not apiClient) to avoid 401 interceptor loop (Pitfall 5)
  const request: TmaAuthRequest = {
    initData: initDataRaw,
  }
  const { data } = await bareAxios.post<TokenResponse>('/api/auth/tma', request)
  return data
}

export async function tmaLogoutApi(refreshToken: string | null): Promise<void> {
  await bareAxios.post('/api/auth/logout', refreshToken ? { refreshToken } : null)
}

export function getInitDataRaw(): string {
  return retrieveRawInitData() ?? ''
}
