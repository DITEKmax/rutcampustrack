import { useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router'
import { AnimatePresence, motion, type PanInfo } from 'motion/react'
import { Bell, BellSlash, ArrowRight } from '@phosphor-icons/react'
import {
  describeNotification,
  isHeadmanOnlyType,
  useNotificationCenter,
  type NotificationRecord,
} from './NotificationCenter'

function formatRelative(iso: string): string {
  const dt = new Date(iso).getTime()
  const diff = Date.now() - dt
  if (diff < 60_000) return 'только что'
  const mins = Math.round(diff / 60_000)
  if (mins < 60) return `${mins} мин назад`
  const hours = Math.round(mins / 60)
  if (hours < 24) return `${hours} ч назад`
  return new Date(iso).toLocaleDateString('ru-RU')
}

function notificationRoute(type: string): string | null {
  switch (type) {
    case 'late_checkin.requested':
      return '/group/late-checkin'
    case 'excuse.requested':
      return '/group/excuses'
    default:
      return null
  }
}

export function NotificationsPage() {
  const { items, markAllRead, archive } = useNotificationCenter()
  const navigate = useNavigate()

  useEffect(() => {
    markAllRead()
  }, [markAllRead])

  const visible = useMemo(
    () =>
      items
        .filter((i) => !i.archived)
        .sort((a, b) => b.receivedAt.localeCompare(a.receivedAt)),
    [items],
  )

  const handleTap = (item: NotificationRecord) => {
    const route = notificationRoute(item.type)
    if (route) navigate(route)
  }

  return (
    <div className="flex min-h-full flex-col p-4">
      <header className="mb-4 flex items-center gap-3">
        <Bell size={22} weight="fill" style={{ color: 'var(--accent-primary)' }} />
        <div className="flex flex-col">
          <h1
            className="text-lg font-semibold"
            style={{
              color: 'var(--text-primary)',
              fontFamily: 'var(--font-heading)',
            }}
          >
            Уведомления
          </h1>
          <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
            Смахни карточку, чтобы убрать в архив
          </p>
        </div>
      </header>

      {visible.length === 0 ? (
        <EmptyState />
      ) : (
        <ul className="flex flex-col gap-3">
          <AnimatePresence initial={false}>
            {visible.map((item) => (
              <NotificationRow
                key={item.id}
                item={item}
                onArchive={() => archive(item.id)}
                onTap={notificationRoute(item.type) ? () => handleTap(item) : undefined}
              />
            ))}
          </AnimatePresence>
        </ul>
      )}
    </div>
  )
}

function NotificationRow({
  item,
  onArchive,
  onTap,
}: {
  item: NotificationRecord
  onArchive: () => void
  onTap?: () => void
}) {
  const { title, body } = describeNotification(item)
  const actionable = !!onTap

  const handleDragEnd = (_e: never, info: PanInfo) => {
    if (Math.abs(info.offset.x) > 96) {
      onArchive()
    }
  }

  return (
    <motion.li
      layout
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: 80, height: 0, marginTop: 0, paddingTop: 0, paddingBottom: 0 }}
      transition={{ duration: 0.2, ease: 'easeOut' }}
      drag="x"
      dragConstraints={{ left: -160, right: 160 }}
      dragElastic={0.2}
      onDragEnd={handleDragEnd as never}
      onClick={onTap}
      className="relative overflow-hidden rounded-2xl border p-4"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: item.read ? 'var(--border-subtle)' : 'var(--border-accent)',
        touchAction: 'pan-y',
        cursor: actionable ? 'pointer' : undefined,
      }}
      aria-label={`${title}. ${actionable ? 'Нажми чтобы перейти.' : ''} Потяни в сторону чтобы архивировать`}
    >
      <div className="flex items-start gap-3">
        <span
          aria-hidden="true"
          className="mt-1 size-2 shrink-0 rounded-full"
          style={{
            background: item.read
              ? 'var(--text-muted)'
              : 'var(--accent-primary)',
            boxShadow: item.read ? 'none' : '0 0 6px var(--accent-primary)',
          }}
        />
        <div className="min-w-0 flex-1">
          <p
            className="truncate text-sm font-semibold"
            style={{ color: 'var(--text-primary)' }}
          >
            {title}
          </p>
          {body && (
            <p
              className="mt-0.5 line-clamp-2 text-xs"
              style={{ color: 'var(--text-secondary)' }}
            >
              {body}
            </p>
          )}
          <p
            className="mt-1 text-[11px]"
            style={{ color: 'var(--text-muted)' }}
          >
            {formatRelative(item.receivedAt)}
          </p>
        </div>
        {actionable && (
          <ArrowRight
            size={16}
            weight="bold"
            className="mt-1 shrink-0"
            style={{ color: 'var(--accent-primary)' }}
          />
        )}
      </div>
    </motion.li>
  )
}

function EmptyState() {
  return (
    <div
      className="mt-10 flex flex-col items-center gap-3 rounded-2xl border border-dashed p-8 text-center"
      style={{
        borderColor: 'var(--border-default)',
        background: 'color-mix(in oklab, var(--bg-secondary) 50%, transparent)',
      }}
    >
      <div
        className="grid size-14 place-items-center rounded-full"
        style={{
          background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
          border: '1px solid var(--border-accent)',
          color: 'var(--accent-primary)',
        }}
      >
        <BellSlash size={24} weight="duotone" />
      </div>
      <h2
        className="text-base font-semibold"
        style={{
          color: 'var(--text-primary)',
          fontFamily: 'var(--font-heading)',
        }}
      >
        Тихо
      </h2>
      <p
        className="max-w-xs text-sm"
        style={{ color: 'var(--text-secondary)' }}
      >
        Здесь появятся оповещения о парах, домашках и решениях по
        тикетам. Смахнуть вбок — убрать в архив.
      </p>
    </div>
  )
}
