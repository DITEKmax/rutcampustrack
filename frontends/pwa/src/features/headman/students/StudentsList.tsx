import { useState } from 'react'
import { Link } from 'react-router'
import { ArrowLeft, Plus, Trash } from '@phosphor-icons/react'
import {
  useGroupMembers,
  useDeleteAssistant,
} from '@/features/headman/shared/headmanApi'
import type { GroupMember } from '@/features/headman/shared/types'
import { AddAssistantModal } from './AddAssistantModal'

function MemberSkeleton() {
  return (
    <div className="flex flex-col gap-2">
      {[1, 2, 3, 4].map((i) => (
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

interface MemberRowProps {
  member: GroupMember
  onDelete?: (id: number) => void
  showDelete?: boolean
}

function MemberRow({ member, onDelete, showDelete }: MemberRowProps) {
  const initial = member.fullName.charAt(0).toUpperCase()
  return (
    <div
      className="flex items-center gap-3 rounded-lg px-3"
      style={{
        minHeight: 56,
        paddingTop: 12,
        paddingBottom: 12,
        background: 'var(--bg-secondary)',
        marginBottom: 8,
        border: '1px solid var(--border-subtle)',
      }}
    >
      {/* Avatar */}
      <div
        className="flex items-center justify-center rounded-full text-sm font-bold text-white flex-shrink-0"
        style={{
          width: 40,
          height: 40,
          background: 'var(--accent-secondary, #6366f1)',
        }}
      >
        {initial}
      </div>

      {/* Info */}
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold truncate">{member.fullName}</p>
        <p className="text-xs truncate" style={{ color: 'var(--text-muted)' }}>
          {member.login}
        </p>
      </div>

      {/* Badges */}
      <div className="flex items-center gap-2 flex-shrink-0">
        {member.isHeadman && (
          <span
            className="text-xs font-medium px-2 py-0.5 rounded-full"
            style={{
              background: 'var(--accent-primary)',
              color: 'white',
            }}
          >
            Староста
          </span>
        )}
        {member.isAssistant && (
          <span
            className="text-xs font-medium px-2 py-0.5 rounded-full"
            style={{
              background: 'var(--accent-info, #3b82f6)',
              color: 'white',
            }}
          >
            Помощник
          </span>
        )}
        {showDelete && onDelete && (
          <button
            aria-label="Удалить помощника"
            onClick={() => onDelete(member.id)}
            style={{ color: 'var(--accent-danger, #ef4444)' }}
          >
            <Trash size={20} />
          </button>
        )}
      </div>
    </div>
  )
}

interface DeleteConfirmProps {
  onConfirm: () => void
  onCancel: () => void
}

function DeleteConfirmDialog({ onConfirm, onCancel }: DeleteConfirmProps) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center"
      style={{ background: 'rgba(0,0,0,0.4)' }}
    >
      <div
        className="rounded-lg p-6 w-full max-w-sm mx-4"
        style={{ background: 'var(--bg-secondary)' }}
      >
        <p className="text-base font-semibold mb-4">Убрать помощника?</p>
        <p className="text-sm mb-6" style={{ color: 'var(--text-muted)' }}>
          Студент потеряет права помощника старосты.
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

export function StudentsList() {
  const [modalOpen, setModalOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null)
  const membersQuery = useGroupMembers()
  const deleteAssistant = useDeleteAssistant()

  const members = membersQuery.data ?? []
  const assistants = members.filter((m) => m.isAssistant)
  // candidates: non-headman, non-assistant members
  const candidates = members.filter((m) => !m.isHeadman && !m.isAssistant)

  function handleDeleteClick(memberId: number) {
    setDeleteTarget(memberId)
  }

  function handleDeleteConfirm() {
    if (deleteTarget !== null) {
      deleteAssistant.mutate(deleteTarget)
      setDeleteTarget(null)
    }
  }

  return (
    <div className="p-6 pb-28">
      <Link to="/group" aria-label="Назад" className="inline-flex items-center gap-2 mb-4">
        <ArrowLeft size={20} />
        <span>Назад</span>
      </Link>
      <h1 className="text-base font-semibold mb-6">Студенты</h1>

      {membersQuery.isLoading ? (
        <MemberSkeleton />
      ) : (
        <>
          {/* Full member list */}
          <div>
            {members.map((m) => (
              <MemberRow key={m.id} member={m} />
            ))}
          </div>

          {/* Assistants section */}
          {assistants.length > 0 && (
            <div className="mt-6">
              <h2 className="text-base font-semibold mb-3">Помощники старосты</h2>
              {assistants.map((m) => (
                <MemberRow
                  key={m.id}
                  member={m}
                  showDelete
                  onDelete={handleDeleteClick}
                />
              ))}
            </div>
          )}
        </>
      )}

      {/* Floating action button */}
      <button
        aria-label="Добавить помощника"
        className="fixed bottom-20 right-6 z-40 flex items-center justify-center rounded-full text-white shadow-lg"
        style={{
          width: 56,
          height: 56,
          background: 'var(--accent-primary)',
        }}
        onClick={() => setModalOpen(true)}
      >
        <Plus size={24} weight="fill" />
      </button>

      {/* Add assistant modal */}
      <AddAssistantModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        candidates={candidates}
      />

      {/* Delete confirmation dialog */}
      {deleteTarget !== null && (
        <DeleteConfirmDialog
          onConfirm={handleDeleteConfirm}
          onCancel={() => setDeleteTarget(null)}
        />
      )}
    </div>
  )
}
