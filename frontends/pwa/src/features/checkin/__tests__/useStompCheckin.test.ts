import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook } from '@testing-library/react'

// Track constructor calls
let mockClientInstances: Array<{
  activate: ReturnType<typeof vi.fn>
  deactivate: ReturnType<typeof vi.fn>
  subscribe: ReturnType<typeof vi.fn>
  onConnect: (() => void) | null
  webSocketFactory: (() => unknown) | null
}>

vi.mock('@stomp/stompjs', () => {
  return {
    Client: vi.fn().mockImplementation((config: { onConnect?: () => void; webSocketFactory?: () => unknown }) => {
      const instance = {
        activate: vi.fn(),
        deactivate: vi.fn().mockResolvedValue(undefined),
        subscribe: vi.fn(),
        onConnect: config.onConnect ?? null,
        webSocketFactory: config.webSocketFactory ?? null,
      }
      mockClientInstances.push(instance)
      return instance
    }),
  }
})

vi.mock('sockjs-client', () => ({
  default: vi.fn().mockImplementation((url: string) => ({ url })),
}))

import { useStompCheckin } from '../useStompCheckin'
import SockJSImport from 'sockjs-client'

const SockJS = vi.mocked(SockJSImport)

describe('useStompCheckin', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockClientInstances = []
  })

  it('creates Client with webSocketFactory using SockJS and token', () => {
    const getAccessToken = () => 'test-jwt-token'

    renderHook(() => useStompCheckin(5, getAccessToken, vi.fn()))

    expect(mockClientInstances).toHaveLength(1)
    const instance = mockClientInstances[0]
    expect(instance.webSocketFactory).toBeDefined()

    // Call the factory and verify it uses the token
    instance.webSocketFactory!()
    expect(SockJS).toHaveBeenCalledWith('/api/ws?token=test-jwt-token')
  })

  it('subscribes to /topic/group/{groupId} on connect', async () => {
    const getAccessToken = () => 'test-token'

    renderHook(() => useStompCheckin(5, getAccessToken, vi.fn()))

    const instance = mockClientInstances[0]
    expect(instance.activate).toHaveBeenCalled()

    // Simulate onConnect
    instance.onConnect!()
    expect(instance.subscribe).toHaveBeenCalledWith(
      '/topic/group/5',
      expect.any(Function)
    )
  })

  it('calls onMarked when attendance.marked event received', () => {
    const onMarked = vi.fn()
    const getAccessToken = () => 'test-token'

    renderHook(() => useStompCheckin(5, getAccessToken, onMarked))

    const instance = mockClientInstances[0]
    instance.onConnect!()

    // Get the subscription callback
    const subscribeCallback = instance.subscribe.mock.calls[0][1]

    // Simulate an attendance.marked message
    subscribeCallback({
      body: JSON.stringify({
        type: 'attendance.marked',
        payload: { lesson_id: 1, user_id: 42, group_id: 5, status: 'present', marked_by: 'student_geo' },
      }),
    })

    expect(onMarked).toHaveBeenCalledWith({
      lesson_id: 1,
      user_id: 42,
      group_id: 5,
      status: 'present',
      marked_by: 'student_geo',
    })
  })

  it('ignores events with different type (not attendance.marked)', () => {
    const onMarked = vi.fn()
    const getAccessToken = () => 'test-token'

    renderHook(() => useStompCheckin(5, getAccessToken, onMarked))

    const instance = mockClientInstances[0]
    instance.onConnect!()

    const subscribeCallback = instance.subscribe.mock.calls[0][1]

    // Send a different event type
    subscribeCallback({
      body: JSON.stringify({
        type: 'lesson.started',
        payload: { lesson_id: 1 },
      }),
    })

    expect(onMarked).not.toHaveBeenCalled()
  })

  it('deactivates client on unmount', () => {
    const getAccessToken = () => 'test-token'

    const { unmount } = renderHook(() => useStompCheckin(5, getAccessToken, vi.fn()))

    const instance = mockClientInstances[0]
    expect(instance.deactivate).not.toHaveBeenCalled()

    unmount()
    expect(instance.deactivate).toHaveBeenCalled()
  })
})
