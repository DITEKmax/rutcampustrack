import type { LessonStatus, AttendanceStatus } from './types'

interface StatusBadgeProps {
  status: LessonStatus | AttendanceStatus
}

const statusConfig: Record<LessonStatus | AttendanceStatus, { label: string; classes: string }> = {
  present: { label: 'б', classes: 'bg-green-100 text-green-700' },
  absent: { label: 'н', classes: 'bg-red-100 text-red-700' },
  excused: { label: 'у', classes: 'bg-yellow-100 text-yellow-700' },
  free_attendance: { label: 'сп', classes: 'bg-blue-100 text-blue-700' },
  ACTIVE: { label: 'Идёт', classes: 'bg-primary text-primary-foreground' },
  PLANNED: { label: 'Запланировано', classes: 'bg-muted text-muted-foreground' },
  CANCELLED: { label: 'Отменена', classes: 'bg-muted text-muted-foreground line-through' },
  CLOSED: { label: 'Завершено', classes: 'bg-muted text-muted-foreground' },
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const config = statusConfig[status]
  if (!config) return null

  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${config.classes}`}>
      {config.label}
    </span>
  )
}
