import { useState } from 'react'
import { ArrowsClockwise, CheckCircle, Shield, Student, UserCircle } from '@phosphor-icons/react'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/AuthProvider'
import { useGroupInfo, useMe } from '@/features/home/api'
import { SkeletonList } from '@/shared/components/Skeleton'

function roleLabel(role?: string): string {
  if (role === 'ADMIN') return 'Администратор'
  if (role === 'TEACHER') return 'Преподаватель'
  if (role === 'STUDENT') return 'Студент'
  return role ?? 'Пользователь'
}

function initials(firstName?: string | null, lastName?: string | null, id?: number): string {
  const first = firstName?.[0] ?? ''
  const last = lastName?.[0] ?? ''
  const value = `${last}${first}`.trim()
  if (value) return value.toUpperCase()
  return typeof id === 'number' ? String(id).slice(-2).padStart(2, '0') : 'RT'
}

export function ProfilePage() {
  const { user, logout } = useAuth()
  const meQuery = useMe()
  const groupId = meQuery.data?.groupId ?? user?.groupId
  const groupQuery = useGroupInfo(groupId)
  const [isRestarting, setIsRestarting] = useState(false)

  const me = meQuery.data
  const isHeadman = me?.headman ?? user?.isHeadman ?? false
  const displayName = me?.fullName ?? (me?.id ? `Студент #${me.id}` : 'Аккаунт RutTrack')

  const handleRestart = async () => {
    setIsRestarting(true)
    try {
      await logout()
    } finally {
      setIsRestarting(false)
    }
  }

  if (meQuery.isLoading) {
    return (
      <div className="px-3 pt-3 pb-[calc(72px+env(safe-area-inset-bottom))]">
        <SkeletonList count={4} />
      </div>
    )
  }

  return (
    <div className="mx-auto flex w-full max-w-md flex-col gap-4 px-3 pt-3 pb-[calc(72px+env(safe-area-inset-bottom))]">
      <section
        className="relative overflow-hidden rounded-2xl border p-4"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
        }}
      >
        <span
          aria-hidden="true"
          className="pointer-events-none absolute -right-16 -top-16 size-44 rounded-full opacity-35"
          style={{ background: 'var(--gradient-brand)' }}
        />

        <div className="relative flex items-center gap-3">
          <span
            aria-hidden="true"
            className="grid size-16 shrink-0 place-items-center rounded-2xl text-xl font-bold tracking-tight"
            style={{
              background: 'var(--gradient-brand)',
              color: 'var(--accent-primary-contrast)',
              boxShadow: 'var(--glow-primary)',
              fontFamily: 'var(--font-display)',
            }}
          >
            {initials(me?.firstName, me?.lastName, me?.id ?? user?.id)}
          </span>

          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <p
              className="truncate text-lg font-semibold leading-tight"
              style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
            >
              {displayName}
            </p>
            <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
              {groupQuery.data?.name ? `Группа ${groupQuery.data.name}` : `ID ${me?.id ?? user?.id}`}
            </p>
          </div>
        </div>

        <dl
          className="relative mt-4 grid grid-cols-2 gap-3 border-t pt-4"
          style={{ borderColor: 'var(--border-subtle)' }}
        >
          <ProfileStat label="Роль" value={roleLabel(me?.role ?? user?.role)} />
          <ProfileStat label="Группа" value={groupQuery.data?.name ?? (groupId ? `#${groupId}` : '—')} />
        </dl>
      </section>

      <section
        className="rounded-2xl border p-4"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
        }}
      >
        <div className="flex items-start gap-3">
          <div
            className="grid size-10 shrink-0 place-items-center rounded-xl"
            style={{
              background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
              border: '1px solid var(--border-accent)',
              color: 'var(--accent-primary)',
            }}
          >
            {isHeadman ? <Shield size={20} weight="duotone" /> : <Student size={20} weight="duotone" />}
          </div>
          <div className="min-w-0 flex-1">
            <p
              className="text-sm font-semibold"
              style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
            >
              Telegram Mini App
            </p>
            <p className="mt-1 text-xs leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
              Вход привязан к текущему аккаунту Telegram. Логин и пароль здесь не используются.
            </p>
          </div>
        </div>
      </section>

      {meQuery.isError && (
        <section
          className="rounded-2xl border p-4"
          style={{
            background: 'color-mix(in oklab, var(--accent-warning) 10%, var(--bg-secondary))',
            borderColor: 'color-mix(in oklab, var(--accent-warning) 34%, var(--border-subtle))',
          }}
        >
          <div className="flex items-start gap-3">
            <UserCircle size={20} weight="duotone" style={{ color: 'var(--accent-warning)' }} />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                Профиль загружен частично
              </p>
              <p className="mt-1 text-xs" style={{ color: 'var(--text-secondary)' }}>
                Не удалось получить расширенные данные аккаунта, но вход активен.
              </p>
            </div>
          </div>
        </section>
      )}

      <section className="flex flex-col gap-2">
        <Button
          type="button"
          size="lg"
          className="min-h-12 w-full gap-2 font-semibold"
          onClick={handleRestart}
          disabled={isRestarting}
        >
          <ArrowsClockwise size={18} weight="bold" aria-hidden="true" />
          {isRestarting ? 'Обновляем вход...' : 'Перезапустить вход'}
        </Button>
        <div className="flex items-center justify-center gap-1.5 text-[11px]" style={{ color: 'var(--text-muted)' }}>
          <CheckCircle size={13} weight="fill" aria-hidden="true" />
          Сессия будет восстановлена через Telegram
        </div>
      </section>
    </div>
  )
}

function ProfileStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex min-w-0 flex-col gap-1">
      <dt
        className="text-[10px] font-medium uppercase tracking-wide"
        style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}
      >
        {label}
      </dt>
      <dd
        className="truncate text-sm font-semibold"
        style={{ color: 'var(--text-primary)' }}
      >
        {value}
      </dd>
    </div>
  )
}
