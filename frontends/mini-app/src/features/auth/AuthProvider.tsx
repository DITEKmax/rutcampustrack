import {
  createContext,
  useContext,
  useState,
  useEffect,
  useRef,
  useCallback,
  type ReactNode,
} from 'react'
import { setAccessTokenGetter, setReAuthCallback } from '@/shared/lib/axios'
import { getProblemDetails } from '@/api/problemDetails'
import { tmaAuthApi, getInitDataRaw, tmaLogoutApi } from './api'
import { LoadingScreen } from './LoadingScreen'
import { ErrorScreen } from './ErrorScreen'
import type { AuthUser } from './types'

type AuthState = 'loading' | 'authenticated' | 'error' | 'unlinked'

interface AuthContextValue {
  isAuthenticated: boolean
  user: AuthUser | null
  accessToken: string | null
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function parseJwt(token: string): {
  sub?: string
  role?: string
  groupId?: number
  group_id?: number
  is_headman?: boolean
  isHeadman?: boolean
} {
  const parts = token.split('.')
  if (parts.length !== 3) {
    throw new Error('Malformed JWT: expected 3 segments')
  }
  const base64Url = parts[1]
  const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
  const json = decodeURIComponent(
    atob(base64)
      .split('')
      .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
      .join('')
  )
  return JSON.parse(json)
}

function tokenToUser(token: string): AuthUser {
  const payload = parseJwt(token)
  const id = Number(payload.sub)
  if (!Number.isFinite(id) || !payload.role) {
    throw new Error('Invalid JWT: missing user claims')
  }
  const rawGroupId =
    typeof payload.group_id === 'number'
      ? payload.group_id
      : typeof payload.groupId === 'number'
        ? payload.groupId
        : undefined
  return {
    id,
    role: payload.role,
    groupId: rawGroupId,
    isHeadman: payload.is_headman ?? payload.isHeadman ?? false,
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authState, setAuthState] = useState<AuthState>('loading')
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [user, setUser] = useState<AuthUser | null>(null)
  const tokenRef = useRef<string | null>(null)
  const refreshTokenRef = useRef<string | null>(null)
  const initDataRef = useRef<string>('')

  const authenticate = useCallback(async (initDataRaw: string) => {
    setAuthState('loading')
    try {
      const response = await tmaAuthApi(initDataRaw)
      const nextUser = tokenToUser(response.accessToken)
      tokenRef.current = response.accessToken
      refreshTokenRef.current = response.refreshToken ?? null
      setAccessToken(response.accessToken)
      setUser(nextUser)
      setAuthState('authenticated')
    } catch (error) {
      tokenRef.current = null
      refreshTokenRef.current = null
      setAccessToken(null)
      setUser(null)
      const problem = getProblemDetails(error)
      setAuthState(problem.status === 401 ? 'unlinked' : 'error')
    }
  }, [])

  const logout = useCallback(async () => {
    const refreshToken = refreshTokenRef.current
    tokenRef.current = null
    refreshTokenRef.current = null
    setAccessToken(null)
    setUser(null)
    try {
      sessionStorage.clear()
    } catch {
      // storage may be disabled inside WebView
    }
    try {
      await tmaLogoutApi(refreshToken)
    } catch {
      // best-effort local logout
    }
    await authenticate(initDataRef.current)
  }, [authenticate])

  // Auth on mount (D-04)
  useEffect(() => {
    const raw = getInitDataRaw()
    initDataRef.current = raw
    authenticate(raw)
  }, [authenticate])

  // Wire axios interceptor (D-05, D-06)
  useEffect(() => {
    setAccessTokenGetter(() => tokenRef.current)
    setReAuthCallback(async () => {
      await authenticate(initDataRef.current)
      if (!tokenRef.current) {
        throw new Error('TMA re-auth failed')
      }
    })
  }, [authenticate])

  if (authState === 'loading') return <LoadingScreen />
  if (authState === 'unlinked') {
    return (
      <ErrorScreen
        title="Telegram не привязан"
        detail="Откройте /start в боте RutTrack или обратитесь к администратору, чтобы привязать Telegram к студенческому аккаунту."
        retryLabel="Проверить снова"
        onRetry={() => authenticate(initDataRef.current)}
      />
    )
  }
  if (authState === 'error') return <ErrorScreen onRetry={() => authenticate(initDataRef.current)} />

  return (
    <AuthContext.Provider value={{ isAuthenticated: true, user, accessToken, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within an AuthProvider')
  return context
}
