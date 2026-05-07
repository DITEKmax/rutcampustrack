import { useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router'
import { AnimatePresence, motion, type PanInfo } from 'motion/react'
import { Bell, BellSlash, ArrowRight, GearSix } from '@phosphor-icons/react'
import {
  describeNotification,
  useNotificationCenter,
  type NotificationRecord,
} from './NotificationCenter'
import {
  useMarkAllRead,
  useMarkNotificationRead,
  useNotificationHistory,
} from './useNotificationHistory'
import type { HistoryItem } from './notificationsApi'

/**
 * Страница «Уведомления».
 *
 * Источник правды — серверная history (`GET /api/notifications`, M10):
 * 30-дневный лог, переживает релогин, F5 и переустановку PWA. Live-события
 * через STOMP инвалидируют react-query (см. NotificationCenter), поэтому
 * новые записи появляются без явного refetch'а. sessionStorage-кеш
 * NotificationCenter оставлен только для нативных банеров — список рендерим
 * исключительно из server query.
 */

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
    case 'homework.published':
    case 'homework.updated':
    case 'homework.weekly_digest':
    case 'homework.due_reminder':
      return '/homework'
    default:
      return null
  }
}

function isHomeworkNotification(type: string): boolean {
  return type === 'homework.published'
    || type === 'homework.updated'
    || type === 'homework.weekly_digest'
    || type === 'homework.due_reminder'
}

const HISTORY_TYPE_TO_EVENT_TYPE: Record<string, string> = {
  EXCUSE_REQUESTED: 'excuse.requested',
  EXCUSE_APPROVED: 'excuse.decided',
  EXCUSE_REJECTED: 'excuse.decided',
  LATE_CHECKIN_REQUESTED: 'late_checkin.requested',
  LATE_CHECKIN_APPROVED: 'late_checkin.decided',
  LATE_CHECKIN_REJECTED: 'late_checkin.decided',
  LESSON_STARTED: 'lesson.started',
  LESSON_CLOSED: 'lesson.closed',
  LESSON_CANCELLED: 'lesson.cancelled',
  LESSON_REMINDER: 'lesson.reminder',
  HOMEWORK_WEEKLY_DIGEST: 'homework.weekly_digest',
  HOMEWORK_DUE_REMINDER: 'homework.due_reminder',
  ATTENDANCE_MARKED_BY_HEADMAN: 'attendance.marked',
}

function normalizeHistoryPayload(item: HistoryItem): Record<string, unknown> {
  const payload = { ...(item.payload ?? {}) }
  if (
    (item.type === 'EXCUSE_APPROVED' ||
      item.type === 'LATE_CHECKIN_APPROVED') &&
    typeof payload.status !== 'string'
  ) {
    payload.status = 'approved'
  }
  if (
    (item.type === 'EXCUSE_REJECTED' ||
      item.type === 'LATE_CHECKIN_REJECTED') &&
    typeof payload.status !== 'string'
  ) {
    payload.status = 'rejected'
  }
  if (
    item.type === 'ATTENDANCE_MARKED_BY_HEADMAN' &&
    typeof payload.marked_by !== 'string'
  ) {
    payload.marked_by = 'headman'
  }
  return payload
}

function toRecord(item: HistoryItem): NotificationRecord {
  return {
    id: item.id,
    type: HISTORY_TYPE_TO_EVENT_TYPE[item.type] ?? item.type,
    payload: normalizeHistoryPayload(item),
    receivedAt: item.sentAt,
    read: item.readAt !== null,
    archived: false,
  }
}

export function NotificationsPage() {
  const navigate = useNavigate()
  // sessionStorage-кеш — для совместимости с архивом (swipe убирает локально).
  const { archive, items: liveItems } = useNotificationCenter()
  const archivedIds = useMemo(
    () => new Set(liveItems.filter((i) => i.archived).map((i) => i.id)),
    [liveItems],
  )

  const {
    data,
    isLoading,
    isError,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useNotificationHistory(false)

  const markAllRead = useMarkAllRead()
  const markRead = useMarkNotificationRead()

  // Сбрасываем счётчик непрочитанных при первом открытии.
  useEffect(() => {
    markAllRead.mutate()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const visible = useMemo(() => {
    const pages = data?.pages ?? []
    const flat: NotificationRecord[] = []
    for (const p of pages) for (const it of p.items) flat.push(toRecord(it))
    return flat.filter((r) => !archivedIds.has(r.id))
  }, [data, archivedIds])

  const handleTap = (item: NotificationRecord) => {
    if (!item.read) markRead.mutate(item.id)
    const route = notificationRoute(item.type)
    if (route) navigate(route)
  }

  return (
    <div className="flex min-h-full flex-col p-4">
      <header className="mb-4 flex items-center gap-3">
        <Bell size={22} weight="fill" style={{ color: 'var(--accent-primary)' }} />
        <div className="flex flex-1 flex-col">
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
        <button
          type="button"
          onClick={() => navigate('/notifications/settings')}
          aria-label="Настройки уведомлений"
          className="grid size-10 place-items-center rounded-full"
          style={{
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border-subtle)',
            color: 'var(--text-primary)',
          }}
        >
          <GearSix size={18} weight="bold" />
        </button>
      </header>

      {isLoading ? (
        <LoadingState />
      ) : isError ? (
        <ErrorState />
      ) : visible.length === 0 ? (
        <EmptyState />
      ) : (
        <>
          <ul className="flex flex-col gap-3">
            <AnimatePresence initial={false}>
              {visible.map((item) => (
                <NotificationRow
                  key={item.id}
                  item={item}
                  onArchive={() => archive(item.id)}
                  onTap={() => handleTap(item)}
                  hasRoute={!!notificationRoute(item.type)}
                />
              ))}
            </AnimatePresence>
          </ul>
          {hasNextPage && (
            <button
              type="button"
              onClick={() => fetchNextPage()}
              disabled={isFetchingNextPage}
              className="mx-auto mt-4 rounded-full px-4 py-2 text-sm"
              style={{
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-subtle)',
                color: 'var(--text-primary)',
                opacity: isFetchingNextPage ? 0.5 : 1,
              }}
            >
              {isFetchingNextPage ? 'Загрузка…' : 'Загрузить ещё'}
            </button>
          )}
        </>
      )}
    </div>
  )
}

function NotificationRow({
  item,
  onArchive,
  onTap,
  hasRoute,
}: {
  item: NotificationRecord
  onArchive: () => void
  onTap: () => void
  hasRoute: boolean
}) {
  const { title, body } = describeNotification(item)

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
        cursor: 'pointer',
      }}
      aria-label={`${title}. ${hasRoute ? 'Нажми чтобы перейти.' : ''} Потяни в сторону чтобы архивировать`}
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
              className={
                isHomeworkNotification(item.type)
                  ? 'mt-0.5 whitespace-pre-wrap text-xs'
                  : 'mt-0.5 line-clamp-2 text-xs'
              }
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
        {hasRoute && (
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

function LoadingState() {
  return (
    <div
      className="mt-10 flex flex-col items-center gap-2 text-center"
      style={{ color: 'var(--text-muted)' }}
    >
      <p className="text-sm">Загружаем уведомления…</p>
    </div>
  )
}

function ErrorState() {
  return (
    <div
      className="mt-10 rounded-2xl border border-dashed p-6 text-center"
      style={{ borderColor: 'var(--border-default)', color: 'var(--text-muted)' }}
    >
      <p className="text-sm">Не удалось загрузить историю. Проверь соединение и потяни вниз для обновления.</p>
    </div>
  )
}
