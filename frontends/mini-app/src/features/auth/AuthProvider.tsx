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
import { tmaAuthApi, getInitDataRaw } from './api'
import { LoadingScreen } from './LoadingScreen'
import { ErrorScreen } from './ErrorScreen'
import type { AuthUser } from './types'

type AuthState = 'loading' | 'authenticated' | 'error'

interface AuthContextValue {
  isAuthenticated: boolean
  user: AuthUser | null
  accessToken: string | null
}

const AuthContext = createContext<AuthContextValue | null>(null)

function parseJwt(token: string): { sub: string; role: string; groupId?: number } {
  const base64Url = token.split('.')[1]
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
  return { id: Number(payload.sub), role: payload.role, groupId: payload.groupId }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authState, setAuthState] = useState<AuthState>('loading')
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [user, setUser] = useState<AuthUser | null>(null)
  const tokenRef = useRef<string | null>(null)
  const initDataRef = useRef<string>('')

  const authenticate = useCallback(async (initDataRaw: string) => {
    setAuthState('loading')
    try {
      const response = await tmaAuthApi(initDataRaw)
      tokenRef.current = response.accessToken
      setAccessToken(response.accessToken)
      setUser(tokenToUser(response.accessToken))
      setAuthState('authenticated')
    } catch {
      setAuthState('error')
    }
  }, [])

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
    })
  }, [authenticate])

  if (authState === 'loading') return <LoadingScreen />
  if (authState === 'error') return <ErrorScreen onRetry={() => authenticate(initDataRef.current)} />

  return (
    <AuthContext.Provider value={{ isAuthenticated: true, user, accessToken }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within an AuthProvider')
  return context
}
