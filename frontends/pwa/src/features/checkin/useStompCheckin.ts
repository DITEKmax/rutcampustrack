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
  groupId: number,
  onMarked: (payload: AttendanceMarkedPayload) => void
) {
  const onMarkedRef = useRef(onMarked)
  useEffect(() => {
    onMarkedRef.current = onMarked
  }, [onMarked])

  useEffect(() => {
    if (!groupId) return

    const client = new Client({
      webSocketFactory: async () => new SockJS(await buildWsUrl()),
      reconnectDelay: 1000,
      onConnect: () => {
        client.subscribe(`/topic/group/${groupId}`, (message) => {
          try {
            const envelope = JSON.parse(message.body)
            if (envelope.type === 'attendance.marked') {
              onMarkedRef.current(envelope.payload)
            }
          } catch {
            // Ignore malformed messages
          }
        })
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'])
      },
    })

    client.activate()

    return () => {
      client.deactivate()
    }
  }, [groupId])
}
