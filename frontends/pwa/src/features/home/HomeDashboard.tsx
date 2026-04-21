import { useAuth } from '@/features/auth/AuthProvider'
import { useGroupInfo, useMe, useStudentDashboard } from './api'
import { ProfileHeader } from './ProfileHeader'
import { StatCards } from './StatCards'
import { StatusDonut } from './StatusDonut'
import { WeeklyChart } from './WeeklyChart'
import { TopMissedList } from './TopMissedList'
import { PullToRefresh } from '@/shared/components/PullToRefresh'

function SkeletonBlock({ height }: { height: number }) {
  return (
    <div
      className="animate-pulse rounded-[var(--radius-lg)]"
      style={{
        height,
        background: 'var(--bg-surface)',
        border: '1px solid var(--border-subtle)',
      }}
    />
  )
}

/**
 * Home tab — personal analytics dashboard for a student.
 * Layout vertical stack on mobile: profile header → 3 stat cards →
 * status donut → weekly line chart → top missed subjects.
 * Data loaded in one call via /attendance/reports/student/dashboard.
 */
export default function HomeDashboard() {
  const { user } = useAuth()
  const meQuery = useMe()
  const groupQuery = useGroupInfo(user?.groupId)
  const dashQuery = useStudentDashboard()

  const loading = dashQuery.isLoading
  const error = dashQuery.error

  const handleRefresh = async () => {
    await Promise.all([meQuery.refetch(), groupQuery.refetch(), dashQuery.refetch()])
  }

  return (
    <PullToRefresh onRefresh={handleRefresh}>
    <section
      aria-labelledby="home-dashboard-title"
      className="mx-auto flex w-full max-w-md flex-col gap-[var(--space-4)] px-[var(--space-4)] pb-[var(--space-6)] pt-[var(--space-5)]"
    >
      <h1 id="home-dashboard-title" className="sr-only">
        Моя посещаемость
      </h1>
      <ProfileHeader
        me={meQuery.data}
        group={groupQuery.data}
        isLoading={meQuery.isLoading}
      />

      {error ? (
        <div
          className="rounded-[var(--radius-md)] border p-[var(--space-4)] text-[var(--text-sm)]"
          style={{
            background: 'color-mix(in oklab, var(--accent-danger) 10%, var(--bg-secondary))',
            borderColor: 'var(--accent-danger)',
            color: 'var(--text-primary)',
          }}
        >
          Не удалось загрузить статистику. Попробуй потянуть экран вниз, чтобы обновить.
        </div>
      ) : loading || !dashQuery.data ? (
        <>
          <div className="grid grid-cols-3 gap-[var(--space-3)]">
            <SkeletonBlock height={88} />
            <SkeletonBlock height={88} />
            <SkeletonBlock height={88} />
          </div>
          <SkeletonBlock height={300} />
          <SkeletonBlock height={220} />
          <SkeletonBlock height={280} />
        </>
      ) : (
        <>
          <StatCards overall={dashQuery.data.overall} />
          <StatusDonut breakdown={dashQuery.data.breakdown} />
          <WeeklyChart weekly={dashQuery.data.weekly} />
          <TopMissedList items={dashQuery.data.topMissed} />
        </>
      )}
    </section>
    </PullToRefresh>
  )
}
