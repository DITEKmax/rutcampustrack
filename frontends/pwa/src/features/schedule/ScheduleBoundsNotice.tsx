import { CalendarBlank } from '@phosphor-icons/react'
import { formatLongDate } from '@/shared/lib/dateUtils'

interface Props {
  /** Reason we can't navigate further. */
  kind: 'before-start' | 'after-end' | 'no-active'
  /** Activation date — shown in info message depending on kind. */
  activeFrom?: Date | null
  activeTo?: Date | null
  /** Start of the next semester (when kind='after-end' or 'no-active'). */
  nextFrom?: Date | null
  onReturn?: () => void
}

/**
 * Info screen shown when the user has navigated past the bounds of the
 * active semester. Rendered in place of the lesson list.
 *
 * D1 решение 5:
 * - `before-start` — семестр ещё не начался (активный, но сегодня < dateFrom).
 * - `after-end` — семестр уже закончился (активный, но сегодня > dateTo).
 * - `no-active` — каникулы (нет активного семестра вообще).
 */
export function ScheduleBoundsNotice({
  kind,
  activeFrom,
  activeTo,
  nextFrom,
  onReturn,
}: Props) {
  const { title, description } = messageFor(kind, activeFrom, activeTo, nextFrom)

  return (
    <div
      className="mt-6 flex flex-col items-center gap-3 rounded-2xl border border-dashed p-8 text-center"
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
        <CalendarBlank size={24} weight="duotone" />
      </div>
      <h2
        className="text-lg font-semibold text-balance"
        style={{
          color: 'var(--text-primary)',
          fontFamily: 'var(--font-heading)',
        }}
      >
        {title}
      </h2>
      <p
        className="max-w-xs text-sm text-pretty"
        style={{ color: 'var(--text-secondary)' }}
      >
        {description}
      </p>
      {onReturn && (
        <button
          type="button"
          onClick={onReturn}
          className="mt-1 rounded-full px-5 py-2.5 text-sm font-semibold"
          style={{
            background: 'var(--gradient-brand)',
            color: 'var(--accent-primary-contrast)',
            boxShadow: 'var(--glow-primary)',
          }}
        >
          Вернуться к сегодня
        </button>
      )}
    </div>
  )
}

function messageFor(
  kind: Props['kind'],
  activeFrom: Date | null | undefined,
  activeTo: Date | null | undefined,
  nextFrom: Date | null | undefined,
): { title: string; description: string } {
  if (kind === 'before-start' && activeFrom) {
    return {
      title: 'Семестр ещё не начался',
      description: `Занятия стартуют ${formatLongDate(activeFrom)}. До этого — каникулы.`,
    }
  }
  if (kind === 'after-end' && activeTo) {
    return {
      title: 'Семестр закончился',
      description: nextFrom
        ? `Семестр закончился ${formatLongDate(activeTo)}. Следующий стартует ${formatLongDate(nextFrom)}.`
        : `Семестр закончился ${formatLongDate(activeTo)}. Новый пока не назначен.`,
    }
  }
  if (kind === 'no-active') {
    return {
      title: 'Сейчас каникулы',
      description: nextFrom
        ? `Ближайший семестр начнётся ${formatLongDate(nextFrom)}.`
        : 'Новый семестр пока не назначен. Жди объявления от администратора.',
    }
  }
  return {
    title: 'Нет расписания',
    description: 'На этот период пары не загружены.',
  }
}
