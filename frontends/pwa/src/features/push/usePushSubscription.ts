import { useCallback, useEffect, useState } from 'react'
import { fetchVapidPublicKey, subscribePush, unsubscribePush } from './api'
import { appPath, PWA_ICON_URL, urlBase64ToUint8Array } from './pushUtils'

export type PushState = 'unsupported' | 'default' | 'granted' | 'denied'

function isPushSupported(): boolean {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') return false
  return (
    'Notification' in window &&
    'PushManager' in window &&
    'serviceWorker' in navigator &&
    window.isSecureContext !== false
  )
}

function getNotificationPermission(): PushState {
  if (!isPushSupported()) return 'unsupported'
  return Notification.permission as PushState
}

export function usePushSubscription() {
  const [state, setState] = useState<PushState>(() => getNotificationPermission())
  const [subscribed, setSubscribed] = useState(false)
  const [checking, setChecking] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setError(null)
    const permission = getNotificationPermission()
    setState(permission)
    if (permission !== 'granted') {
      setSubscribed(false)
      return
    }

    setChecking(true)
    try {
      const reg = await navigator.serviceWorker.ready
      const sub = await reg.pushManager.getSubscription()
      if (!sub) {
        setSubscribed(false)
        return
      }
      await subscribePush(sub)
      setSubscribed(true)
    } catch (e) {
      setSubscribed(false)
      setError(e instanceof Error ? e.message : 'Failed to verify push subscription')
    } finally {
      setChecking(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const subscribe = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      if (!isPushSupported()) {
        setState('unsupported')
        setSubscribed(false)
        return
      }

      const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent)
      const isStandalone = (typeof window.matchMedia === 'function'
        && window.matchMedia('(display-mode: standalone)').matches)
        || (navigator as Navigator & { standalone?: boolean }).standalone === true
      if (isIOS && !isStandalone) {
        setError('iOS_NOT_STANDALONE')
        return
      }

      // PUSHUI-03: requestPermission ONLY on explicit user gesture, never on page load.
      const permission = await Notification.requestPermission()
      setState(permission as PushState)
      if (permission !== 'granted') {
        setSubscribed(false)
        return
      }

      const reg = await navigator.serviceWorker.ready
      const existingSub = await reg.pushManager.getSubscription()
      const sub = existingSub ?? await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(await fetchVapidPublicKey()),
      })
      await subscribePush(sub)
      setSubscribed(true)
    } catch (e) {
      setSubscribed(false)
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
      setSubscribed(false)
      setState(getNotificationPermission())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to unsubscribe')
    } finally {
      setLoading(false)
    }
  }, [])

  const showTestNotification = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      if (getNotificationPermission() !== 'granted') {
        setState(getNotificationPermission())
        setError('PERMISSION_NOT_GRANTED')
        return
      }

      const reg = await navigator.serviceWorker.ready
      await reg.showNotification('RutTrack', {
        body: 'Если это видно в шторке, системные уведомления для PWA включены.',
        icon: PWA_ICON_URL,
        badge: PWA_ICON_URL,
        data: { url: appPath('/notifications') },
        tag: 'ruttrack-test-notification',
        silent: false,
        vibrate: [160, 80, 160],
        renotify: true,
      } as NotificationOptions & { vibrate?: number[]; renotify?: boolean })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to show test notification')
    } finally {
      setLoading(false)
    }
  }, [])

  return {
    state,
    subscribed,
    checking,
    loading,
    error,
    subscribe,
    unsubscribe,
    refresh,
    showTestNotification,
  }
}
