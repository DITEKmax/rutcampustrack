import {
  createContext,
  useContext,
  useState,
  useEffect,
  useRef,
  useCallback,
  type ReactNode,
} from 'react'
import {
  setAccessTokenGetter,
  setTokenRefreshCallback,
  setAuthLogoutCallback,
} from '@/shared/lib/axios'
import { loginApi, logoutApi, type LoginRequest, type AuthUser } from './api'

interface AuthContextValue {
  isAuthenticated: boolean
  user: AuthUser | null
  accessToken: string | null
  login: (credentials: LoginRequest) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function parseJwt(token: string): { sub: string; role: string; groupId?: number; is_headman?: boolean } {
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
  const idNum = Number(payload.sub)
  if (!Number.isFinite(idNum)) {
    throw new Error('Invalid JWT: sub is not numeric')
  }
  return {
    id: idNum,
    role: payload.role,
    groupId: typeof payload.groupId === 'number' ? payload.groupId : undefined,
    isHeadman: payload.is_headman ?? false,
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [user, setUser] = useState<AuthUser | null>(null)
  const tokenRef = useRef<string | null>(null)

  // Keep ref in sync with state
  useEffect(() => {
    tokenRef.current = accessToken
  }, [accessToken])

  // Wire Axios interceptor callbacks on mount
  useEffect(() => {
    setAccessTokenGetter(() => tokenRef.current)

    setTokenRefreshCallback((newToken: string) => {
      tokenRef.current = newToken
      setAccessToken(newToken)
      setUser(tokenToUser(newToken))
    })

    setAuthLogoutCallback(() => {
      tokenRef.current = null
      setAccessToken(null)
      setUser(null)
    })
  }, [])

  const login = useCallback(async (credentials: LoginRequest) => {
    const response = await loginApi(credentials)
    tokenRef.current = response.accessToken
    setAccessToken(response.accessToken)
    setUser(tokenToUser(response.accessToken))
  }, [])

  const logout = useCallback(async () => {
    try {
      await logoutApi()
    } finally {
      tokenRef.current = null
      setAccessToken(null)
      setUser(null)
    }
  }, [])

  const isAuthenticated = !!accessToken

  return (
    <AuthContext.Provider value={{ isAuthenticated, user, accessToken, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
