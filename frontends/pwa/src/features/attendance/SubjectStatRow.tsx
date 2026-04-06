import { CaretRight } from '@phosphor-icons/react'
import { Card } from '@/components/ui/card'
import type { SubjectStats } from './types'
import { RedZoneBadge } from './RedZoneBadge'

interface SubjectStatRowProps {
  stats: SubjectStats
  threshold: number | null
  onClick: () => void
}

export function SubjectStatRow({ stats, threshold, onClick }: SubjectStatRowProps) {
  const isRedZone = threshold !== null && stats.percentage < threshold

  return (
    <button
      onClick={onClick}
      className="w-full text-left"
      aria-label={`Открыть записи посещаемости для ${stats.subjectName}`}
    >
      <Card
        className={`px-4 py-3 min-h-[56px] flex flex-col justify-center gap-1 ${
          isRedZone ? 'border-l-4 border-destructive' : ''
        }`}
      >
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 flex-1 min-w-0">
            <span className="text-base font-semibold truncate">{stats.subjectName}</span>
            {isRedZone && <RedZoneBadge />}
          </div>
          <div className="flex items-center gap-1 shrink-0">
            <span
              className={`text-base font-semibold ${
                isRedZone ? 'text-destructive' : 'text-foreground'
              }`}
            >
              {Math.round(stats.percentage)}%
            </span>
            <CaretRight size={20} weight="bold" className="text-muted-foreground" />
          </div>
        </div>
        <div className="text-sm text-muted-foreground">
          б: {stats.attended} / н: {stats.absent} / у: {stats.excused}
        </div>
      </Card>
    </button>
  )
}
