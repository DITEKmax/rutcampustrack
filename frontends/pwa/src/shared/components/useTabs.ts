import { useMemo } from 'react'
import {
  Bell,
  Calendar,
  Fingerprint,
  House,
  User,
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
    // Tab layout: Главная → Расписание → Отметка → Уведомления →
    //             [Группа] → Профиль. "Группа" only for headmen.
    const tabs: Tab[] = [
      { to: '/home', icon: House, label: 'Главная' },
      { to: '/schedule', icon: Calendar, label: 'Расписание' },
      { to: '/checkin', icon: Fingerprint, label: 'Отметка' },
      { to: '/notifications', icon: Bell, label: 'Уведомл.', badge: 'unread' },
      ...(user?.isHeadman
        ? [{ to: '/group', icon: Users, label: 'Группа' } as Tab]
        : []),
      { to: '/profile', icon: User, label: 'Профиль' },
    ]
    return tabs
  }, [user?.isHeadman])
}
