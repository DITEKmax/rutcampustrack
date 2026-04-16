import { motion } from 'motion/react'
import type { MeResponse, GroupResponse } from './api'

interface Props {
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

export function ProfileHeader({ me, group, isLoading }: Props) {
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
      transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
      className="relative overflow-hidden rounded-[var(--radius-lg)] border p-[var(--space-5)]"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
      }}
    >
      <span
        aria-hidden="true"
        className="pointer-events-none absolute -right-20 -top-20 size-56 rounded-full opacity-25"
        style={{ background: 'var(--gradient-brand)' }}
      />
      <div className="relative flex items-center gap-[var(--space-4)]">
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
            className="truncate text-[var(--text-xl)] font-semibold leading-tight"
            style={{
              color: 'var(--text-primary)',
              fontFamily: 'var(--font-heading)',
            }}
          >
            {displayName}
          </p>
          <p
            className="text-[var(--text-sm)]"
            style={{ color: 'var(--text-secondary)' }}
          >
            {subtitle}
          </p>
        </div>
      </div>
    </motion.section>
  )
}
