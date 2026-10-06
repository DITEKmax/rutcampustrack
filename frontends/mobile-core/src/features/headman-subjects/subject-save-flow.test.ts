import { describe, expect, it, vi } from 'vitest'
import { HeadmanSubjectsApi, type HeadmanSubject } from './headman-subjects-client'
import { createSubjectSaveFlow } from './subject-save-flow'
import { parseSubjectEditIntent, createSubjectClosureIntent, type SubjectEditIntent } from './subject-edit-intent'
const key = '11111111-1111-4111-8111-111111111111', operation = '22222222-2222-4222-8222-222222222222'
const assignment = { teacherId: 103, semesterId: 1, lessonType: 'LECTURE' as const, validFrom: '2026-10-06', validUntilExclusive: null }
function plan(overrides: Partial<SubjectEditIntent> = {}): SubjectEditIntent { return { version: 1, actorUserId: '10', groupId: 4, editId: key, subjectId: 1, createdId: null, name: 'Новый заголовок', lessonTypes: ['LECTURE'], assignments: [assignment], removals: [], pendingAdoptions: [], step: 0, unknown: false, ...overrides } }
function subject(): HeadmanSubject { return { id: 1, name: 'Новый заголовок', type: 'LECTURE', groupId: 4, lessonTypes: ['LECTURE'], teacherIds: [], assignments: [], pendingAdoptions: [] } }
function json(value: unknown, status = 200) { return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } }) }
function fixture(initial: SubjectEditIntent | null, handle: (url: URL, init: RequestInit | undefined) => Promise<Response> | Response) {
  const state = { intent: initial as SubjectEditIntent | null, subjects: [subject()] as HeadmanSubject[], snapshot: [subject()] as HeadmanSubject[], actor: '10', allowed: true, completed: false, selectedResult: false, cancelled: false, error: null as string | null, reads: 0, storageFails: false, writes: [] as { method: string; path: string; key: string | null; body: unknown }[] }
  const api = new HeadmanSubjectsApi({ accessToken: () => null, fetcher: async (input, init) => {
    const url = new URL(String(input), 'http://test.invalid'), method = init?.method ?? 'GET'
    if (method === 'GET') {
      state.reads++
      const project = (subject: HeadmanSubject) => ({ ...subject, assignments: subject.assignments.filter((a) => !subject.pendingAdoptions?.some((h) => h.targetAssignmentId === a.id && h.state === 'PREPARED')) })
      if (/^\/api\/academic\/subjects\/\d+$/.test(url.pathname)) return json(project(state.subjects.find((subject) => subject.id === Number(url.pathname.split('/').at(-1)))!))
      return json({ _embedded: { subjectResponseList: state.subjects.map((subject) => ({ ...project(subject), pendingAdoptions: undefined, pendingClosures: undefined })) }, page: { number: 0, totalPages: 1 } })
    }
    state.writes.push({ method, path: url.pathname + url.search, key: new Headers(init?.headers).get('Idempotency-Key'), body: init?.body ? JSON.parse(String(init.body)) : null })
    return handle(url, init)
  } })
  const flow = createSubjectSaveFlow({ api: () => api, actor: () => state.actor, group: () => 4, available: () => state.allowed, disposed: () => false, subjects: () => state.snapshot, intent: () => state.intent,
    persist: (next) => { if (state.storageFails) return false; state.intent = next; return true }, read: async () => { state.snapshot = [...await api.listSubjects(4)]; return true }, busy: () => undefined, error: (value) => { state.error = value }, denied: (value) => { state.allowed = !value }, completed: (selectedResult) => { state.completed = true; state.selectedResult = selectedResult === true }, cancelled: () => { state.cancelled = true }, failed: () => undefined })
  return Object.assign(state, { flow })
}
describe('subject save recovery preserves admitted data and operation identity', () => {
  it('stops rejected metadata before additions/removals and releases only the unaccepted draft plan', async () => {
    const f = fixture(plan(), () => json({ detail: 'Тип имеет историю назначений' }, 409))
    await f.flow.send(true)
    expect(f.writes.map((w) => w.method)).toEqual(['PUT']); expect(f.completed).toBe(false); expect(f.intent).toBeNull(); expect(f.error).toContain('Черновик сохранён')
  })
  it('reconciles a lost metadata PUT independently and retries only that frozen PUT before additions', async () => {
    let puts = 0
    const f = fixture(plan(), (url) => {
      if (url.pathname === '/api/academic/subjects/1') { puts++; if (puts === 1) throw new TypeError('lost PUT'); f.subjects[0] = subject(); return json(subject()) }
      f.subjects[0] = { ...subject(), assignments: [{ id: 500, ...assignment }] }; return json({ id: 500 }, 201)
    })
    f.subjects[0] = { ...subject(), name: 'Старый заголовок' }
    await f.flow.send(true); expect(f.intent?.unknown).toBe(true)
    await f.flow.reconcile(); expect(f.flow.canContinue()).toBe(true); expect(f.writes.map((w) => w.method)).toEqual(['PUT'])
    await f.flow.send(); expect(f.writes.map((w) => w.method)).toEqual(['PUT', 'PUT', 'POST']); expect(f.writes[1]).toEqual(f.writes[0]); expect(f.completed).toBe(true)
  })
  it('accepts a matching lost PUT read without repeating PUT while sending the remaining add once', async () => {
    const f = fixture(plan({ unknown: true }), () => { f.subjects[0] = { ...subject(), assignments: [{ id: 500, ...assignment }] }; return json({ id: 500 }, 201) })
    await f.flow.reconcile(); await f.flow.send(); expect(f.writes.map((w) => w.method)).toEqual(['POST']); expect(f.completed).toBe(true)
  })
  it('requires explicit user acceptance for a zero-target lost create and never rePOSTs it', async () => {
    const f = fixture(plan({ subjectId: null, assignments: [], unknown: true }), () => { throw new Error('No mutation permitted') })
    await f.flow.reconcile(1); expect(f.completed).toBe(false); expect(f.writes).toHaveLength(0)
    f.subjects[0] = { ...subject(), pendingAdoptions: [{ targetAssignmentId: 500, adoptionOperationId: operation, state: 'PREPARED', ...assignment, subjectId: 1, groupId: 4 }] }
    await f.flow.reconcile(1, true); expect(f.completed).toBe(false)
    f.subjects[0] = subject(); await f.flow.reconcile(1, true)
    expect(f.completed).toBe(true); expect(f.selectedResult).toBe(true); expect(f.writes).toHaveLength(0); expect(f.intent).toBeNull()
  })
  it('retains received create adoption handles across restore and completes only after resume200 and final read', async () => {
    const f = fixture(plan({ subjectId: null }), (url) => {
      if (url.pathname === '/api/academic/subjects') { f.subjects[0] = { ...subject(), teacherIds: [103], assignments: [{ id: 500, ...assignment }], pendingAdoptions: [{ targetAssignmentId: 500, adoptionOperationId: operation, state: 'PREPARED', ...assignment, subjectId: 1, groupId: 4 }] }; return json({ type: 'urn:rct:assignment-adoption-pending', extras: { subjectId: 1, pendingAdoptions: [{ targetAssignmentId: 500, adoptionOperationId: operation }] } }, 503) }
      f.subjects[0] = { ...subject(), teacherIds: [103], assignments: [{ id: 500, ...assignment }] }; return json({ id: 500, ...assignment, subjectId: 1, groupId: 4 })
    })
    await f.flow.send(true); expect(f.completed).toBe(false); expect(f.intent?.createdId).toBe(1)
    f.intent = parseSubjectEditIntent(JSON.stringify(f.intent), '10', 4); f.flow.reset()
    await f.flow.reconcile()
    expect(f.writes.filter((w) => w.path === '/api/academic/subjects')).toHaveLength(1)
    expect(f.writes.at(-1)?.path).toBe(`/api/academic/assignments/500/resume-adoption?operationId=${operation}`)
    expect(f.completed).toBe(true); expect(f.intent).toBeNull(); expect(f.reads).toBeGreaterThanOrEqual(2)
  })
  it('does not infer adoption COMMITTED from an admitted assignment after a lost add response', async () => {
    let resumes = 0
    const f = fixture(plan(), (url) => {
      if (url.pathname.endsWith('/teachers/103')) { f.subjects[0] = { ...subject(), teacherIds: [103], assignments: [{ id: 500, ...assignment }] }; throw new TypeError('response lost') }
      if (url.pathname.endsWith('/resume-adoption')) { resumes++; return json({ id: 500, ...assignment, subjectId: 1, groupId: 4 }) }
      return json(subject())
    })
    await f.flow.send(true); expect(f.intent?.unknown).toBe(true); expect(f.completed).toBe(false)
    await f.flow.reconcile()
    expect(resumes).toBe(1); expect(f.writes.filter((w) => w.path.includes('/teachers/'))).toHaveLength(1); expect(f.completed).toBe(true)
  })
  it('rejects another-group resume tuple and keeps uncertainty despite the matching assignment read', async () => {
    const f = fixture(plan({ step: 1, unknown: true }), () => json({ id: 500, ...assignment, subjectId: 1, groupId: 9 }))
    f.subjects[0] = { ...subject(), assignments: [{ id: 500, ...assignment }] }
    await f.flow.reconcile(); expect(f.completed).toBe(false); expect(f.intent?.unknown).toBe(true); expect(f.error).toContain('другое назначение')
  })
  it('requires explicit attribution of a raw lost create, then confirms exact targets without another create POST', async () => {
    const f = fixture(plan({ subjectId: null, unknown: true }), () => json({ id: 500, ...assignment, subjectId: 1, groupId: 4 }))
    f.subjects[0] = { ...subject(), assignments: [{ id: 500, ...assignment }] }
    await f.flow.reconcile(); expect(f.writes).toHaveLength(0); expect(f.completed).toBe(false)
    await f.flow.reconcile(1); expect(f.completed).toBe(true); expect(f.writes).toHaveLength(1); expect(f.writes[0]?.path).toBe('/api/academic/assignments/500/resume-adoption')
  })
  it('replays unknown removal after Moscow midnight with the identical key/date/assignment', async () => {
    vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-06T20:59:00Z'))
    let deletes = 0
    const removal = { subjectId: 1, teacherId: 103, assignmentId: 500, validUntilExclusive: '2026-10-06', requestKey: key }
    const f = fixture(plan({ assignments: [], removals: [removal] }), (url) => {
      if (url.pathname.includes('/teachers/')) { deletes++; if (deletes === 1) return json({ type: 'urn:rct:assignment-closure-pending', extras: { assignmentId: 500, requestKey: key } }, 503); f.subjects[0] = subject(); return new Response(null, { status: 204 }) }
      return json(f.subjects[0])
    })
    f.subjects[0] = { ...subject(), assignments: [{ id: 500, ...assignment }] }
    await f.flow.send(true); expect(f.intent?.unknown).toBe(true)
    vi.setSystemTime(new Date('2026-10-06T21:01:00Z'))
    f.intent = parseSubjectEditIntent(JSON.stringify(f.intent), '10', 4); f.flow.reset(); await f.flow.reconcile(); await f.flow.send()
    const writes = f.writes.filter((w) => w.method === 'DELETE')
    expect(writes).toHaveLength(2); expect(writes[1]).toEqual(writes[0]); expect(writes[0]?.key).toBe(key); expect(writes[1]?.path).toContain('validUntilExclusive=2026-10-06'); expect(f.completed).toBe(true); vi.useRealTimers()
  })
  it('prevalidates raw-lost PREPARED detail tuples before resume while list hides both target and handle', async () => {
    const f = fixture(plan({ step: 1, unknown: true }), () => { f.subjects[0] = { ...subject(), assignments: [{ id: 500, ...assignment }] }; return json({ id: 500, ...assignment, subjectId: 1, groupId: 4 }) })
    f.subjects[0] = { ...subject(), pendingAdoptions: [{ targetAssignmentId: 500, adoptionOperationId: operation, state: 'PREPARED', ...assignment, subjectId: 1, groupId: 4 }] }
    await f.flow.reconcile(); expect(f.completed).toBe(true); expect(f.writes.map((w) => w.path)).toEqual([`/api/academic/assignments/500/resume-adoption?operationId=${operation}`])
  })
  it('does not resume a lone unrelated PREPARED handle or ambiguous matching handles', async () => {
    const f = fixture(plan({ step: 1, unknown: true }), () => { throw new Error('No resume permitted') })
    const handle = { targetAssignmentId: 500, adoptionOperationId: operation, state: 'PREPARED', ...assignment, subjectId: 1, groupId: 4 }
    f.subjects[0] = { ...subject(), pendingAdoptions: [{ ...handle, teacherId: 999 }] }
    await f.flow.reconcile(); expect(f.writes).toHaveLength(0); expect(f.completed).toBe(false)
    f.subjects[0] = { ...subject(), pendingAdoptions: [handle, { ...handle, targetAssignmentId: 501 }] }
    await f.flow.reconcile(); expect(f.writes).toHaveLength(0); expect(f.intent?.unknown).toBe(true)
    const incomplete = JSON.parse(JSON.stringify(handle)); delete incomplete.validUntilExclusive
    f.subjects[0] = { ...subject(), pendingAdoptions: [incomplete] }
    await f.flow.reconcile(); expect(f.writes).toHaveLength(0); expect(f.completed).toBe(false); expect(f.error).toContain('исходную границу')
  })
  it('releases definitive CREATE409 back to the untouched local draft with no adopted receipt', async () => {
    const f = fixture(plan({ subjectId: null }), () => json({ detail: 'atomic creation rejected' }, 409))
    await f.flow.send(true); expect(f.intent).toBeNull(); expect(f.completed).toBe(false); expect(f.writes.map((w) => w.method)).toEqual(['POST'])
  })
  it('cancels only rejected remaining add after fresh prefix confirmation and never undoes confirmed metadata', async () => {
    const f = fixture(plan(), (url) => url.pathname.includes('/teachers/') ? json({ detail: 'add rejected' }, 409) : json(subject()))
    await f.flow.send(true); expect(f.intent?.step).toBe(1); expect(f.intent?.rejected).toBe(true)
    f.intent = parseSubjectEditIntent(JSON.stringify(f.intent), '10', 4); f.flow.reset()
    await f.flow.cancelRejected(); expect(f.cancelled).toBe(true); expect(f.intent).toBeNull(); expect(f.subjects[0]?.name).toBe('Новый заголовок'); expect(f.writes.map((w) => w.method)).toEqual(['PUT', 'POST'])
  })
  it('cancels rejected second add without repeating the already confirmed first add', async () => {
    const second = { ...assignment, teacherId: 104 }
    const f = fixture(plan({ assignments: [assignment, second] }), (url) => {
      if (url.pathname.endsWith('/teachers/103')) { f.subjects[0] = { ...subject(), teacherIds: [103], assignments: [{ id: 500, ...assignment }] }; return json({ id: 500 }, 201) }
      if (url.pathname.endsWith('/teachers/104')) return json({ detail: 'second add rejected' }, 409)
      return json(subject())
    })
    await f.flow.send(true); expect(f.intent?.step).toBe(2); expect(f.intent?.rejected).toBe(true)
    const before = [...f.writes]; await f.flow.cancelRejected()
    expect(f.cancelled).toBe(true); expect(f.intent).toBeNull(); expect(f.subjects[0]?.assignments.map((a) => a.id)).toEqual([500]); expect(f.writes).toEqual(before)
  })
  it('preserves unresolved DELETE400 key/date and never offers rejected-step cancellation', async () => {
    const f = fixture(plan({ assignments: [], removals: [{ subjectId: 1, teacherId: 103, assignmentId: 500, validUntilExclusive: '2026-10-06', requestKey: key }] }), (url) => url.pathname.includes('/teachers/') ? json({ detail: 'cutoff rejected' }, 400) : json(subject()))
    await f.flow.send(true); expect(f.intent?.unknown).toBe(true); expect(f.intent?.rejected).not.toBe(true)
    const saved = f.intent; await f.flow.cancelRejected(); expect(f.intent).toBe(saved); expect(f.cancelled).toBe(false)
  })
  it('lets a lawful new actor with no local intent continue an exact server CLOSE receipt using original pastD/key only', async () => {
    const handle = { operationId: operation, assignmentId: 500, requestKey: key, validUntilExclusive: '2026-10-05', teacherId: 103, subjectId: 1, groupId: 4, semesterId: 1, lessonType: 'LECTURE' as const }
    const f = fixture(null, () => { f.subjects[0] = { ...subject(), name: 'Актуальное название', pendingClosures: [] }; return new Response(null, { status: 204 }) })
    f.actor = '20'; f.subjects[0] = { ...subject(), pendingClosures: [handle] }
    const api = new HeadmanSubjectsApi({ accessToken: () => null, fetcher: async () => json(f.subjects[0]) })
    const fresh = await api.getSubject(1)
    expect(f.intent).toBeNull(); f.intent = createSubjectClosureIntent('20', 4, fresh, fresh.pendingClosures![0]!)
    expect(f.intent?.removals?.[0]?.operationId).toBe(operation); expect(f.intent?.actorUserId).toBe('20')
    f.intent = parseSubjectEditIntent(JSON.stringify(f.intent), '20', 4)
    await f.flow.reconcile(); await f.flow.send()
    expect(f.writes).toEqual([{ method: 'DELETE', path: '/api/academic/subjects/1/teachers/103?assignmentId=500&validUntilExclusive=2026-10-05', key, body: null }]); expect(f.completed).toBe(true); expect(f.intent).toBeNull(); expect(f.subjects[0]?.name).toBe('Актуальное название')
    expect(createSubjectClosureIntent('20', 4, fresh, { ...handle, groupId: 9 })).toBeNull()
    expect(createSubjectClosureIntent('20', 4, fresh, { ...handle, subjectId: 8 })).toBeNull()
  })
  it('never clears an unknown admitted add through rejected-draft cancellation', async () => {
    const f = fixture(plan({ step: 1, unknown: true }), () => { throw new Error('No writes permitted') })
    await f.flow.cancelRejected(); expect(f.intent?.unknown).toBe(true); expect(f.cancelled).toBe(false); expect(f.writes).toHaveLength(0)
  })
  it('keeps a payload mismatch receipt blocked instead of treating every409 as neveraccepted', async () => {
    const f = fixture(plan({ assignments: [], removals: [{ subjectId: 1, teacherId: 103, assignmentId: 500, validUntilExclusive: '2026-10-06', requestKey: key }], step: 1, unknown: true }), () => json({ type: 'conflict', extras: { code: 'IDEMPOTENCY_PAYLOAD_MISMATCH' } }, 409))
    await f.flow.reconcile(); await f.flow.send(); expect(f.intent?.blockedReason).toBeTruthy(); expect(f.intent?.unknown).toBe(true)
    const count = f.writes.length; f.flow.reset(); await f.flow.reconcile(); await f.flow.send(); expect(f.writes).toHaveLength(count); expect(f.completed).toBe(false)
  })
  it('does not send any write when persisting the before-send uncertainty fails', async () => {
    const f = fixture(plan(), () => json(subject())); f.storageFails = true
    await f.flow.send(true); expect(f.writes).toHaveLength(0); expect(f.completed).toBe(false)
  })
  it('ignores an old owner response and cannot advance into the next owner save', async () => {
    let resolve!: (value: Response) => void
    const response = new Promise<Response>((accept) => { resolve = accept })
    const f = fixture(plan(), () => response), saving = f.flow.send(true)
    await Promise.resolve(); f.actor = '20'; f.intent = null; f.flow.reset(); resolve(json(subject())); await saving
    expect(f.writes).toHaveLength(1); expect(f.intent).toBeNull(); expect(f.completed).toBe(false); expect(f.reads).toBe(0)
  })
})
