import { useState } from 'react'
import { SignOut, Student, Shield, ChalkboardTeacher } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import { PushPermissionCard } from '@/features/push/PushPermissionCard'
import { Button } from '@/components/ui/button'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { cn } from '@/lib/utils'

/**
 * Profile page — identity card, notifications, and logout.
 *
 * The theme toggle used to live here; it was moved to the global AppHeader
 * in Step 6 so it's one tap away from every screen. What remains:
 *   - identity block (gradient avatar with initials + role chip + group id)
 *   - push permission card
 *   - logout confirmation flow
 */
export default function ProfilePage() {
  const { logout, user } = useAuth()
  const [showConfirm, setShowConfirm] = useState(false)
  const [isLoggingOut, setIsLoggingOut] = useState(false)

  const handleLogout = async () => {
    setIsLoggingOut(true)
    await logout()
  }

  const role = (user?.role ?? '').toUpperCase()
  const roleLabel =
    role === 'ADMIN' ? 'Администратор'
    : role === 'TEACHER' ? 'Преподаватель'
    : role === 'STUDENT' ? 'Студент'
    : user?.role ?? '—'

  const RoleIcon =
    role === 'ADMIN' ? Shield
    : role === 'TEACHER' ? ChalkboardTeacher
    : Student

  const initials = user ? String(user.id).slice(-2).padStart(2, '0') : '??'

  return (
    <div className="mx-auto flex w-full max-w-md flex-col gap-5 px-4 pb-6 pt-5">
      {/* Identity card */}
      <section
        className="relative overflow-hidden rounded-2xl border p-5"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
        }}
      >
        {/* Tint halo */}
        <span
          aria-hidden="true"
          className="pointer-events-none absolute -right-16 -top-16 size-48 rounded-full opacity-40"
          style={{ background: 'var(--gradient-glow)' }}
        />

        <div className="relative flex items-center gap-4">
          <span
            aria-hidden="true"
            className="grid size-16 place-items-center rounded-2xl text-xl font-bold"
            style={{
              background: 'var(--gradient-brand)',
              color: 'var(--accent-primary-contrast)',
              boxShadow: 'var(--glow-primary)',
              fontFamily: 'var(--font-display)',
            }}
          >
            {initials}
          </span>

          <div className="flex min-w-0 flex-1 flex-col gap-1.5">
            <p
              className="text-xs font-medium uppercase tracking-wide"
              style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}
            >
              ID {user?.id ?? '—'}
            </p>
            <p
              className="text-lg font-semibold leading-tight text-balance"
              style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
            >
              {roleLabel}
            </p>
            <span
              className="inline-flex w-fit items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium"
              style={{
                background: 'color-mix(in oklab, var(--accent-primary) 14%, transparent)',
                color: 'var(--accent-primary)',
                border: '1px solid var(--border-accent)',
              }}
            >
              <RoleIcon size={11} weight="fill" aria-hidden="true" />
              RutTrack PWA
            </span>
          </div>
        </div>

        {user?.groupId !== undefined && (
          <dl
            className="mt-4 grid grid-cols-2 gap-3 border-t pt-4"
            style={{ borderColor: 'var(--border-subtle)' }}
          >
            <div className="flex flex-col gap-1">
              <dt
                className="text-[10px] font-medium uppercase tracking-wide"
                style={{ color: 'var(--text-muted)' }}
              >
                Группа
              </dt>
              <dd
                className="text-sm font-semibold tabular-nums"
                style={{ color: 'var(--text-primary)' }}
              >
                #{user.groupId}
              </dd>
            </div>
          </dl>
        )}
      </section>

      {/* Notifications */}
      <PushPermissionCard />

      {/* Logout */}
      <section className="flex flex-col gap-3">
        {!showConfirm ? (
          <Button
            variant="destructive"
            className={cn('min-h-[48px] w-full gap-2 text-sm font-semibold')}
            onClick={() => setShowConfirm(true)}
          >
            <SignOut size={18} weight="bold" aria-hidden="true" />
            Выйти
          </Button>
        ) : (
          <Alert>
            <AlertDescription className="flex flex-col gap-4">
              <p className="text-sm text-pretty">Вы уверены, что хотите выйти?</p>
              <div className="flex gap-3">
                <Button
                  variant="destructive"
                  className="min-h-[44px] flex-1"
                  onClick={handleLogout}
                  disabled={isLoggingOut}
                >
                  Выйти
                </Button>
                <Button
                  variant="ghost"
                  className="min-h-[44px] flex-1"
                  onClick={() => setShowConfirm(false)}
                >
                  Остаться
                </Button>
              </div>
            </AlertDescription>
          </Alert>
        )}
      </section>
    </div>
  )
}
