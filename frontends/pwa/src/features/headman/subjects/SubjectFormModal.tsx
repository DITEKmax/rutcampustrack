import { useState, useEffect } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { X } from '@phosphor-icons/react'
import {
  useGroupTeachers,
  useCreateSubject,
  useUpdateSubject,
  mapHeadmanApiError,
} from '@/features/headman/shared/headmanApi'
import type { Subject } from '@/features/headman/shared/types'
import { useAuth } from '@/features/auth/AuthProvider'

interface Props {
  open: boolean
  mode: 'create' | 'edit'
  subject?: Subject
  onClose: () => void
}

export function SubjectFormModal({ open, mode, subject, onClose }: Props) {
  const { user } = useAuth()
  const groupId = user?.groupId ?? 0

  const [name, setName] = useState('')
  const [teacherId, setTeacherId] = useState<number | ''>('')

  const teachersQuery = useGroupTeachers(groupId)
  const createSubject = useCreateSubject()
  const updateSubject = useUpdateSubject()

  // Prefill when editing
  useEffect(() => {
    if (mode === 'edit' && subject) {
      setName(subject.name)
      setTeacherId(subject.teacherId ?? '')
    } else {
      setName('')
      setTeacherId('')
    }
  }, [mode, subject, open])

  const mutation = mode === 'create' ? createSubject : updateSubject
  const errorStatus =
    mutation.isError ? ((mutation.error as any)?.response?.status ?? 500) : null

  function handleSave() {
    if (!name.trim() || name.trim().length < 2) return
    const body = { name: name.trim(), teacherId: teacherId ? Number(teacherId) : null }

    if (mode === 'create') {
      createSubject.mutate(body, {
        onSuccess: () => {
          handleClose()
        },
      })
    } else if (subject) {
      updateSubject.mutate({ id: subject.id, body }, {
        onSuccess: () => {
          handleClose()
        },
      })
    }
  }

  function handleClose() {
    setName('')
    setTeacherId('')
    onClose()
  }

  const heading = mode === 'create' ? 'Новый предмет' : 'Редактировать предмет'

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="fixed inset-0 z-50 flex items-start justify-center"
          style={{ background: 'rgba(0,0,0,0.4)' }}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={(e) => {
            if (e.target === e.currentTarget) handleClose()
          }}
        >
          <motion.div
            className="rounded-lg p-6 w-full max-w-md mx-4 mt-24"
            style={{ background: 'var(--bg-secondary)' }}
            initial={{ opacity: 0, y: -16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -16 }}
            transition={{ duration: 0.15 }}
          >
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-base font-semibold">{heading}</h2>
              <button onClick={handleClose} aria-label="Закрыть">
                <X size={20} />
              </button>
            </div>

            {/* Subject name */}
            <div className="mb-4">
              <label
                className="block text-sm font-medium mb-1"
                htmlFor="subject-name-input"
              >
                Название
              </label>
              <input
                id="subject-name-input"
                type="text"
                aria-label="Название"
                className="w-full rounded-md px-3 py-2 text-sm"
                style={{
                  background: 'var(--bg-primary, #fff)',
                  border: '1px solid var(--border-subtle)',
                }}
                value={name}
                onChange={(e) => setName(e.target.value)}
                minLength={2}
                placeholder="Введите название предмета"
              />
            </div>

            {/* Teacher picker */}
            <div className="mb-4">
              <label
                className="block text-sm font-medium mb-1"
                htmlFor="subject-teacher-select"
              >
                Преподаватель
              </label>
              <select
                id="subject-teacher-select"
                aria-label="Преподаватель"
                className="w-full rounded-md px-3 py-2 text-sm"
                style={{
                  background: 'var(--bg-primary, #fff)',
                  border: '1px solid var(--border-subtle)',
                }}
                value={teacherId}
                onChange={(e) =>
                  setTeacherId(e.target.value ? Number(e.target.value) : '')
                }
              >
                <option value="">Не выбран</option>
                {(teachersQuery.data ?? []).map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.fullName}
                  </option>
                ))}
              </select>
            </div>

            {/* Error */}
            {errorStatus !== null && (
              <p className="text-sm mb-3" style={{ color: 'var(--accent-danger, #ef4444)' }}>
                {mapHeadmanApiError(errorStatus)}
              </p>
            )}

            {/* Actions */}
            <div className="flex gap-3">
              <button
                className="flex-1 rounded-lg px-4 py-2 text-sm font-medium text-white"
                style={{ background: 'var(--accent-primary)' }}
                onClick={handleSave}
                disabled={!name.trim() || name.trim().length < 2 || mutation.isPending}
              >
                Сохранить
              </button>
              <button
                className="flex-1 rounded-lg px-4 py-2 text-sm font-medium"
                style={{
                  background: 'var(--bg-primary, #f5f5f5)',
                  border: '1px solid var(--border-subtle)',
                }}
                onClick={handleClose}
              >
                Отмена
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
