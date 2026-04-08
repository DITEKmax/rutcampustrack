import { Books, WarningCircle, ArrowsClockwise } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { SkeletonList } from '@/shared/components/Skeleton'
import { useActiveSemester, useHomeworkList, useToggleHomework } from './api'
import { HomeworkItem } from './HomeworkItem'
import type { HomeworkResponse } from './types'

/**
 * Homework tracker — grouped by subject.
 *
 * Title comes from AppHeader. This page lays out loading/error/empty states
 * plus the subject-grouped list. Toggling items is optimistic with Telegram
 * haptic feedback on each tap (wired inside HomeworkItem).
 */
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
  const { data: homeworks, isLoading, isError, refetch } = useHomeworkList(
    user?.groupId,
    semester?.id,
  )
  const toggleMutation = useToggleHomework(user?.groupId, semester?.id)

  const loading = semLoading || isLoading

  return (
    <div className="px-3 pt-3 pb-[calc(72px+env(safe-area-inset-bottom))]">
      {loading && <SkeletonList count={5} />}

      {!loading && !semester && (
        <MessageCard
          tone="warning"
          title="Активный семестр не найден"
          text="Обратитесь к старосте."
        />
      )}

      {isError && (
        <div className="flex min-h-[40vh] flex-col items-center justify-center gap-3 text-center">
          <div
            className="grid size-12 place-items-center rounded-full"
            style={{
              background: 'color-mix(in oklab, var(--accent-danger) 14%, transparent)',
              border: '1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent)',
              color: 'var(--accent-danger)',
            }}
          >
            <WarningCircle size={22} weight="fill" />
          </div>
          <p
            className="text-sm font-semibold text-balance"
            style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
          >
            Не удалось загрузить задания
          </p>
          <button
            type="button"
            onClick={() => refetch()}
            className="inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold"
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
      )}

      {!loading && !isError && semester && homeworks?.length === 0 && (
        <div
          className="mt-4 flex flex-col items-center gap-3 rounded-2xl border border-dashed p-6 text-center"
          style={{
            borderColor: 'var(--border-default)',
            background: 'color-mix(in oklab, var(--bg-secondary) 50%, transparent)',
          }}
        >
          <div
            className="grid size-12 place-items-center rounded-full"
            style={{
              background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
              border: '1px solid var(--border-accent)',
              color: 'var(--accent-primary)',
            }}
          >
            <Books size={22} weight="duotone" />
          </div>
          <p
            className="text-sm font-semibold text-balance"
            style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
          >
            Нет домашних заданий
          </p>
          <p
            className="max-w-[28ch] text-xs text-pretty"
            style={{ color: 'var(--text-secondary)' }}
          >
            Всё сдано или пока ничего не задано.
          </p>
        </div>
      )}

      {!loading && !isError && semester && homeworks && homeworks.length > 0 && (
        <div className="flex flex-col gap-5">
          {Array.from(groupBySubject(homeworks)).map(([subjectName, items]) => (
            <section key={subjectName} className="flex flex-col gap-2">
              <p
                className="text-[11px] font-medium uppercase tracking-wide"
                style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}
              >
                {subjectName}
              </p>
              <div className="flex flex-col gap-2">
                {items.map((hw) => (
                  <HomeworkItem
                    key={hw.id}
                    homework={hw}
                    onToggle={(id, completed) =>
                      toggleMutation.mutate({ id, completed })
                    }
                  />
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </div>
  )
}

function MessageCard({
  tone,
  title,
  text,
}: {
  tone: 'warning' | 'info'
  title: string
  text: string
}) {
  const fg = tone === 'warning' ? 'var(--accent-warning)' : 'var(--accent-info)'
  return (
    <div className="flex min-h-[40vh] flex-col items-center justify-center gap-3 text-center">
      <div
        className="grid size-12 place-items-center rounded-full"
        style={{
          background: `color-mix(in oklab, ${fg} 14%, transparent)`,
          border: `1px solid color-mix(in oklab, ${fg} 30%, transparent)`,
          color: fg,
        }}
      >
        <WarningCircle size={22} weight="fill" />
      </div>
      <p
        className="text-sm font-semibold text-balance"
        style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
      >
        {title}
      </p>
      <p
        className="max-w-[28ch] text-xs text-pretty"
        style={{ color: 'var(--text-secondary)' }}
      >
        {text}
      </p>
    </div>
  )
}
