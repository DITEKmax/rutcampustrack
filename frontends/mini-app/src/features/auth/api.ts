import { retrieveLaunchParams } from '@telegram-apps/sdk-react'
import { bareAxios } from '@/shared/lib/axios'
import type { TmaAuthResponse } from './types'

export async function tmaAuthApi(initDataRaw: string): Promise<TmaAuthResponse> {
  // Use bareAxios (not apiClient) to avoid 401 interceptor loop (Pitfall 5)
  const { data } = await bareAxios.post<TmaAuthResponse>('/api/auth/tma', {
    initData: initDataRaw,
  })
  return data
}

export function getInitDataRaw(): string {
  const launchParams = retrieveLaunchParams()
  return launchParams.initDataRaw ?? ''
}
