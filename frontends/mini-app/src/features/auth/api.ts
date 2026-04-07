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
  // retrieveLaunchParams returns snake_case by default; initDataRaw is the raw URL-encoded string
  // Cast to any to bypass SDK type mismatch — value is always a string at runtime
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const launchParams = retrieveLaunchParams() as any
  const raw = launchParams.initDataRaw ?? launchParams.tgWebAppData ?? ''
  return typeof raw === 'string' ? raw : ''
}
