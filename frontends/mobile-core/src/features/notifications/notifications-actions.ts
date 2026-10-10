import type { NotificationAction, NotificationSignedRole } from './notifications-v2-models'
export interface NotificationActionContext {
  generation: number
  userId: string
  role: NotificationSignedRole | null
  authorityKey: string
  online: boolean
}
export interface NotificationActionPort {
  open(action: NotificationAction, isCurrent: () => boolean): boolean | Promise<boolean>
}
/** Every async protected read and final route commit uses the same signed context. */
export async function executeNotificationAction(
  action: NotificationAction,
  current: () => NotificationActionContext,
  port: NotificationActionPort,
): Promise<boolean> {
  const captured = { ...current() }
  const isCurrent = (): boolean => {
    const now = current()
    return now.online && now.generation === captured.generation && now.userId === captured.userId
      && now.role === captured.role && now.authorityKey === captured.authorityKey
  }
  if (!captured.online || !captured.userId || captured.role !== action.role) return false
  const opened = await port.open(action, isCurrent)
  return isCurrent() && opened
}
export function notificationNumericId(value: string): number | null {
  if (!/^[1-9]\d*$/.test(value)) return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}
