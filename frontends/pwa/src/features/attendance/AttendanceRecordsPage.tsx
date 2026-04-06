import { motion } from 'motion/react'
import { useParams, useNavigate, useLocation } from 'react-router'
import { CaretLeft } from '@phosphor-icons/react'
import { LoadingSpinner } from '@/shared/components/LoadingSpinner'
import { Button } from '@/components/ui/button'
import { useAttendanceRecords } from './api'
import { AttendanceRecordRow } from './AttendanceRecordRow'

export function AttendanceRecordsPage() {
  const { subjectId: subjectIdStr } = useParams<{ subjectId: string }>()
  const navigate = useNavigate()
  const location = useLocation()

  const subjectId = subjectIdStr ? Number(subjectIdStr) : undefined
  const subjectName = (location.state as { subjectName?: string } | null)?.subjectName ?? 'Предмет'

  const { data: records, isLoading, isError, refetch } = useAttendanceRecords(subjectId)

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-16 px-4 gap-4">
        <p className="text-sm text-muted-foreground text-center">
          Не удалось загрузить данные. Проверьте соединение и попробуйте снова.
        </p>
        <Button onClick={() => refetch()}>Попробовать снова</Button>
      </div>
    )
  }

  const sortedRecords = [...(records ?? [])].sort(
    (a, b) => b.lessonDate.localeCompare(a.lessonDate)
  )

  return (
    <div className="flex flex-col min-h-full">
      {/* Header */}
      <div className="flex items-center gap-2 px-2 pt-4 pb-2">
        <button
          onClick={() => navigate('/stats')}
          aria-label="Назад к статистике"
          className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-lg text-muted-foreground"
        >
          <CaretLeft size={24} />
        </button>
        <h1 className="text-xl font-semibold truncate">{subjectName}</h1>
      </div>

      {sortedRecords.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 px-4">
          <h2 className="text-xl font-semibold text-center">Нет записей</h2>
          <p className="text-sm text-muted-foreground text-center mt-1">
            Посещаемость по этому предмету не зафиксирована
          </p>
        </div>
      ) : (
        <motion.div
          className="flex flex-col pb-20"
          initial="hidden"
          animate="visible"
          variants={{
            visible: { transition: { staggerChildren: 0.04 } },
            hidden: {},
          }}
        >
          {sortedRecords.map((record) => (
            <motion.div
              key={record.lessonId}
              variants={{
                hidden: { opacity: 0 },
                visible: { opacity: 1 },
              }}
            >
              <AttendanceRecordRow record={record} />
            </motion.div>
          ))}
        </motion.div>
      )}
    </div>
  )
}
