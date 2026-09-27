import attendanceTab from '../assets/attendance-tab.svg'
import moreTab from '../assets/more-tab.svg'
import profileTab from '../assets/profile-tab.svg'
import scheduleTab from '../assets/schedule-tab.svg'
import todayTabActive from '../assets/today-tab-active.svg'
import teacherTodayTab from '../features/profile/assets/profile-nav-today.svg'
import teacherProfileTab from '../features/profile/assets/profile-nav-profile.svg'
import teacherAttendanceTab from '../features/profile/assets/profile-nav-attendance.svg'
import teacherMapTab from '../features/profile/assets/more-map.svg'
import teacherStatsTab from '../features/profile/assets/more-statistics.svg'
import type { MobileBottomNavItems } from './navigation'

export interface StudentNavigationOptions {
  homeworkEnabled?: boolean
  attendanceEnabled?: boolean
  moreEnabled?: boolean
  profileEnabled?: boolean
}

/**
 * The shell owns the single dock. Only routes with an actual caller-owned
 * implementation are enabled; the remaining accepted placeholders stay
 * visibly unavailable instead of claiming product coverage.
 */
export function createStudentNavigationItems(options: StudentNavigationOptions = {}): MobileBottomNavItems {
  const homeworkEnabled = options.homeworkEnabled === true
  const attendanceEnabled = options.attendanceEnabled === true
  const moreEnabled = options.moreEnabled === true
  const profileEnabled = options.profileEnabled === true
  return [
    { id: 'today', label: 'Сегодня', icon: todayTabActive, route: 'today' },
    {
      id: 'homework',
      label: 'Задания',
      icon: scheduleTab,
      route: 'homework',
      ...(homeworkEnabled ? {} : { disabled: true, disabledReason: 'Раздел пока недоступен' }),
    },
    {
      id: 'attendance',
      label: 'Учёт',
      accessibleLabel: 'Посещаемость',
      icon: attendanceTab,
      route: 'attendance',
      ...(attendanceEnabled ? {} : { disabled: true, disabledReason: 'Раздел пока недоступен' }),
    },
    {
      id: 'more',
      label: 'Ещё',
      icon: moreTab,
      route: 'more',
      ...(moreEnabled ? {} : { disabled: true, disabledReason: 'Раздел пока недоступен' }),
    },
    {
      id: 'profile',
      label: 'Профиль',
      icon: profileTab,
      route: 'profile',
      ...(profileEnabled ? {} : { disabled: true, disabledReason: 'Раздел пока недоступен' }),
    },
  ] as const satisfies MobileBottomNavItems
}

/** The teacher dock exposes the five canonical destinations owned by Vue. */
export function createTeacherNavigationItems(profileEnabled = true): MobileBottomNavItems {
  return [
    { id: 'teacher-home', label: 'Сегодня', icon: teacherTodayTab, route: 'teacher-home' },
    { id: 'teacher-attendance', label: 'Учёт', accessibleLabel: 'Посещаемость', icon: teacherAttendanceTab, route: 'teacher-attendance' },
    { id: 'teacher-stats', label: 'Итоги', icon: teacherStatsTab, route: 'teacher-stats' },
    { id: 'teacher-map', label: 'Карта', icon: teacherMapTab, route: 'teacher-map' },
    {
      id: 'profile',
      label: 'Профиль',
      icon: teacherProfileTab,
      route: 'profile',
      ...(profileEnabled ? {} : { disabled: true, disabledReason: 'Раздел пока недоступен' }),
    },
  ] as const satisfies MobileBottomNavItems
}
