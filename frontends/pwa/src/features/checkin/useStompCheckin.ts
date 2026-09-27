import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { buildWsUrl } from '@/features/auth/wsTicket'
import type { AttendanceMarkedPayload } from './types'

/**
 * M03b Группа 6: webSocketFactory теперь pre-fetch'ит single-use ticket
 * из /auth/ws-ticket вместо передачи JWT в query. Параметр
 * `getAccessToken` удалён — access-token нужен только для endpoint'а
 * ws-ticket (axios interceptor ставит Bearer автоматически).
 */
export function useStompCheckin(
  groupId: number | null,
  userId: number | null,
  onMarked: (payload: AttendanceMarkedPayload) => void
) {
  const onMarkedRef = useRef(onMarked)
  useEffect(() => {
    onMarkedRef.current = onMarked
  }, [onMarked])

  useEffect(() => {
    if (userId === null || !Number.isSafeInteger(userId) || userId <= 0) return
    const validGroupId = groupId !== null
      && Number.isSafeInteger(groupId)
      && groupId > 0
      ? groupId
      : null

    const client = new Client({
      webSocketFactory: async () => new SockJS(await buildWsUrl()),
      reconnectDelay: 1000,
      onConnect: () => {
        const handle = (message: { body: string }) => {
          try {
            const envelope = JSON.parse(message.body)
            if (envelope.type === 'attendance.marked') {
              onMarkedRef.current(envelope.payload)
            }
          } catch {
            // Ignore malformed messages
          }
        }

        client.subscribe(`/topic/user/${userId}`, handle)
        if (validGroupId !== null) {
          client.subscribe(`/topic/group/${validGroupId}`, handle)
        }
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'])
      },
    })

    client.activate()

    return () => {
      client.deactivate()
    }
  }, [groupId, userId])
}
