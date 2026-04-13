import { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { X } from '@phosphor-icons/react'
import { useCreateAssistant, mapHeadmanApiError } from '@/features/headman/shared/headmanApi'
import type { GroupMember, AssistantPermission } from '@/features/headman/shared/types'

const PERMISSION_LABELS: { value: AssistantPermission; label: string }[] = [
  { value: 'manage_students', label: 'Управление студентами' },
  { value: 'manage_subjects', label: 'Управление предметами' },
  { value: 'manage_excuses', label: 'Одобрение пропусков' },
  { value: 'manage_stats', label: 'Редактирование статистики' },
]

interface Props {
  open: boolean
  onClose: () => void
  candidates: GroupMember[]
}

export function AddAssistantModal({ open, onClose, candidates }: Props) {
  const [selectedStudentId, setSelectedStudentId] = useState<number | ''>('')
  const [selectedPermissions, setSelectedPermissions] = useState<AssistantPermission[]>([])
  const createAssistant = useCreateAssistant()

  function handlePermissionToggle(permission: AssistantPermission) {
    setSelectedPermissions((prev) =>
      prev.includes(permission)
        ? prev.filter((p) => p !== permission)
        : [...prev, permission],
    )
  }

  function handleSave() {
    if (!selectedStudentId) return
    createAssistant.mutate(
      { studentId: Number(selectedStudentId), permissions: selectedPermissions },
      {
        onSuccess: () => {
          setSelectedStudentId('')
          setSelectedPermissions([])
          onClose()
        },
      },
    )
  }

  function handleClose() {
    setSelectedStudentId('')
    setSelectedPermissions([])
    onClose()
  }

  const errorStatus = createAssistant.isError
    ? ((createAssistant.error as any)?.response?.status ?? 500)
    : null

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
              <h2 className="text-base font-semibold">Добавить помощника</h2>
              <button onClick={handleClose} aria-label="Закрыть">
                <X size={20} />
              </button>
            </div>

            {/* Student selector */}
            <div className="mb-4">
              <label className="block text-sm font-medium mb-1" htmlFor="assistant-select">
                Студент
              </label>
              <select
                id="assistant-select"
                className="w-full rounded-md px-3 py-2 text-sm"
                style={{
                  background: 'var(--bg-primary, #fff)',
                  border: '1px solid var(--border-subtle)',
                }}
                value={selectedStudentId}
                onChange={(e) =>
                  setSelectedStudentId(e.target.value ? Number(e.target.value) : '')
                }
              >
                <option value="">Выберите студента</option>
                {candidates.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.fullName}
                  </option>
                ))}
              </select>
            </div>

            {/* Permissions */}
            <div className="mb-4">
              <p className="text-sm font-medium mb-2">Права</p>
              <div className="flex flex-col gap-2">
                {PERMISSION_LABELS.map(({ value, label }) => (
                  <label key={value} className="flex items-center gap-2 text-sm cursor-pointer">
                    <input
                      type="checkbox"
                      checked={selectedPermissions.includes(value)}
                      onChange={() => handlePermissionToggle(value)}
                      aria-label={label}
                    />
                    {label}
                  </label>
                ))}
              </div>
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
                disabled={!selectedStudentId || createAssistant.isPending}
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
