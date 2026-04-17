import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import {
  BookOpen,
  Link as LinkIcon,
  Pencil,
  Plus,
  Trash,
} from '@phosphor-icons/react'
import { cn } from '@/lib/utils'
import { useDeleteHomework } from './api'
import { HomeworkInlineForm } from './HomeworkInlineForm'
import type { HomeworkResponse } from './types'

interface Props {
  /** All homeworks for the displayed week; component filters by lesson context. */
  homeworks: HomeworkResponse[]
  groupId: number
  semesterId: number | null
  subjectId: number
  lessonDate: string   // YYYY-MM-DD
  lessonNumber: number
  isCancelled: boolean
  /** Whether the current user can create/edit/delete homeworks on this lesson. */
  canManage: boolean
  /** Current user id — used to gate per-item edit/delete (D-05). */
  currentUserId: number | undefined
}

/**
 * Per-lesson homework strip rendered under a LessonCard on /schedule.
 *
 * Student view: read-only list of ДЗ (title + optional link). Tapping the
 * row does nothing — checkbox-toggle lives on /homework, here the schedule
 * view stays focused on the lesson itself.
 *
 * Headman view: adds edit/delete icons for own ДЗ (hw.publishedBy ===
 * currentUserId) and a "+ Добавить ДЗ" affordance that expands an inline
 * form below the list.
 */
export function LessonHomeworkSection({
  homeworks,
  groupId,
  semesterId,
  subjectId,
  lessonDate,
  lessonNumber,
  isCancelled,
  canManage,
  currentUserId,
}: Props) {
  const [editingId, setEditingId] = useState<number | 'new' | null>(null)
  const deleteMut = useDeleteHomework()

  const items = homeworks
    .filter(
      (hw) =>
        hw.lessonDate === lessonDate && hw.lessonNumber === lessonNumber,
    )
    .sort((a, b) => a.id - b.id)

  const nothing = items.length === 0
  // Cancelled lessons don't accept new homework and hide the add button.
  const showAddButton = canManage && !isCancelled
  // Nothing to render at all for students when lesson has no homework.
  if (!canManage && nothing) return null

  const handleDelete = (hw: HomeworkResponse) => {
    if (!confirm(`Удалить задание «${hw.title}»?`)) return
    void deleteMut.mutateAsync(hw.id)
  }

  return (
    <section
      className="flex flex-col gap-2"
      aria-label="Домашние задания на пару"
    >
      {!nothing && (
        <ul className="flex flex-col gap-1.5">
          {items.map((hw) => {
            const isOwn =
              currentUserId !== undefined && hw.publishedBy === currentUserId
            const isEditing = editingId === hw.id
            return (
              <li key={hw.id}>
                <div
                  className={cn(
                    'flex items-start gap-2 rounded-lg border px-2.5 py-1.5',
                    'text-xs',
                  )}
                  style={{
                    background:
                      'color-mix(in oklab, var(--accent-primary) 6%, var(--bg-secondary))',
                    borderColor: 'var(--border-subtle)',
                  }}
                >
                  <BookOpen
                    size={14}
                    weight="duotone"
                    aria-hidden="true"
                    className="mt-0.5 shrink-0"
                    style={{ color: 'var(--accent-primary)' }}
                  />
                  <div className="min-w-0 flex-1">
                    <p
                      className="font-medium leading-snug"
                      style={{ color: 'var(--text-primary)' }}
                    >
                      {hw.title}
                    </p>
                    {hw.link && (
                      <a
                        href={hw.link}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 text-[11px]"
                        style={{ color: 'var(--accent-primary)' }}
                      >
                        <LinkIcon size={10} weight="bold" />
                        Ссылка
                      </a>
                    )}
                  </div>
                  {canManage && isOwn && !isCancelled && (
                    <div className="flex shrink-0 gap-1">
                      <button
                        type="button"
                        onClick={() =>
                          setEditingId(isEditing ? null : hw.id)
                        }
                        aria-label="Редактировать задание"
                        className="grid size-7 place-items-center rounded-full"
                        style={{ color: 'var(--text-secondary)' }}
                      >
                        <Pencil size={12} weight="bold" />
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(hw)}
                        aria-label="Удалить задание"
                        className="grid size-7 place-items-center rounded-full"
                        style={{ color: 'var(--accent-danger)' }}
                      >
                        <Trash size={12} weight="bold" />
                      </button>
                    </div>
                  )}
                </div>
                <AnimatePresence>
                  {isEditing && semesterId !== null && (
                    <HomeworkInlineForm
                      mode="edit"
                      groupId={groupId}
                      semesterId={semesterId}
                      subjectId={subjectId}
                      lessonDate={lessonDate}
                      lessonNumber={lessonNumber}
                      initial={hw}
                      onDone={() => setEditingId(null)}
                      onCancel={() => setEditingId(null)}
                    />
                  )}
                </AnimatePresence>
              </li>
            )
          })}
        </ul>
      )}

      {showAddButton && semesterId !== null && (
        <>
          {editingId !== 'new' && (
            <motion.button
              type="button"
              layout
              onClick={() => setEditingId('new')}
              className={cn(
                'inline-flex items-center justify-center gap-1.5 rounded-lg border border-dashed',
                'px-3 py-1.5 text-xs font-medium',
              )}
              style={{
                background: 'transparent',
                borderColor: 'var(--accent-primary)',
                color: 'var(--accent-primary)',
              }}
            >
              <Plus size={12} weight="bold" />
              Добавить ДЗ
            </motion.button>
          )}
          <AnimatePresence>
            {editingId === 'new' && (
              <HomeworkInlineForm
                mode="create"
                groupId={groupId}
                semesterId={semesterId}
                subjectId={subjectId}
                lessonDate={lessonDate}
                lessonNumber={lessonNumber}
                onDone={() => setEditingId(null)}
                onCancel={() => setEditingId(null)}
              />
            )}
          </AnimatePresence>
        </>
      )}
    </section>
  )
}
