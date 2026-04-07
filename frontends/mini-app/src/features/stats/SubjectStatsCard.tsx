import { RED_ZONE_THRESHOLD } from './api'
import type { SubjectStats } from './types'

interface SubjectStatsCardProps {
  stats: SubjectStats
}

export function SubjectStatsCard({ stats }: SubjectStatsCardProps) {
  const isRedZone = stats.percentage < RED_ZONE_THRESHOLD

  return (
    <div className={`bg-card border border-border rounded-xl p-4 ${isRedZone ? 'border-l-4 border-l-destructive' : ''}`}>
      <div className="flex items-center justify-between">
        <span className="text-sm font-semibold line-clamp-1">{stats.subjectName}</span>
        <span className={`text-sm font-semibold ${isRedZone ? 'text-destructive' : 'text-foreground'}`}>
          {stats.percentage}%
        </span>
      </div>
      <div className="mt-2 h-2 rounded-full bg-muted overflow-hidden">
        <div
          className={`h-full rounded-full transition-all duration-300 ${isRedZone ? 'bg-destructive' : 'bg-primary'}`}
          style={{ width: `${Math.min(stats.percentage, 100)}%` }}
        />
      </div>
      <div className="flex gap-4 mt-2 text-xs text-muted-foreground">
        <span>Н: {stats.absent}</span>
        <span>У: {stats.excused}</span>
      </div>
    </div>
  )
}
