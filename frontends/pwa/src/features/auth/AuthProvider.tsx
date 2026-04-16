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
  setRefreshTokenGetter,
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

const STORAGE_KEY = 'rct.auth.v1'

interface PersistedTokens {
  accessToken: string
  refreshToken: string
}

function readPersisted(): PersistedTokens | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<PersistedTokens>
    if (
      parsed &&
      typeof parsed.accessToken === 'string' &&
      typeof parsed.refreshToken === 'string'
    ) {
      return { accessToken: parsed.accessToken, refreshToken: parsed.refreshToken }
    }
    return null
  } catch {
    return null
  }
}

function writePersisted(tokens: PersistedTokens | null): void {
  try {
    if (tokens === null) {
      localStorage.removeItem(STORAGE_KEY)
    } else {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens))
    }
  } catch {
    // Storage disabled — degrade gracefully.
  }
}

function parseJwt(token: string): {
  sub: string
  role: string
  groupId?: number
  group_id?: number
  is_headman?: boolean
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
  const idNum = Number(payload.sub)
  if (!Number.isFinite(idNum)) {
    throw new Error('Invalid JWT: sub is not numeric')
  }
  // auth-service JwtService emits the `group_id` claim (snake_case, see
  // services/auth-service/.../JwtService.java). Older tests and some legacy
  // tokens used `groupId` — accept both so a token refresh is not required.
  const rawGroupId =
    typeof payload.group_id === 'number'
      ? payload.group_id
      : typeof payload.groupId === 'number'
        ? payload.groupId
        : undefined
  return {
    id: idNum,
    role: payload.role,
    groupId: rawGroupId,
    isHeadman: payload.is_headman ?? false,
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const persisted = readPersisted()
  const [accessToken, setAccessToken] = useState<string | null>(persisted?.accessToken ?? null)
  const [refreshToken, setRefreshToken] = useState<string | null>(persisted?.refreshToken ?? null)
  const [user, setUser] = useState<AuthUser | null>(() => {
    if (!persisted) return null
    try {
      return tokenToUser(persisted.accessToken)
    } catch {
      return null
    }
  })
  const tokenRef = useRef<string | null>(persisted?.accessToken ?? null)
  const refreshRef = useRef<string | null>(persisted?.refreshToken ?? null)

  // Keep refs in sync with state
  useEffect(() => {
    tokenRef.current = accessToken
  }, [accessToken])
  useEffect(() => {
    refreshRef.current = refreshToken
  }, [refreshToken])

  // Wire Axios interceptor callbacks on mount
  useEffect(() => {
    setAccessTokenGetter(() => tokenRef.current)
    setRefreshTokenGetter(() => refreshRef.current)

    setTokenRefreshCallback((newAccess: string, newRefresh: string) => {
      tokenRef.current = newAccess
      refreshRef.current = newRefresh
      setAccessToken(newAccess)
      setRefreshToken(newRefresh)
      setUser(tokenToUser(newAccess))
      writePersisted({ accessToken: newAccess, refreshToken: newRefresh })
    })

    setAuthLogoutCallback(() => {
      tokenRef.current = null
      refreshRef.current = null
      setAccessToken(null)
      setRefreshToken(null)
      setUser(null)
      writePersisted(null)
    })

    // Cross-tab sync: listen for storage writes from other tabs.
    const onStorage = (event: StorageEvent) => {
      if (event.key !== STORAGE_KEY) return
      if (!event.newValue) {
        tokenRef.current = null
        refreshRef.current = null
        setAccessToken(null)
        setRefreshToken(null)
        setUser(null)
        return
      }
      try {
        const parsed = JSON.parse(event.newValue) as Partial<PersistedTokens>
        if (
          typeof parsed.accessToken === 'string' &&
          typeof parsed.refreshToken === 'string'
        ) {
          tokenRef.current = parsed.accessToken
          refreshRef.current = parsed.refreshToken
          setAccessToken(parsed.accessToken)
          setRefreshToken(parsed.refreshToken)
          setUser(tokenToUser(parsed.accessToken))
        }
      } catch {
        // Malformed write — ignore.
      }
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const login = useCallback(async (credentials: LoginRequest) => {
    const response = await loginApi(credentials)
    tokenRef.current = response.accessToken
    refreshRef.current = response.refreshToken
    setAccessToken(response.accessToken)
    setRefreshToken(response.refreshToken)
    setUser(tokenToUser(response.accessToken))
    writePersisted({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
    })
  }, [])

  const logout = useCallback(async () => {
    try {
      await logoutApi(refreshRef.current)
    } finally {
      tokenRef.current = null
      refreshRef.current = null
      setAccessToken(null)
      setRefreshToken(null)
      setUser(null)
      writePersisted(null)
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
