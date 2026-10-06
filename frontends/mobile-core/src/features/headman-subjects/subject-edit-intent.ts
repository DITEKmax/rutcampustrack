import type { HeadmanSubject, HeadmanSubjectInitialAssignment, HeadmanSubjectType, HeadmanPendingClosure } from './headman-subjects-client'
export interface SubjectRemovalIntent {
  readonly subjectId: number; readonly teacherId: number; readonly assignmentId: number
  readonly validUntilExclusive: string; readonly requestKey: string
  readonly operationId?: string; readonly groupId?: number; readonly semesterId?: number; readonly lessonType?: HeadmanSubjectType
}
export interface SubjectAdoptionHandle { readonly targetAssignmentId: number; readonly adoptionOperationId: string | null }
export interface SubjectEditIntent {
  readonly version: 1; readonly actorUserId: string; readonly groupId: number; readonly editId: string
  readonly subjectId: number | null; readonly createdId: number | null; readonly name: string
  readonly lessonTypes: readonly HeadmanSubjectType[]; readonly assignments: readonly HeadmanSubjectInitialAssignment[]
  readonly removals?: readonly SubjectRemovalIntent[]; readonly pendingAdoptions?: readonly SubjectAdoptionHandle[]
  readonly closureOnly?: boolean
  readonly rejected?: boolean
  readonly blockedReason?: string | null
  readonly step: number; readonly unknown: boolean
}
export function subjectEditStorageKey(actor: string, group: number): string { return `rct:headman-subject-edit:v1:${encodeURIComponent(actor)}:${group}` }
export function parseSubjectEditIntent(raw: string, actor: string, group: number): SubjectEditIntent | null {
  try {
    const value: unknown = JSON.parse(raw)
    if (!value || typeof value !== 'object') return null
    const v = value as Record<string, unknown>
    const positive = (id: unknown): id is number => typeof id === 'number' && Number.isSafeInteger(id) && id > 0
    const date = (s: unknown): s is string => typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && new Date(`${s}T00:00:00Z`).toISOString().slice(0, 10) === s
    const type = (s: unknown): s is HeadmanSubjectType => ['LECTURE', 'PRACTICE', 'LAB'].includes(String(s))
    if (v.version !== 1 || v.actorUserId !== actor || v.groupId !== group || typeof v.editId !== 'string' || !v.editId
      || (v.subjectId !== null && !positive(v.subjectId)) || (v.createdId !== null && !positive(v.createdId))
      || typeof v.name !== 'string' || !v.name.trim() || !Array.isArray(v.lessonTypes) || !v.lessonTypes.length
      || !v.lessonTypes.every(type) || new Set(v.lessonTypes).size !== v.lessonTypes.length
      || !Array.isArray(v.assignments) || typeof v.unknown !== 'boolean' || typeof v.step !== 'number'
      || !Number.isSafeInteger(v.step) || v.step < 0 || v.step > (v.subjectId === null ? 1 : v.assignments.length + (Array.isArray(v.removals) ? v.removals.length : 0) + 1)) return null
    for (const assignment of v.assignments) {
      if (!assignment || typeof assignment !== 'object') return null
      const a = assignment as Record<string, unknown>
      if (!positive(a.teacherId) || !positive(a.semesterId) || !type(a.lessonType) || !v.lessonTypes.includes(a.lessonType)
        || !date(a.validFrom) || (a.validUntilExclusive !== null && (!date(a.validUntilExclusive) || a.validUntilExclusive <= a.validFrom))) return null
    }
    const validUuid = (key: unknown): key is string => typeof key === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(key)
    if (v.removals !== undefined) {
      if (!Array.isArray(v.removals) || (v.subjectId === null && v.removals.length)) return null
      for (const removal of v.removals) {
        if (!removal || typeof removal !== 'object') return null
        const r = removal as Record<string, unknown>
        if (r.operationId !== undefined && (!validUuid(r.operationId) || r.groupId !== group || !positive(r.semesterId) || !type(r.lessonType))) return null
        if (r.subjectId !== v.subjectId || !positive(r.teacherId) || !positive(r.assignmentId) || !date(r.validUntilExclusive) || !validUuid(r.requestKey)) return null
      }
    }
    if (v.pendingAdoptions !== undefined) {
      if (!Array.isArray(v.pendingAdoptions)) return null
      for (const handle of v.pendingAdoptions) {
        if (!handle || typeof handle !== 'object') return null
        const h = handle as Record<string, unknown>
        if (!positive(h.targetAssignmentId) || (h.adoptionOperationId !== null && !validUuid(h.adoptionOperationId))) return null
      }
    }
    if (v.closureOnly !== undefined && (typeof v.closureOnly !== 'boolean' || (v.closureOnly && (v.subjectId === null || v.assignments.length !== 0 || !Array.isArray(v.removals) || v.removals.length !== 1 || v.step < 1 || !v.removals[0]?.operationId)))) return null
    if (v.rejected !== undefined && (typeof v.rejected !== 'boolean' || (v.rejected && (v.unknown || v.subjectId === null || v.step === 0 || v.step <= (Array.isArray(v.removals) ? v.removals.length : 0))))) return null
    if (v.blockedReason !== undefined && v.blockedReason !== null && typeof v.blockedReason !== 'string') return null
    return v as unknown as SubjectEditIntent
  } catch { return null }
}
export function subjectEditStepConfirmed(intent: SubjectEditIntent, subjects: readonly HeadmanSubject[]): boolean {
  const subject = subjects.find((item) => item.id === (intent.subjectId ?? intent.createdId) && item.groupId === intent.groupId)
  if (!subject) return false
  if (intent.subjectId !== null && intent.step === 0) return subject.name === intent.name
    && subject.lessonTypes.length === intent.lessonTypes.length && intent.lessonTypes.every((type) => subject.lessonTypes.includes(type))
  // An assignment read proves admission, never distributed adoption COMMITTED.
  return false
}

export function subjectEditTotal(intent: SubjectEditIntent): number { return intent.subjectId === null ? 1 : 1 + (intent.removals?.length ?? 0) + intent.assignments.length }
export function subjectEditRemoval(intent: SubjectEditIntent): SubjectRemovalIntent | null { return intent.subjectId === null || intent.step === 0 ? null : intent.removals?.[intent.step - 1] ?? null }
export function subjectEditAssignment(intent: SubjectEditIntent): HeadmanSubjectInitialAssignment | null { return intent.subjectId === null || intent.step === 0 ? null : intent.assignments[intent.step - 1 - (intent.removals?.length ?? 0)] ?? null }
export function subjectEditResultConfirmed(intent: SubjectEditIntent, subjects: readonly HeadmanSubject[]): boolean {
  const subject = subjects.find((item) => item.id === (intent.subjectId ?? intent.createdId) && item.groupId === intent.groupId)
  return Boolean(subject && (intent.closureOnly || (subject.name === intent.name && subject.lessonTypes.length === intent.lessonTypes.length && intent.lessonTypes.every((type) => subject.lessonTypes.includes(type))))
    && intent.assignments.every((target) => subject.assignments.some((a) => a.teacherId === target.teacherId && a.semesterId === target.semesterId && a.lessonType === target.lessonType && a.validFrom === target.validFrom && a.validUntilExclusive === target.validUntilExclusive))
    && (intent.removals ?? []).every((r) => !subject.assignments.some((a) => a.id === r.assignmentId && (a.validUntilExclusive === null || a.validUntilExclusive > r.validUntilExclusive))))
}

export function subjectEditPrefixConfirmed(intent: SubjectEditIntent, subject: HeadmanSubject): boolean {
  if (intent.subjectId === null || intent.step < 1) return false
  const completed = intent.step - 1, removals = intent.removals ?? []
  return subjectEditResultConfirmed({ ...intent, removals: removals.slice(0, completed), assignments: intent.assignments.slice(0, Math.max(0, completed - removals.length)) }, [subject])
}

export function createSubjectClosureIntent(actorUserId: string, groupId: number, subject: HeadmanSubject, handle: HeadmanPendingClosure): SubjectEditIntent | null {
  if (!actorUserId || subject.groupId !== groupId || handle.groupId !== groupId || handle.subjectId !== subject.id || !subject.lessonTypes.includes(handle.lessonType)) return null
  const plan: SubjectEditIntent = { version: 1, actorUserId, groupId, editId: `closure:${handle.operationId}`, subjectId: subject.id, createdId: null, name: subject.name, lessonTypes: subject.lessonTypes,
    assignments: [], removals: [{ ...handle }], pendingAdoptions: [], step: 1, unknown: true, closureOnly: true }
  return parseSubjectEditIntent(JSON.stringify(plan), actorUserId, groupId)
}
