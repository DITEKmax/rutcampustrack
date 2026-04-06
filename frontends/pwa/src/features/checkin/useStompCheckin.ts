import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { AttendanceMarkedPayload } from './types'

export function useStompCheckin(
  groupId: number,
  getAccessToken: () => string | null,
  onMarked: (payload: AttendanceMarkedPayload) => void
) {
  const onMarkedRef = useRef(onMarked)
  useEffect(() => {
    onMarkedRef.current = onMarked
  }, [onMarked])

  useEffect(() => {
    if (!groupId) return

    const client = new Client({
      webSocketFactory: () => new SockJS(`/api/ws?token=${getAccessToken() ?? ''}`),
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
  }, [groupId]) // getAccessToken is a factory (stable ref), not a dependency
}
