import { motion } from 'motion/react'
import { useNavigate } from 'react-router'
import { ArrowCounterClockwise } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { LoadingSpinner } from '@/shared/components/LoadingSpinner'
import { OfflineBanner } from '@/shared/components/OfflineBanner'
import { usePullToRefresh } from '@/shared/hooks/usePullToRefresh'
import { Button } from '@/components/ui/button'
import { useStudentStats, useThreshold } from './api'
import { SubjectStatRow } from './SubjectStatRow'

export function AttendanceStatsPage() {
  const { user } = useAuth()
  const navigate = useNavigate()

  const { data, isLoading, isError, refetch } = useStudentStats()
  const { data: threshold } = useThreshold(user?.groupId)

  const { containerRef, isRefreshing, pullDistance } = usePullToRefresh({
    onRefresh: async () => {
      await refetch()
    },
  })

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

  const subjects = data?.subjects ?? []

  return (
    <div ref={containerRef} className="flex flex-col min-h-full overflow-y-auto">
      {/* Pull-to-refresh indicator */}
      {(pullDistance > 0 || isRefreshing) && (
        <div
          className="flex items-center justify-center py-2 text-muted-foreground transition-transform duration-200 ease-out"
          style={{ transform: `translateY(${pullDistance}px)` }}
        >
          <ArrowCounterClockwise
            size={20}
            weight="bold"
            className={isRefreshing ? 'animate-spin' : ''}
          />
        </div>
      )}

      <h1 className="text-xl font-semibold pt-6 px-4">Посещаемость</h1>

      <OfflineBanner />

      {subjects.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 px-4">
          <h2 className="text-xl font-semibold text-center">Нет данных</h2>
          <p className="text-sm text-muted-foreground text-center mt-1">
            Посещаемость ещё не была зафиксирована
          </p>
        </div>
      ) : (
        <motion.div
          className="flex flex-col gap-3 px-4 pt-4 pb-20"
          initial="hidden"
          animate="visible"
          variants={{
            visible: { transition: { staggerChildren: 0.04 } },
            hidden: {},
          }}
        >
          {subjects.map((stats) => (
            <motion.div
              key={stats.subjectId}
              variants={{
                hidden: { opacity: 0 },
                visible: { opacity: 1 },
              }}
            >
              <SubjectStatRow
                stats={stats}
                threshold={threshold ?? null}
                onClick={() =>
                  navigate(`/stats/${stats.subjectId}`, {
                    state: { subjectName: stats.subjectName },
                  })
                }
              />
            </motion.div>
          ))}
        </motion.div>
      )}
    </div>
  )
}
