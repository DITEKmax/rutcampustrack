import { useNavigate } from 'react-router'
import { useEffect } from 'react'
import { ArrowLeft, BellSlash, MoonStars } from '@phosphor-icons/react'
import {
  CATEGORY_LABELS,
  DEFAULT_PREFS,
  isMutedNow,
  loadPrefs,
  useNotificationPrefs,
  type NotificationCategory,
  type NotificationPrefs,
} from './notificationPrefs'
import {
  fetchNotificationPreferences,
  updateNotificationPreferences,
} from './notificationsApi'

/**
 * Настройки уведомлений. Хранятся в localStorage (клиентская фильтрация),
 * серверу ничего не отправляется — поэтому настройки применяются мгновенно
 * и не зависят от рассылающей стороны.
 *
 * Что умеет:
 * 1. Toggle по категориям (см. {@link NotificationCategory}).
 *    Выключенная категория не пишется в историю и не триггерит банер.
 * 2. Тихий режим: окно времени, в которое native Notification подавляется,
 *    но запись в историю продолжает идти — пользователь увидит её утром.
 * 3. Ссылка на системное разрешение — если пользователь отозвал, включить
 *    обратно можно только из настроек браузера, это вне нашего контроля.
 */
export default function NotificationSettingsPage() {
  const navigate = useNavigate()
  const [prefs, setPrefs] = useNotificationPrefs()
  const mutedActive = isMutedNow(prefs)

  useEffect(() => {
    let mounted = true
    fetchNotificationPreferences()
      .then((serverPrefs) => {
        if (!mounted) return
        const current = loadPrefs()
        setPrefs({
          ...current,
          categories: {
            ...current.categories,
            ...serverPrefs.categories,
          },
          mutedUntil: serverPrefs.mutedUntil,
        })
      })
      .catch(() => {
        // Settings remain usable offline with local prefs.
      })
    return () => {
      mounted = false
    }
  }, [setPrefs])

  const syncPrefs = (next: NotificationPrefs) => {
    setPrefs(next)
    updateNotificationPreferences({
      categories: next.categories,
      mutedUntil: next.mutedUntil,
    }).catch(() => {
      // Local state is still useful offline; server sync will retry on next edit.
    })
  }

  const toggleCategory = (category: NotificationCategory) => {
    const next: NotificationPrefs = {
      ...prefs,
      categories: {
        ...prefs.categories,
        [category]: !prefs.categories[category],
      },
    }
    syncPrefs(next)
  }

  const toggleQuietHours = () => {
    if (prefs.quietHours) {
      setPrefs({ ...prefs, quietHours: null })
    } else {
      setPrefs({ ...prefs, quietHours: { from: '22:00', to: '08:00' } })
    }
  }

  const updateQuietHours = (field: 'from' | 'to', value: string) => {
    if (!prefs.quietHours) return
    setPrefs({
      ...prefs,
      quietHours: { ...prefs.quietHours, [field]: value },
    })
  }

  const setPause = (days: number) => {
    const mutedUntil = new Date(
      Date.now() + days * 24 * 60 * 60 * 1000,
    ).toISOString()
    syncPrefs({ ...prefs, mutedUntil })
  }

  const clearPause = () => syncPrefs({ ...prefs, mutedUntil: null })

  const resetAll = () => syncPrefs(DEFAULT_PREFS)

  const categories: NotificationCategory[] = [
    'lessons',
    'reminders',
    'homework',
    'tickets',
    'schedule',
    'group',
  ]

  return (
    <div className="mx-auto flex w-full max-w-md flex-col gap-5 px-4 pb-6 pt-4">
      <header className="flex items-center gap-3">
        <button
          type="button"
          onClick={() => navigate(-1)}
          aria-label="Назад"
          className="grid size-10 place-items-center rounded-full"
          style={{
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border-subtle)',
            color: 'var(--text-primary)',
          }}
        >
          <ArrowLeft size={18} weight="bold" />
        </button>
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
            Настройки применяются к этому устройству
          </p>
        </div>
      </header>

      <section
        className="rounded-2xl border"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
        }}
      >
        <h2
          className="px-4 pt-4 text-sm font-semibold"
          style={{ color: 'var(--text-primary)' }}
        >
          Категории
        </h2>
        <ul className="flex flex-col">
          {categories.map((cat, i) => (
            <li
              key={cat}
              className="flex items-center justify-between gap-4 px-4 py-3"
              style={{
                borderTop: i === 0 ? 'none' : '1px solid var(--border-subtle)',
              }}
            >
              <span
                className="text-sm"
                style={{ color: 'var(--text-secondary)' }}
              >
                {CATEGORY_LABELS[cat]}
              </span>
              <Toggle
                checked={prefs.categories[cat]}
                onChange={() => toggleCategory(cat)}
                label={CATEGORY_LABELS[cat]}
              />
            </li>
          ))}
        </ul>
      </section>

      <section
        className="rounded-2xl border"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
        }}
      >
        <div className="flex items-center gap-2 px-4 pt-4">
          <BellSlash
            size={18}
            weight="duotone"
            style={{ color: 'var(--accent-primary)' }}
          />
          <h2
            className="text-sm font-semibold"
            style={{ color: 'var(--text-primary)' }}
          >
            Пауза
          </h2>
        </div>
        <p
          className="px-4 pt-1 text-xs"
          style={{ color: 'var(--text-muted)' }}
        >
          {mutedActive && prefs.mutedUntil
            ? `До ${formatMutedUntil(prefs.mutedUntil)}`
            : 'Без временной паузы'}
        </p>
        <div className="grid grid-cols-2 gap-2 px-4 py-3">
          <button
            type="button"
            onClick={() => setPause(1)}
            className="rounded-lg border px-3 py-2 text-sm font-medium"
            style={{
              background: 'var(--bg-primary)',
              borderColor: 'var(--border-default)',
              color: 'var(--text-primary)',
            }}
          >
            На день
          </button>
          <button
            type="button"
            onClick={() => setPause(7)}
            className="rounded-lg border px-3 py-2 text-sm font-medium"
            style={{
              background: 'var(--bg-primary)',
              borderColor: 'var(--border-default)',
              color: 'var(--text-primary)',
            }}
          >
            На неделю
          </button>
          <button
            type="button"
            onClick={clearPause}
            className="col-span-2 rounded-lg border px-3 py-2 text-sm font-medium"
            disabled={!mutedActive}
            style={{
              background: mutedActive
                ? 'var(--bg-primary)'
                : 'var(--bg-tertiary)',
              borderColor: 'var(--border-default)',
              color: mutedActive ? 'var(--text-primary)' : 'var(--text-muted)',
            }}
          >
            Снять паузу
          </button>
        </div>
      </section>

      <section
        className="rounded-2xl border"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
        }}
      >
        <div className="flex items-center justify-between gap-4 px-4 pt-4">
          <div className="flex items-center gap-2">
            <MoonStars
              size={18}
              weight="duotone"
              style={{ color: 'var(--accent-primary)' }}
            />
            <h2
              className="text-sm font-semibold"
              style={{ color: 'var(--text-primary)' }}
            >
              Тихий режим
            </h2>
          </div>
          <Toggle
            checked={!!prefs.quietHours}
            onChange={toggleQuietHours}
            label="Тихий режим"
          />
        </div>
        <p
          className="px-4 pt-1 text-xs"
          style={{ color: 'var(--text-muted)' }}
        >
          В указанном окне банер не появляется, но уведомление попадает в
          историю.
        </p>
        {prefs.quietHours && (
          <div className="flex gap-3 px-4 py-3">
            <TimeField
              label="С"
              value={prefs.quietHours.from}
              onChange={(v) => updateQuietHours('from', v)}
            />
            <TimeField
              label="По"
              value={prefs.quietHours.to}
              onChange={(v) => updateQuietHours('to', v)}
            />
          </div>
        )}
      </section>

      <button
        type="button"
        onClick={resetAll}
        className="self-start text-xs underline"
        style={{ color: 'var(--text-muted)' }}
      >
        Сбросить к настройкам по умолчанию
      </button>
    </div>
  )
}

function formatMutedUntil(value: string): string {
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return value
  return date.toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function Toggle({
  checked,
  onChange,
  label,
}: {
  checked: boolean
  onChange: () => void
  label: string
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={onChange}
      className="relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full transition-colors"
      style={{
        background: checked
          ? 'var(--accent-primary)'
          : 'var(--border-default)',
      }}
    >
      <span
        className="inline-block size-5 transform rounded-full bg-white shadow transition-transform"
        style={{
          transform: checked ? 'translateX(22px)' : 'translateX(2px)',
        }}
      />
    </button>
  )
}

function TimeField({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (next: string) => void
}) {
  return (
    <label className="flex flex-1 flex-col gap-1">
      <span className="text-xs" style={{ color: 'var(--text-muted)' }}>
        {label}
      </span>
      <input
        type="time"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="rounded-lg border px-3 py-2 text-sm"
        style={{
          background: 'var(--bg-primary)',
          borderColor: 'var(--border-default)',
          color: 'var(--text-primary)',
        }}
      />
    </label>
  )
}
