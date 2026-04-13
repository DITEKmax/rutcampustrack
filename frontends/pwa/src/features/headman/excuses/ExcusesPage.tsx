import { Link } from 'react-router'
import { ArrowLeft, FileText } from '@phosphor-icons/react'

/**
 * ExcusesPage — graceful-degradation shell (D-10).
 *
 * The backend endpoint for pending excuses is deferred. We intentionally do NOT
 * render a list here even if `usePendingExcuses` were wired. The empty state is
 * always shown for Phase 56. When future phases ship the approval flow, replace
 * the empty-state block with a list and approve/reject controls.
 */
export function ExcusesPage() {
  return (
    <div className="min-h-screen p-6">
      <Link
        to="/group"
        aria-label="Назад"
        className="inline-flex items-center gap-2 mb-6 text-sm"
      >
        <ArrowLeft size={20} /> Назад
      </Link>
      <h1 className="text-lg font-semibold mb-8">Пропуски</h1>
      <div className="flex flex-col items-center justify-center text-center py-12">
        <div
          className="w-20 h-20 rounded-full flex items-center justify-center mb-4"
          style={{
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border-subtle)',
          }}
        >
          <FileText size={36} weight="duotone" style={{ color: 'var(--text-muted)' }} />
        </div>
        <h2 className="text-base font-semibold mb-2">Функция в разработке</h2>
        <p
          className="text-sm max-w-[280px]"
          style={{ color: 'var(--text-secondary)' }}
        >
          Запросы студентов на одобрение пропусков появятся здесь. Сейчас эта функция находится в разработке.
        </p>
      </div>
    </div>
  )
}
