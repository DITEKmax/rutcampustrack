import { useState } from 'react'
import { Link } from 'react-router'
import { ArrowLeft, Plus, Pencil, Trash } from '@phosphor-icons/react'
import {
  useGroupSubjects,
  useDeleteSubject,
} from '@/features/headman/shared/headmanApi'
import type { Subject } from '@/features/headman/shared/types'
import { SubjectFormModal } from './SubjectFormModal'

interface ModalState {
  open: boolean
  mode: 'create' | 'edit'
  subject?: Subject
}

interface DeleteConfirmProps {
  subjectName: string
  onConfirm: () => void
  onCancel: () => void
}

function DeleteConfirmDialog({ subjectName, onConfirm, onCancel }: DeleteConfirmProps) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center"
      style={{ background: 'rgba(0,0,0,0.4)' }}
    >
      <div
        className="rounded-lg p-6 w-full max-w-sm mx-4"
        style={{ background: 'var(--bg-secondary)' }}
      >
        <p className="text-base font-semibold mb-2">Удалить этот предмет?</p>
        <p className="text-sm mb-6" style={{ color: 'var(--text-muted)' }}>
          «{subjectName}» будет удалён без возможности восстановления.
        </p>
        <div className="flex gap-3">
          <button
            className="flex-1 rounded-lg px-4 py-2 text-sm font-medium text-white"
            style={{ background: 'var(--accent-danger, #ef4444)' }}
            onClick={onConfirm}
          >
            Удалить
          </button>
          <button
            className="flex-1 rounded-lg px-4 py-2 text-sm font-medium"
            style={{ border: '1px solid var(--border-subtle)' }}
            onClick={onCancel}
          >
            Отмена
          </button>
        </div>
      </div>
    </div>
  )
}

function SubjectSkeleton() {
  return (
    <div className="flex flex-col gap-2">
      {[1, 2, 3].map((i) => (
        <div
          key={i}
          className="rounded-lg animate-pulse"
          style={{
            height: 56,
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border-subtle)',
          }}
        />
      ))}
    </div>
  )
}

export function SubjectsList() {
  const [modal, setModal] = useState<ModalState>({ open: false, mode: 'create' })
  const [deleteTarget, setDeleteTarget] = useState<Subject | null>(null)

  const subjectsQuery = useGroupSubjects()
  const deleteSubject = useDeleteSubject()

  const subjects = subjectsQuery.data ?? []

  function openCreate() {
    setModal({ open: true, mode: 'create' })
  }

  function openEdit(subject: Subject) {
    setModal({ open: true, mode: 'edit', subject })
  }

  function closeModal() {
    setModal((prev) => ({ ...prev, open: false }))
  }

  function handleDeleteConfirm() {
    if (deleteTarget) {
      deleteSubject.mutate(deleteTarget.id)
      setDeleteTarget(null)
    }
  }

  return (
    <div className="p-6 pb-28">
      <Link to="/group" aria-label="Назад" className="inline-flex items-center gap-2 mb-4">
        <ArrowLeft size={20} />
        <span>Назад</span>
      </Link>
      <h1 className="text-base font-semibold mb-6">Предметы</h1>

      {subjectsQuery.isLoading ? (
        <SubjectSkeleton />
      ) : subjects.length === 0 ? (
        <p className="text-center text-sm" style={{ color: 'var(--text-muted)' }}>
          У группы пока нет предметов
        </p>
      ) : (
        <div>
          {subjects.map((subject) => (
            <div
              key={subject.id}
              className="flex items-center gap-3 rounded-lg px-3 mb-2"
              style={{
                minHeight: 56,
                paddingTop: 12,
                paddingBottom: 12,
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-subtle)',
              }}
              role="button"
              onClick={() => openEdit(subject)}
            >
              {/* Subject info */}
              <div className="flex-1 min-w-0">
                <p className="text-base font-semibold truncate">{subject.name}</p>
                {subject.teacherName && (
                  <p className="text-sm truncate" style={{ color: 'var(--text-muted)' }}>
                    {subject.teacherName}
                  </p>
                )}
              </div>

              {/* Action icons */}
              <div
                className="flex items-center gap-2 flex-shrink-0"
                onClick={(e) => e.stopPropagation()}
              >
                <button
                  aria-label="Редактировать предмет"
                  onClick={() => openEdit(subject)}
                  style={{ color: 'var(--text-muted)' }}
                >
                  <Pencil size={20} />
                </button>
                <button
                  aria-label="Удалить предмет"
                  onClick={() => setDeleteTarget(subject)}
                  style={{ color: 'var(--accent-danger, #ef4444)' }}
                >
                  <Trash size={20} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Floating action button */}
      <button
        aria-label="Создать предмет"
        className="fixed bottom-20 right-6 z-40 flex items-center justify-center rounded-full text-white shadow-lg"
        style={{
          width: 56,
          height: 56,
          background: 'var(--accent-primary)',
        }}
        onClick={openCreate}
      >
        <Plus size={24} weight="fill" />
      </button>

      {/* Create/Edit modal */}
      <SubjectFormModal
        open={modal.open}
        mode={modal.mode}
        subject={modal.subject}
        onClose={closeModal}
      />

      {/* Delete confirmation */}
      {deleteTarget && (
        <DeleteConfirmDialog
          subjectName={deleteTarget.name}
          onConfirm={handleDeleteConfirm}
          onCancel={() => setDeleteTarget(null)}
        />
      )}
    </div>
  )
}
