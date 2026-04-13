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
    const baseTabs: Tab[] = [
      { to: '/home', icon: House, label: 'Главная' },
      { to: '/schedule', icon: Calendar, label: 'Расписание' },
      { to: '/checkin', icon: Fingerprint, label: 'Отметка' },
      { to: '/profile', icon: User, label: 'Профиль' },
    ]
    if (user?.isHeadman) {
      // Insert Группа before Профиль (per D-01):
      // Главная → Расписание → Отметка → Группа → Профиль
      baseTabs.splice(baseTabs.length - 1, 0, {
        to: '/group',
        icon: Users,
        label: 'Группа',
      })
    }
    return baseTabs
  }, [user?.isHeadman])
}
