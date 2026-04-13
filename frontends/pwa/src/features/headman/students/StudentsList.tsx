import { Link } from 'react-router'
import { ArrowLeft } from '@phosphor-icons/react'

export function StudentsList() {
  return (
    <div className="p-6">
      <Link to="/group" aria-label="Назад к группе" className="inline-flex items-center gap-2 mb-4">
        <ArrowLeft size={20} /> Назад
      </Link>
      <h1 className="text-lg font-semibold">Студенты</h1>
      <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Загрузка…</p>
    </div>
  )
}
