import { afterEach, expect, it, vi } from 'vitest'
import { effectScope, shallowReactive } from 'vue'
import type { StudentApi } from '../../api/student-client'
import type { StudentAttendanceDocument } from '../../api/types'
import { useAttendanceDocuments } from './use-attendance-documents'
import { attendanceDocuments, type AttendanceViewModel } from './attendance-view-model'

afterEach(() => vi.restoreAllMocks())
const file = (id: string, revision = 'mark-1'): StudentAttendanceDocument => ({
  id, name: `${id}.pdf`, contentType: 'application/pdf', size: 8, state: 'ACTIVE',
  expiresAt: '2099-01-01T00:00:00Z', markRevision: revision, lessonId: '11',
})

it('keeps all real files, uses physical lesson/revision, and releases previews on owner/offline changes', async () => {
  const create = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:own-proof')
  const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  const download = vi.fn<StudentApi["downloadAttendanceAttachment"]>(async () => new Blob(['%PDF-1.7'], { type: 'application/pdf' }))
  const state = shallowReactive({ api: { downloadAttendanceAttachment: download } as unknown as StudentApi,
    ownerKey: 'student-owner', semesterId: '7', allowed: true, offline: false, data: null as AttendanceViewModel | null })
  const scope = effectScope()
  const resolve = scope.run(() => useAttendanceDocuments(() => state))!
  const explanation = { documents: [file('first'), file('second')] }
  const refs = attendanceDocuments(resolve('101', explanation))
  expect(refs).toHaveLength(2)
  refs[1]!.open()
  await vi.waitFor(() => expect(create).toHaveBeenCalledOnce())
  expect(download.mock.calls[0]?.slice(0, 4)).toEqual(['7', '11', 'second', 'mark-1'])
  expect(attendanceDocuments(resolve('101', explanation))[1]?.preview?.kind).toBe('pdf')
  state.data = { days: [{ lessons: [{ id: '11', status: 'EXCUSED', explanation: { documents: [file('second', 'mark-2')] } }] }] } as unknown as AttendanceViewModel
  expect(revoke).toHaveBeenCalledWith('blob:own-proof')
  state.offline = true
  expect(revoke).toHaveBeenCalledWith('blob:own-proof')
  expect(attendanceDocuments(resolve('101', explanation))[0]?.unavailable).toBe(true)
  state.offline = false
  state.ownerKey = 'new-student-owner'
  refs[0]!.open()
  expect(download).toHaveBeenCalledOnce()
  scope.stop()
})

it('aborts and fences an old owner response before it can create a URL', async () => {
  const create = vi.spyOn(URL, 'createObjectURL')
  let complete!: (blob: Blob) => void
  const download = vi.fn<StudentApi["downloadAttendanceAttachment"]>(() => new Promise<Blob>(resolve => { complete = resolve }))
  const state = shallowReactive({ api: { downloadAttendanceAttachment: download } as unknown as StudentApi,
    ownerKey: 'owner-1', semesterId: '7', allowed: true, offline: false, data: null })
  const scope = effectScope()
  const resolve = scope.run(() => useAttendanceDocuments(() => state))!
  attendanceDocuments(resolve('101', { documents: [file('proof')] }))[0]!.open()
  const signal = download.mock.calls[0]?.[4] as AbortSignal | undefined
  state.ownerKey = 'owner-2'
  expect(signal?.aborted).toBe(true)
  complete(new Blob(['%PDF-1.7'], { type: 'application/pdf' }))
  await Promise.resolve()
  await Promise.resolve()
  expect(create).not.toHaveBeenCalled()
  expect(attendanceDocuments(resolve('101', { documents: [] }))).toEqual([])
  scope.stop()
})
