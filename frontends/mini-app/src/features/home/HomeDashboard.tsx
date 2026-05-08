import { useQueryClient } from '@tanstack/react-query'
import { ArrowsClockwise } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { SkeletonList } from '@/shared/components/Skeleton'
import { useGroupInfo, useMe, useStudentDashboard } from './api'
import { ProfileHeader } from './ProfileHeader'
import { StatCards } from './StatCards'
import { StatusDonut } from './StatusDonut'
import { WeeklyChart } from './WeeklyChart'
import { TopMissedList } from './TopMissedList'

export function HomeDashboard() {
  const { user } = useAuth()
  const meQuery = useMe()
  const groupQuery = useGroupInfo(user?.groupId)
  const dashboardQuery = useStudentDashboard()
  const queryClient = useQueryClient()

  const handleRefresh = async () => {
    await Promise.all([
      meQuery.refetch(),
      groupQuery.refetch(),
      queryClient.invalidateQueries({ queryKey: ['student-dashboard'] }),
    ])
  }

  return (
    <section className="mx-auto flex w-full max-w-md flex-col gap-4 px-3 pb-[calc(72px+env(safe-area-inset-bottom))] pt-3">
      <ProfileHeader me={meQuery.data} group={groupQuery.data} isLoading={meQuery.isLoading} />

      {dashboardQuery.isError ? (
        <div
          className="rounded-2xl border p-4 text-sm"
          style={{
            background: 'color-mix(in oklab, var(--accent-danger) 10%, var(--bg-secondary))',
            borderColor: 'var(--accent-danger)',
            color: 'var(--text-primary)',
          }}
        >
          <p className="font-semibold">Не удалось загрузить статистику</p>
          <button
            type="button"
            onClick={() => void handleRefresh()}
            className="mt-3 inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold"
            style={{
              borderColor: 'var(--border-accent)',
              color: 'var(--accent-primary)',
              background: 'color-mix(in oklab, var(--accent-primary) 10%, transparent)',
            }}
          >
            <ArrowsClockwise size={12} weight="bold" />
            Обновить
          </button>
        </div>
      ) : dashboardQuery.isLoading || !dashboardQuery.data ? (
        <SkeletonList count={5} />
      ) : (
        <>
          <StatCards overall={dashboardQuery.data.overall} />
          <StatusDonut breakdown={dashboardQuery.data.breakdown} />
          <WeeklyChart weekly={dashboardQuery.data.weekly} />
          <TopMissedList items={dashboardQuery.data.topMissed} />
        </>
      )}
    </section>
  )
}
