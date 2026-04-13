import { useState, useMemo } from 'react'
import { Link } from 'react-router'
import { ArrowLeft } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'
import {
  useGroupSubjects,
  useJournal,
} from '@/features/headman/shared/headmanApi'
import { JournalStudentRow } from './JournalStudentRow'

function todayIso(): string {
  return new Date().toISOString().slice(0, 10)
}

function formattedDate(iso: string): string {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleDateString('ru-RU')
  } catch {
    return iso
  }
}

export function JournalPage() {
  const { user } = useAuth()
  const groupId = user?.groupId
  const [step, setStep] = useState<1 | 2>(1)
  const [subjectId, setSubjectId] = useState<number | ''>('')
  const [date, setDate] = useState<string>(todayIso())

  const subjectsQuery = useGroupSubjects()
  const journalQuery = useJournal(
    groupId ?? 0,
    subjectId === '' ? 0 : Number(subjectId),
    date,
    date,
  )

  const selectedSubjectName = useMemo(() => {
    const list = subjectsQuery.data ?? []
    const s = list.find((x) => x.id === Number(subjectId))
    return s?.name ?? ''
  }, [subjectsQuery.data, subjectId])

  const handleSubmit = () => {
    if (!subjectId) return
    setStep(2)
  }

  // Step 1: selectors
  if (step === 1) {
    return (
      <div className="p-6">
        <Link
          to="/group"
          aria-label="Назад к группе"
          className="inline-flex items-center gap-2 mb-4"
        >
          <ArrowLeft size={20} /> Назад
        </Link>
        <h1 className="text-lg font-semibold mb-4">Журнал</h1>

        <div
          className="rounded-lg p-4 mb-4"
          style={{ background: 'var(--bg-secondary)' }}
        >
          <h2 className="text-base font-semibold mb-3">
            Выберите предмет и дату
          </h2>

          <label
            htmlFor="journal-subject"
            className="block text-sm font-semibold mb-1"
          >
            Предмет
          </label>
          <select
            id="journal-subject"
            aria-label="Предмет"
            value={subjectId}
            onChange={(e) =>
              setSubjectId(e.target.value === '' ? '' : Number(e.target.value))
            }
            className="w-full min-h-[44px] rounded-md px-3 mb-4"
            style={{
              background: 'var(--bg-elevated)',
              color: 'var(--text-primary)',
            }}
          >
            <option value="">Выберите предмет</option>
            {(subjectsQuery.data ?? []).map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>

          <label
            htmlFor="journal-date"
            className="block text-sm font-semibold mb-1"
          >
            Дата
          </label>
          <input
            id="journal-date"
            type="date"
            aria-label="Дата"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className="w-full min-h-[44px] rounded-md px-3 mb-4"
            style={{
              background: 'var(--bg-elevated)',
              color: 'var(--text-primary)',
            }}
          />

          <button
            type="button"
            onClick={handleSubmit}
            disabled={!subjectId || journalQuery.isLoading}
            className="w-full min-h-[44px] rounded-md font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
            style={{
              background: 'var(--accent-primary)',
              color: '#fff',
            }}
          >
            {journalQuery.isLoading ? 'Загружаем…' : 'Загрузить журнал'}
          </button>

          {journalQuery.isError && (
            <p
              role="alert"
              className="mt-3 text-sm font-semibold"
              style={{ color: 'var(--accent-danger)' }}
            >
              Ошибка загрузки. Проверьте подключение и попробуйте снова.
            </p>
          )}
        </div>
      </div>
    )
  }

  // Step 2: student list
  return (
    <div className="p-6">
      <div className="flex items-center gap-3 mb-4">
        <button
          type="button"
          aria-label="Назад"
          onClick={() => setStep(1)}
          className="inline-flex items-center justify-center min-w-[40px] min-h-[40px]"
        >
          <ArrowLeft size={20} />
        </button>
        <div className="flex flex-col">
          <span className="text-base font-semibold">Журнал</span>
          <span
            className="text-sm"
            style={{ color: 'var(--text-muted)' }}
          >
            {selectedSubjectName} · {formattedDate(date)}
          </span>
        </div>
      </div>

      {journalQuery.isLoading ? (
        <div className="space-y-2">
          {[0, 1, 2, 3].map((i) => (
            <div
              key={i}
              className="rounded-lg animate-pulse min-h-[56px]"
              style={{ background: 'var(--bg-secondary)' }}
            />
          ))}
        </div>
      ) : (journalQuery.data ?? []).length === 0 ? (
        <p
          className="text-center text-sm mt-8"
          style={{ color: 'var(--text-muted)' }}
        >
          На эту дату занятий нет
        </p>
      ) : (
        <>
          <div>
            {(journalQuery.data ?? []).map((cell) => (
              <JournalStudentRow
                key={`${cell.lessonId}-${cell.studentId}`}
                lessonId={cell.lessonId}
                studentId={cell.studentId}
                studentName={cell.studentName}
                initialStatus={cell.status}
              />
            ))}
          </div>
          <p
            className="text-xs text-center mt-4"
            style={{ color: 'var(--text-muted)' }}
          >
            Изменения сохраняются автоматически
          </p>
        </>
      )}
    </div>
  )
}
