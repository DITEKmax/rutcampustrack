import { useState, useCallback } from 'react'
import { fetchVapidPublicKey, subscribePush, unsubscribePush } from './api'

function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = atob(base64)
  return new Uint8Array([...rawData].map((c) => c.charCodeAt(0)))
}

export type PushState = 'unsupported' | 'default' | 'granted' | 'denied'

export function usePushSubscription() {
  const [state, setState] = useState<PushState>(() => {
    if (typeof window === 'undefined') return 'unsupported'
    if (!('Notification' in window) || !('PushManager' in window)) return 'unsupported'
    return Notification.permission as PushState
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const subscribe = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // iOS standalone guard -- Web Push only works in standalone mode on iOS 16.4+
      const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent)
      const isStandalone = window.matchMedia('(display-mode: standalone)').matches
        || (navigator as Navigator & { standalone?: boolean }).standalone === true
      if (isIOS && !isStandalone) {
        setError('iOS_NOT_STANDALONE')
        return
      }

      // PUSHUI-03: requestPermission ONLY on explicit user gesture, never on page load
      const permission = await Notification.requestPermission()
      setState(permission as PushState)
      if (permission !== 'granted') return

      const reg = await navigator.serviceWorker.ready
      const vapidKey = await fetchVapidPublicKey()
      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidKey),
      })
      await subscribePush(sub)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to subscribe')
    } finally {
      setLoading(false)
    }
  }, [])

  const unsubscribe = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const reg = await navigator.serviceWorker.ready
      const sub = await reg.pushManager.getSubscription()
      if (sub) {
        await unsubscribePush(sub.endpoint)
        await sub.unsubscribe()
      }
      setState('default')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to unsubscribe')
    } finally {
      setLoading(false)
    }
  }, [])

  return { state, loading, error, subscribe, unsubscribe }
}
