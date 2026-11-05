import { onScopeDispose, ref, watch } from 'vue'
import type { StudentApi } from '../../api/student-client'
import type { StudentAttendanceDocument } from '../../api/types'
import { ServerAttachmentPreview, type ServerAttachmentPreviewState } from '../requests/server-attachment-preview'
import type { AttendanceDocumentResolver, AttendanceLessonDocument, AttendanceViewModel } from './attendance-view-model'

/** Lazy authenticated previews reuse the existing signature, expiry and URL lifecycle owner. */
export function useAttendanceDocuments(scope: () => {
  api: StudentApi | null; ownerKey: string; semesterId: string | null;
  allowed: boolean; offline: boolean; data: AttendanceViewModel | null
}): AttendanceDocumentResolver {
  const revision = ref(0)
  const entries = new Map<string, { preview: ServerAttachmentPreview; abort: AbortController; state: ServerAttachmentPreviewState }>()
  let generation = 0
  function clear(): void {
    generation += 1
    for (const value of entries.values()) { value.abort.abort(); value.preview.dispose() }
    entries.clear()
    revision.value += 1
  }
  watch(() => [scope().api, scope().ownerKey, scope().semesterId, scope().allowed, scope().offline,
    JSON.stringify(scope().data?.days.flatMap(day => day.lessons.map(lesson => [lesson.id, lesson.status, lesson.explanation?.documents])))],
  clear, { flush: 'sync' })
  onScopeDispose(clear)

  function document(lessonId: string, file: StudentAttendanceDocument): AttendanceLessonDocument {
    const current = scope()
    const key = `${generation}:${lessonId}:${file.id}:${file.markRevision}`
    let entry = entries.get(key)
    if (!entry) {
      const abort = new AbortController()
      const captured = current
      const capturedGeneration = generation
      const api = { downloadAttachment: () => captured.api!.downloadAttendanceAttachment(
        captured.semesterId!, lessonId, file.id, file.markRevision, abort.signal) }
      const owner = () => ({ api, requestId: lessonId,
        attachment: { ...file, state: file.state === 'ACTIVE' ? 'AVAILABLE' : file.state,
          sha256: null, uploadedAt: null, downloadUrl: null, expiresAt: file.expiresAt ?? null },
        ownerKey: `${scope().ownerKey}:${generation}:${file.markRevision}`,
        allowed: scope().allowed && scope().api === captured.api && generation === capturedGeneration,
        offline: scope().offline })
      const initial: ServerAttachmentPreviewState = { status: 'idle', url: null, mime: null, error: null }
      const value: { abort: AbortController; state: ServerAttachmentPreviewState; preview: ServerAttachmentPreview } =
        { abort, state: initial, preview: null as unknown as ServerAttachmentPreview }
      value.preview = new ServerAttachmentPreview(owner, state => { value.state = state; revision.value += 1 })
      entry = value
      entries.set(key, entry)
    }
    const state = entry.state
    const validExpiry = file.expiresAt && Number.isFinite(Date.parse(file.expiresAt)) && Date.parse(file.expiresAt) > Date.now()
    return { label: file.name, open: () => {
      const url = entry!.preview.openUrl()
      if (url && typeof globalThis.document !== 'undefined') {
        const link = globalThis.document.createElement('a')
        link.href = url
        link.download = file.name
        link.rel = 'noopener'
        link.click()
      } else if (entry!.state.status !== 'loading') void entry!.preview.load()
    },
      pending: state.status === 'loading',
      unavailable: !current.allowed || current.offline || file.state !== 'ACTIVE' || !validExpiry,
      error: state.status === 'error' ? state.error : null,
      ...(state.status === 'ready' ? { preview: { src: state.url, kind: state.mime === 'application/pdf' ? 'pdf' as const : 'image' as const } } : {}) }
  }
  return (lessonId, explanation) => {
    void revision.value
    const current = scope()
    const lesson = current.data?.days.flatMap(day => day.lessons).find(value => value.id === lessonId)
    if (!explanation && lesson?.status !== 'EXCUSED') return []
    return ((explanation ?? lesson?.explanation)?.documents ?? []).map(file => document(file.lessonId, file))
  }
}
