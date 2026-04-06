import { createContext, useContext, useState, useEffect, useRef, useCallback, type ReactNode } from 'react'
import { useAuth } from '@/features/auth/AuthProvider'
import { useStompCheckin } from './useStompCheckin'
import type { AttendanceMarkedPayload } from './types'
import type { AttendanceStatus } from '@/features/schedule/types'

interface StompContextValue {
  attendanceCounts: Record<number, number>
  personalStatuses: Record<number, AttendanceStatus | null>
  markPersonalStatus: (lessonId: number, status: AttendanceStatus) => void
}

const StompContext = createContext<StompContextValue | null>(null)

export function StompProvider({ children }: { children: ReactNode }) {
  const { user, accessToken } = useAuth()
  const groupId = user?.groupId ?? 0
  const userId = user?.id ?? 0

  // Keep accessToken ref in sync for STOMP factory (Pitfall 7)
  const accessTokenRef = useRef(accessToken)
  useEffect(() => {
    accessTokenRef.current = accessToken
  }, [accessToken])

  const [attendanceCounts, setAttendanceCounts] = useState<Record<number, number>>({})
  const [personalStatuses, setPersonalStatuses] = useState<Record<number, AttendanceStatus | null>>({})

  const handleMarked = useCallback(
    (payload: AttendanceMarkedPayload) => {
      setAttendanceCounts((prev) => ({
        ...prev,
        [payload.lesson_id]: (prev[payload.lesson_id] ?? 0) + 1,
      }))
      if (payload.user_id === userId) {
        setPersonalStatuses((prev) => ({
          ...prev,
          [payload.lesson_id]: payload.status as AttendanceStatus,
        }))
      }
    },
    [userId]
  )

  useStompCheckin(groupId, () => accessTokenRef.current, handleMarked)

  const markPersonalStatus = useCallback((lessonId: number, status: AttendanceStatus) => {
    setPersonalStatuses((prev) => ({ ...prev, [lessonId]: status }))
  }, [])

  return (
    <StompContext.Provider value={{ attendanceCounts, personalStatuses, markPersonalStatus }}>
      {children}
    </StompContext.Provider>
  )
}

export function useStompEvents(): StompContextValue {
  const context = useContext(StompContext)
  if (!context) {
    throw new Error('useStompEvents must be used within a StompProvider')
  }
  return context
}
