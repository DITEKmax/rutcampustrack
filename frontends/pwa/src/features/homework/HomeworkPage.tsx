import { useState } from 'react'
import { motion } from 'motion/react'
import { ArrowCounterClockwise } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { LoadingSpinner } from '@/shared/components/LoadingSpinner'
import { OfflineBanner } from '@/shared/components/OfflineBanner'
import { usePullToRefresh } from '@/shared/hooks/usePullToRefresh'
import { Button } from '@/components/ui/button'
import { useActiveSemester, useHomework, useToggleHomework } from './api'
import { HomeworkItem } from './HomeworkItem'

export function HomeworkPage() {
  const { user } = useAuth()
  const [errorMap, setErrorMap] = useState<Record<number, string>>({})

  const { data: semesterId, isLoading: semesterLoading, isError: semesterError } = useActiveSemester()
  const {
    data: homeworks,
    isLoading: homeworkLoading,
    isError: homeworkError,
    refetch,
  } = useHomework(user?.groupId, semesterId)
  const toggleMutation = useToggleHomework()

  const { containerRef, isRefreshing, pullDistance } = usePullToRefresh({
    onRefresh: async () => {
      await refetch()
    },
  })

  const isLoading = semesterLoading || homeworkLoading
  const isError = semesterError || homeworkError

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-16 px-4 gap-4">
        <p className="text-sm text-muted-foreground text-center">
          Не удалось загрузить задания. Проверьте соединение и попробуйте снова.
        </p>
        <Button onClick={() => refetch()}>Попробовать снова</Button>
      </div>
    )
  }

  const homeworkList = homeworks ?? []

  const sortedHomework = [...homeworkList].sort((a, b) => {
    if (a.completed !== b.completed) return a.completed ? 1 : -1
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  })

  function handleToggle(id: number, completed: boolean) {
    setErrorMap((prev) => {
      const next = { ...prev }
      delete next[id]
      return next
    })
    toggleMutation.mutate(
      { id, completed, groupId: user?.groupId, semesterId },
      {
        onError: () =>
          setErrorMap((prev) => ({ ...prev, [id]: 'Не удалось сохранить' })),
      }
    )
  }

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

      <h1 className="text-xl font-semibold pt-6 px-4">Задания</h1>

      <OfflineBanner />

      {sortedHomework.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 px-4">
          <h2 className="text-xl font-semibold text-center">Нет заданий</h2>
          <p className="text-sm text-muted-foreground text-center mt-1">
            Домашних заданий пока нет
          </p>
        </div>
      ) : (
        <motion.div
          className="flex flex-col gap-4 px-4 pt-4 pb-20"
          initial="hidden"
          animate="visible"
          variants={{
            visible: { transition: { staggerChildren: 0.04 } },
            hidden: {},
          }}
        >
          {sortedHomework.map((hw) => (
            <motion.div
              key={hw.id}
              layout
              variants={{
                hidden: { opacity: 0 },
                visible: { opacity: 1 },
              }}
              transition={{ type: 'spring', stiffness: 300, damping: 30 }}
            >
              <HomeworkItem
                homework={hw}
                onToggle={handleToggle}
                error={errorMap[hw.id] ?? null}
              />
            </motion.div>
          ))}
        </motion.div>
      )}
    </div>
  )
}
