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
import {
  loginApi,
  logoutApi,
  refreshApi,
  type LoginRequest,
  type AuthUser,
} from './api'
import { clearAllClientState } from './clearAllClientState'

interface AuthContextValue {
  isAuthenticated: boolean
  user: AuthUser | null
  accessToken: string | null
  /** true во время первичного `refresh` на mount. Пока true — guards не знают authenticated-состояние. */
  isBootstrapping: boolean
  login: (credentials: LoginRequest) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * M03b Группа 6: legacy ключ `rct.auth.v1` больше НЕ используется
 * (refresh теперь в HttpOnly cookie). Migration helper удаляет старый blob
 * со всех устройств после первой загрузки PWA post-M03b.
 */
const LEGACY_STORAGE_KEY = 'rct.auth.v1'

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
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [user, setUser] = useState<AuthUser | null>(null)
  const [isBootstrapping, setIsBootstrapping] = useState<boolean>(true)
  const tokenRef = useRef<string | null>(null)

  useEffect(() => {
    tokenRef.current = accessToken
  }, [accessToken])

  // Migration helper — удаляет legacy blob после upgrade.
  useEffect(() => {
    try {
      if (localStorage.getItem(LEGACY_STORAGE_KEY)) {
        localStorage.removeItem(LEGACY_STORAGE_KEY)
      }
    } catch { /* storage disabled */ }
  }, [])

  // Axios interceptor wiring + bootstrap refresh on mount.
  useEffect(() => {
    setAccessTokenGetter(() => tokenRef.current)

    setTokenRefreshCallback((newAccess: string) => {
      tokenRef.current = newAccess
      setAccessToken(newAccess)
      try {
        setUser(tokenToUser(newAccess))
      } catch {
        setUser(null)
      }
    })

    setAuthLogoutCallback(() => {
      tokenRef.current = null
      setAccessToken(null)
      setUser(null)
    })

    // Bootstrap: попытка восстановить сессию через cookie-based refresh.
    // Если cookie живёт — получаем новый access в memory. Если нет — остаёмся
    // unauth'ed и redirect to /login происходит через guards.
    let cancelled = false
    refreshApi()
      .then((response) => {
        if (cancelled) return
        tokenRef.current = response.accessToken
        setAccessToken(response.accessToken)
        try {
          setUser(tokenToUser(response.accessToken))
        } catch {
          setUser(null)
        }
      })
      .catch(() => {
        // 401 → no valid refresh cookie → остаёмся unauth'ed.
      })
      .finally(() => {
        if (!cancelled) setIsBootstrapping(false)
      })

    return () => {
      cancelled = true
    }
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
    } catch {
      // best-effort — всё равно чистим локально.
    }
    tokenRef.current = null
    setAccessToken(null)
    setUser(null)
    await clearAllClientState()
  }, [])

  const isAuthenticated = !!accessToken

  return (
    <AuthContext.Provider
      value={{ isAuthenticated, user, accessToken, isBootstrapping, login, logout }}
    >
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
