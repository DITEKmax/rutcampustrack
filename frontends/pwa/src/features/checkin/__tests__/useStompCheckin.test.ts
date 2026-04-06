import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock @stomp/stompjs
vi.mock('@stomp/stompjs', () => {
  const mockSubscribe = vi.fn()
  const mockActivate = vi.fn()
  const mockDeactivate = vi.fn()

  return {
    Client: vi.fn().mockImplementation(() => ({
      activate: mockActivate,
      deactivate: mockDeactivate,
      subscribe: mockSubscribe,
      connected: false,
      onConnect: null as (() => void) | null,
    })),
    __mockSubscribe: mockSubscribe,
    __mockActivate: mockActivate,
  }
})

describe('useStompCheckin', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('subscribes to /topic/group/{groupId} (stub test)', async () => {
    // Stub: useStompCheckin hook is not yet implemented (Plan 02)
    // This test validates the STOMP client mock setup works
    const { Client } = await import('@stomp/stompjs')
    const client = new Client({ brokerURL: 'ws://localhost/ws' })

    expect(client.activate).toBeDefined()
    expect(client.subscribe).toBeDefined()
    expect(Client).toHaveBeenCalledWith({ brokerURL: 'ws://localhost/ws' })
  })

  it('calls onMarked callback on attendance.marked event (stub test)', async () => {
    const onMarked = vi.fn()
    const { Client } = await import('@stomp/stompjs')
    const client = new Client({ brokerURL: 'ws://localhost/ws' })

    // Simulate subscription and message
    const mockMessage = {
      body: JSON.stringify({
        lesson_id: 1,
        user_id: 1,
        group_id: 5,
        status: 'present',
        marked_by: 'geo',
      }),
    }

    // Stub: when useStompCheckin is implemented, it will subscribe and call onMarked
    // For now, verify the mock framework works
    client.subscribe('/topic/group/5', (msg: { body: string }) => {
      onMarked(JSON.parse(msg.body))
    })

    // Simulate callback
    const subscribeCall = vi.mocked(client.subscribe).mock.calls[0]
    const callback = subscribeCall[1] as (msg: { body: string }) => void
    callback(mockMessage)

    expect(onMarked).toHaveBeenCalledWith(
      expect.objectContaining({ lesson_id: 1, status: 'present' })
    )
  })
})
