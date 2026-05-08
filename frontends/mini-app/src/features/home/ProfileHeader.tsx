import { motion } from 'motion/react'
import type { GroupResponse, MeResponse } from './api'

interface ProfileHeaderProps {
  me?: MeResponse
  group?: GroupResponse
  isLoading?: boolean
}

function buildInitials(me?: MeResponse): string {
  const first = me?.firstName?.[0] ?? ''
  const last = me?.lastName?.[0] ?? ''
  const both = `${last}${first}`.trim()
  if (both) return both.toUpperCase()
  return me ? String(me.id).slice(-2).padStart(2, '0') : '??'
}

export function ProfileHeader({ me, group, isLoading }: ProfileHeaderProps) {
  const displayName = me?.fullName ?? (isLoading ? '...' : `Студент #${me?.id ?? ''}`)
  const subtitle = group?.name
    ? `Группа ${group.name} · РУТ МИИТ`
    : me?.groupId
      ? `Группа #${me.groupId} · РУТ МИИТ`
      : 'РУТ МИИТ'

  return (
    <motion.section
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.28, ease: 'easeOut' }}
      className="relative overflow-hidden rounded-2xl border p-4"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
      }}
    >
      <span
        aria-hidden="true"
        className="pointer-events-none absolute -right-20 -top-20 size-52 rounded-full opacity-25"
        style={{ background: 'var(--gradient-brand)' }}
      />
      <div className="relative flex items-center gap-3">
        <span
          aria-hidden="true"
          className="grid size-14 shrink-0 place-items-center rounded-2xl text-base font-bold tracking-tight"
          style={{
            background: 'var(--gradient-brand)',
            color: 'var(--accent-primary-contrast)',
            boxShadow: 'var(--glow-primary)',
            fontFamily: 'var(--font-display)',
          }}
        >
          {buildInitials(me)}
        </span>
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <p
            className="truncate text-lg font-semibold leading-tight"
            style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
          >
            {displayName}
          </p>
          <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
            {subtitle}
          </p>
        </div>
      </div>
    </motion.section>
  )
}
