import { useMemo } from 'react'
import {
  Bell,
  Calendar,
  Fingerprint,
  House,
  Users,
  type Icon,
} from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'

export interface Tab {
  to: string
  icon: Icon
  label: string
  /** Name of a badge to read from NotificationCenter (e.g. "unread"). */
  badge?: 'unread'
}

export function useTabs(): Tab[] {
  const { user } = useAuth()
  return useMemo(() => {
    // Primary tabs only: Главная → Расписание → Отметка → Уведомления →
    // [Группа]. Secondary destinations (ДЗ, Профиль) live in the drawer
    // menu opened from the header, to keep the bar compact for headmen.
    const tabs: Tab[] = [
      { to: '/home', icon: House, label: 'Главная' },
      { to: '/schedule', icon: Calendar, label: 'Расписание' },
      { to: '/checkin', icon: Fingerprint, label: 'Отметка' },
      { to: '/notifications', icon: Bell, label: 'Уведомл.', badge: 'unread' },
      ...(user?.isHeadman
        ? [{ to: '/group', icon: Users, label: 'Группа' } as Tab]
        : []),
    ]
    return tabs
  }, [user?.isHeadman])
}
