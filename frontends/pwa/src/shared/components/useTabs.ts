import { useMemo } from 'react'
import { House, Calendar, Fingerprint, Users, User, type Icon } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'

export interface Tab {
  to: string
  icon: Icon
  label: string
}

export function useTabs(): Tab[] {
  const { user } = useAuth()
  return useMemo(() => {
    // Per D-01: Главная → Расписание → Отметка → [Группа] → Профиль
    // "Группа" is inserted before Профиль only for headmen.
    const tabs: Tab[] = [
      { to: '/home', icon: House, label: 'Главная' },
      { to: '/schedule', icon: Calendar, label: 'Расписание' },
      { to: '/checkin', icon: Fingerprint, label: 'Отметка' },
      ...(user?.isHeadman
        ? [{ to: '/group', icon: Users, label: 'Группа' } as Tab]
        : []),
      { to: '/profile', icon: User, label: 'Профиль' },
    ]
    return tabs
  }, [user?.isHeadman])
}
