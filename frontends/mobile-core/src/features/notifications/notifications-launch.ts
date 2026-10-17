import { parseNotificationAction } from './notifications-v2-client'
import type { NotificationAction, NotificationSignedRole } from './notifications-v2-models'
const MAX_ENCODED = 384
const actionFields: Record<NotificationAction['kind'], readonly string[]> = {
  lesson: ['kind', 'role', 'lessonId', 'lessonDate'],
  request: ['kind', 'role', 'requestId', 'requestKind'],
  homework: ['kind', 'role', 'homeworkId', 'lessonDate'],
  group: ['kind', 'role', 'groupId'],
  'homework-week': ['kind', 'role', 'weekStart'],
}
export function decodeNotificationLaunch(encoded: string | null): NotificationAction | null {
  if (!encoded || encoded.length > MAX_ENCODED || !/^[A-Za-z0-9_-]+$/.test(encoded)) return null
  try {
    const bytes = Uint8Array.from(atob(encoded.replace(/-/g, '+').replace(/_/g, '/')), (c) =>
      c.charCodeAt(0),
    )
    const envelope: unknown = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes))
    if (!envelope || typeof envelope !== 'object' || Array.isArray(envelope)) return null
    const body = envelope as Record<string, unknown>
    if (body.v !== 2 || Object.keys(body).some((key) => key !== 'v' && key !== 'action'))
      return null
    const action = parseNotificationAction(body.action)
    if (
      !action ||
      !body.action ||
      typeof body.action !== 'object' ||
      Object.keys(body.action).some((key) => !actionFields[action.kind].includes(key))
    )
      return null
    return action
  } catch {
    return null
  }
}
export function encodeNotificationLaunch(action: NotificationAction): string | null {
  const bytes = new TextEncoder().encode(JSON.stringify({ v: 2, action }))
  const encoded = btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
  return encoded.length <= MAX_ENCODED && decodeNotificationLaunch(encoded) ? encoded : null
}
/** Launch data is intent only; the signed session and protected action port grant access. */
export class NotificationLaunchOwner {
  private boundUser: string | null
  private running = false
  constructor(
    private action: NotificationAction | null,
    expectedUserId: string | null = null,
  ) {
    this.boundUser = expectedUserId
  }
  get pendingRole(): NotificationSignedRole | null {
    return this.action?.role ?? null
  }
  clear(): void {
    this.action = null
  }
  async consume(
    userId: string | null,
    role: NotificationSignedRole | null,
    open: (action: NotificationAction) => Promise<boolean>,
  ): Promise<'waiting' | 'role-required' | 'opened' | 'unavailable' | 'empty'> {
    if (!this.action) return 'empty'
    if (!userId || !role || this.running) return 'waiting'
    if (this.boundUser && this.boundUser !== userId) {
      this.clear()
      return 'unavailable'
    }
    this.boundUser = userId
    if (role !== this.action.role) return 'role-required'
    const action = this.action
    this.action = null
    this.running = true
    try {
      return (await open(action)) ? 'opened' : 'unavailable'
    } catch {
      return 'unavailable'
    } finally {
      this.running = false
    }
  }
}
