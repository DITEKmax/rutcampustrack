import { Books, WarningCircle } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { useActiveSemester, useHomeworkList, useToggleHomework } from './api'
import { HomeworkItem } from './HomeworkItem'
import type { HomeworkResponse } from './types'

function groupBySubject(items: HomeworkResponse[]): Map<string, HomeworkResponse[]> {
  const map = new Map<string, HomeworkResponse[]>()
  for (const hw of items) {
    const group = map.get(hw.subjectName) ?? []
    group.push(hw)
    map.set(hw.subjectName, group)
  }
  return map
}

export function HomeworkPage() {
  const { user } = useAuth()
  const { data: semester, isLoading: semLoading } = useActiveSemester()
  const { data: homeworks, isLoading, isError, refetch } = useHomeworkList(user?.groupId, semester?.id)
  const toggleMutation = useToggleHomework(user?.groupId, semester?.id)

  const loading = semLoading || isLoading

  return (
    <div className="px-4 pt-4 pb-[calc(56px+env(safe-area-inset-bottom)+16px)]">
      <h1 className="text-base font-semibold">Домашние задания</h1>

      {loading && (
        <div className="flex flex-col gap-3 mt-4">
          <div className="bg-muted rounded-xl h-14 animate-pulse" />
          <div className="bg-muted rounded-xl h-14 animate-pulse" />
          <div className="bg-muted rounded-xl h-14 animate-pulse" />
          <div className="bg-muted rounded-xl h-14 animate-pulse" />
          <div className="bg-muted rounded-xl h-14 animate-pulse" />
        </div>
      )}

      {!loading && !semester && (
        <div className="flex flex-col items-center justify-center min-h-[40vh] text-center">
          <WarningCircle size={32} weight="bold" className="text-muted-foreground" />
          <p className="text-base font-semibold mt-3">Активный семестр не найден</p>
          <p className="text-sm text-muted-foreground mt-1">Обратитесь к старосте.</p>
        </div>
      )}

      {isError && (
        <div className="flex flex-col items-center justify-center min-h-[40vh] text-center">
          <WarningCircle size={32} weight="bold" className="text-muted-foreground" />
          <p className="text-base font-semibold mt-3">Не удалось загрузить задания</p>
          <button onClick={() => refetch()} className="text-sm font-semibold text-primary mt-2">
            Обновить
          </button>
        </div>
      )}

      {!loading && !isError && semester && homeworks?.length === 0 && (
        <div className="flex flex-col items-center justify-center min-h-[40vh] text-center">
          <Books size={32} weight="bold" className="text-muted-foreground" />
          <p className="text-base font-semibold mt-3">Нет домашних заданий</p>
          <p className="text-sm text-muted-foreground mt-1">Всё сдано или пока ничего не задано</p>
        </div>
      )}

      {!loading && !isError && semester && homeworks && homeworks.length > 0 && (
        <div className="flex flex-col gap-4 mt-4">
          {Array.from(groupBySubject(homeworks)).map(([subjectName, items], idx) => (
            <div key={subjectName}>
              {idx > 0 && <div className="h-px bg-border mb-4" />}
              <p className="text-sm font-semibold text-muted-foreground mb-2">{subjectName}</p>
              <div className="flex flex-col gap-2">
                {items.map((hw) => (
                  <HomeworkItem
                    key={hw.id}
                    homework={hw}
                    onToggle={(id, completed) => toggleMutation.mutate({ id, completed })}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
