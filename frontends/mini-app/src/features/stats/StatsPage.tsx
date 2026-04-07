import { ChartBar, WarningCircle } from '@phosphor-icons/react'
import { useStudentStats } from './api'
import { SubjectStatsCard } from './SubjectStatsCard'

export function StatsPage() {
  const { data, isLoading, isError, refetch } = useStudentStats()

  return (
    <div className="px-4 pt-4 pb-[calc(56px+env(safe-area-inset-bottom)+16px)]">
      <h1 className="text-base font-semibold">Статистика</h1>

      {isLoading && (
        <div className="flex flex-col gap-3 mt-4">
          <div className="bg-muted rounded-xl h-20 animate-pulse" />
          <div className="bg-muted rounded-xl h-20 animate-pulse" />
          <div className="bg-muted rounded-xl h-20 animate-pulse" />
          <div className="bg-muted rounded-xl h-20 animate-pulse" />
        </div>
      )}

      {isError && (
        <div className="flex flex-col items-center justify-center min-h-[40vh] text-center">
          <WarningCircle size={32} weight="bold" className="text-muted-foreground" />
          <p className="text-base font-semibold mt-3">Не удалось загрузить статистику</p>
          <p className="text-sm text-muted-foreground mt-1">Проверьте соединение и попробуйте снова</p>
          <button onClick={() => refetch()} className="text-sm font-semibold text-primary mt-2">
            Обновить
          </button>
        </div>
      )}

      {!isLoading && !isError && (!data || data.subjects.length === 0) && (
        <div className="flex flex-col items-center justify-center min-h-[40vh] text-center">
          <ChartBar size={32} weight="bold" className="text-muted-foreground" />
          <p className="text-base font-semibold mt-3">Нет данных о посещаемости</p>
          <p className="text-sm text-muted-foreground mt-1">Статистика появится после первых пар</p>
        </div>
      )}

      {!isLoading && !isError && data && data.subjects.length > 0 && (
        <div className="flex flex-col gap-3 mt-4">
          {/* Overall stats card */}
          <div className="bg-card border border-border rounded-xl p-4">
            <p className="text-sm font-semibold text-muted-foreground">Общая посещаемость</p>
            <p className="text-[1.75rem] font-semibold mt-1">{data.overall.percentage}%</p>
            <div className="flex justify-between mt-2 text-xs text-muted-foreground">
              <span>Присутствовал: {data.overall.attended}</span>
              <span>Отсутствовал: {data.overall.absent}</span>
              <span>Уважит.: {data.overall.excused}</span>
            </div>
          </div>

          {/* Subject cards */}
          {data.subjects.map((subj) => (
            <SubjectStatsCard key={subj.subjectId} stats={subj} />
          ))}
        </div>
      )}
    </div>
  )
}
