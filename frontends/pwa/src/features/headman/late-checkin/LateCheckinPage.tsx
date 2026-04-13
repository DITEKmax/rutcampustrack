import { Link } from 'react-router'
import { ArrowLeft, Clock } from '@phosphor-icons/react'

/**
 * LateCheckinPage — graceful-degradation shell (D-10).
 *
 * The backend endpoint for pending late-checkin requests is deferred. We
 * intentionally render the empty state for Phase 56. When future phases ship
 * the approval flow, replace the empty-state block with a list + controls.
 */
export function LateCheckinPage() {
  return (
    <div className="min-h-screen p-6">
      <Link
        to="/group"
        aria-label="Назад"
        className="inline-flex items-center gap-2 mb-6 text-sm"
      >
        <ArrowLeft size={20} /> Назад
      </Link>
      <h1 className="text-lg font-semibold mb-8">Запросы отметки</h1>
      <div className="flex flex-col items-center justify-center text-center py-12">
        <div
          className="w-20 h-20 rounded-full flex items-center justify-center mb-4"
          style={{
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border-subtle)',
          }}
        >
          <Clock size={36} weight="duotone" style={{ color: 'var(--text-muted)' }} />
        </div>
        <h2 className="text-base font-semibold mb-2">Функция в разработке</h2>
        <p
          className="text-sm max-w-[280px]"
          style={{ color: 'var(--text-secondary)' }}
        >
          Запросы студентов на опоздалую отметку появятся здесь. Сейчас эта функция находится в разработке.
        </p>
      </div>
    </div>
  )
}
