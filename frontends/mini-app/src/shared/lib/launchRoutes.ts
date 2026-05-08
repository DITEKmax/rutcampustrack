const DIRECT_ROUTES: Record<string, string> = {
  home: '/home',
  schedule: '/schedule',
  stats: '/stats',
  homework: '/homework',
  late_checkin: '/late-checkin',
  'late-checkin': '/late-checkin',
  excuses: '/excuses',
  profile: '/profile',
  group: '/group',
  group_overview: '/group/overview',
  'group-overview': '/group/overview',
  group_students: '/group/students',
  'group-students': '/group/students',
  group_subjects: '/group/subjects',
  'group-subjects': '/group/subjects',
  group_journal: '/group/journal',
  'group-journal': '/group/journal',
  group_stats: '/group/stats',
  'group-stats': '/group/stats',
  group_excuses: '/group/excuses',
  'group-excuses': '/group/excuses',
  group_late_checkin: '/group/late-checkin',
  'group-late-checkin': '/group/late-checkin',
}

export function resolveStartParamRoute(startParam: string | null | undefined): string | null {
  const value = startParam?.trim()
  if (!value) return null

  const lessonMatch = /^(checkin|lesson)[:_-](\d+)$/i.exec(value)
  if (lessonMatch) {
    return `/checkin/${lessonMatch[2]}`
  }

  const normalized = value.replace(/^\/+/, '').toLowerCase()
  return DIRECT_ROUTES[normalized] ?? null
}

export function getStartParamFromLaunchParams(params: unknown): string | null {
  if (!params || typeof params !== 'object') return null
  const launchParams = params as {
    tgWebAppStartParam?: unknown
    tgWebAppData?: { startParam?: unknown } | null
  }
  if (typeof launchParams.tgWebAppStartParam === 'string') {
    return launchParams.tgWebAppStartParam
  }
  if (typeof launchParams.tgWebAppData?.startParam === 'string') {
    return launchParams.tgWebAppData.startParam
  }
  return null
}

export function getStartParamFromSearch(search: string): string | null {
  const params = new URLSearchParams(search)
  return (
    params.get('tgWebAppStartParam') ??
    params.get('start_param') ??
    params.get('startapp')
  )
}
