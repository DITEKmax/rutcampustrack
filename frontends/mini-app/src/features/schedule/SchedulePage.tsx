import { useNavigate } from 'react-router'
import { CalendarBlank, Info, WarningCircle } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { useTodaySchedule, useSubjectName } from './api'
import { LessonCard } from './LessonCard'
import type { LessonResponse } from './types'

function SubjectNameCard({ lesson, onCheckin }: { lesson: LessonResponse; onCheckin: (id: number) => void }) {
  const { data: subject } = useSubjectName(lesson.subjectId)
  return <LessonCard lesson={lesson} subjectName={subject?.name ?? '...'} onCheckin={onCheckin} />
}

export function SchedulePage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  const isStudent = user?.role === 'STUDENT' && user.groupId
  const { data: lessons, isLoading, isError, refetch } = useTodaySchedule(isStudent ? user.groupId : undefined)

  const today = new Date().toLocaleDateString('ru-RU', { weekday: 'long', day: 'numeric', month: 'long' })

  if (!isStudent) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] px-4 text-center">
        <Info size={32} weight="bold" className="text-muted-foreground" />
        <p className="text-base font-semibold mt-3">Расписание студентов</p>
        <p className="text-sm text-muted-foreground mt-1">Расписание доступно только для студентов</p>
      </div>
    )
  }

  return (
    <div className="px-4 pt-4 pb-[calc(56px+env(safe-area-inset-bottom)+16px)]">
      <h1 className="text-base font-semibold">Расписание</h1>
      <p className="text-xs text-muted-foreground capitalize">{today}</p>

      <div className="flex flex-col gap-3 mt-4">
        {isLoading && (
          <>
            <div className="bg-muted rounded-xl h-24 animate-pulse" />
            <div className="bg-muted rounded-xl h-24 animate-pulse" />
            <div className="bg-muted rounded-xl h-24 animate-pulse" />
          </>
        )}

        {isError && (
          <div className="flex flex-col items-center justify-center min-h-[40vh] text-center">
            <WarningCircle size={32} weight="bold" className="text-muted-foreground" />
            <p className="text-base font-semibold mt-3">Не удалось загрузить расписание</p>
            <button onClick={() => refetch()} className="text-sm font-semibold text-primary mt-2">
              Обновить расписание
            </button>
          </div>
        )}

        {!isLoading && !isError && lessons?.length === 0 && (
          <div className="flex flex-col items-center justify-center min-h-[40vh] text-center">
            <CalendarBlank size={32} weight="bold" className="text-muted-foreground" />
            <p className="text-base font-semibold mt-3">Сегодня пар нет</p>
            <p className="text-sm text-muted-foreground mt-1">Наслаждайтесь свободным днём</p>
          </div>
        )}

        {!isLoading && !isError && lessons?.map((lesson) => (
          <SubjectNameCard
            key={lesson.id}
            lesson={lesson}
            onCheckin={(id) => navigate(`/checkin/${id}`)}
          />
        ))}
      </div>
    </div>
  )
}
