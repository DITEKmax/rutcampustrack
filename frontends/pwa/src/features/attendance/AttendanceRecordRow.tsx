import { StatusBadge } from '@/features/schedule/StatusBadge'
import type { AttendanceStatus } from '@/features/schedule/types'
import type { AttendanceRecordEntry } from './types'

const MONTH_ABBREV = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек']

function formatLessonDate(lessonDate: string): string {
  // lessonDate is "YYYY-MM-DD"
  const parts = lessonDate.split('-')
  const day = parseInt(parts[2], 10)
  const monthIndex = parseInt(parts[1], 10) - 1
  return `${day} ${MONTH_ABBREV[monthIndex]}`
}

interface AttendanceRecordRowProps {
  record: AttendanceRecordEntry
}

export function AttendanceRecordRow({ record }: AttendanceRecordRowProps) {
  return (
    <div className="flex items-center gap-3 min-h-[44px] px-4 py-2">
      <span className="text-sm font-medium text-foreground w-14 shrink-0">
        {formatLessonDate(record.lessonDate)}
      </span>
      <span className="text-sm text-muted-foreground line-clamp-1 flex-1">
        {record.lessonNumber}-я пара
      </span>
      <StatusBadge status={record.status as AttendanceStatus} />
    </div>
  )
}
