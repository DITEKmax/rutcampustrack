import { HeadmanSubjectsApiError, headmanAdoptionPending, headmanIdempotencyMismatch, type HeadmanSubjectsApi, type HeadmanSubject } from './headman-subjects-client'
import { subjectEditAssignment, subjectEditRemoval, subjectEditTotal, subjectEditStepConfirmed, subjectEditResultConfirmed, subjectEditPrefixConfirmed, type SubjectEditIntent } from './subject-edit-intent'
export interface SubjectSavePort {
  api(): HeadmanSubjectsApi | null; actor(): string | null; group(): number | null; available(): boolean; disposed(): boolean
  subjects(): readonly HeadmanSubject[]; intent(): SubjectEditIntent | null; persist(next: SubjectEditIntent | null): boolean
  read(): Promise<boolean>; busy(value: boolean): void; error(value: string | null): void; denied(value: boolean): void
  completed(selectedResult?: boolean): void; cancelled?(): void; failed(cause: unknown): void
}
export function createSubjectSaveFlow(port: SubjectSavePort) {
  let revision = 0, running = false, reconciled = false
  const reset = () => { revision++; running = false; reconciled = false }
  const currentOwner = () => {
    const owner = { api: port.api(), actor: port.actor(), group: port.group(), revision }
    return () => !port.disposed() && owner.api === port.api() && owner.actor === port.actor() && owner.group === port.group() && owner.revision === revision
  }
  async function detail(subjectId: number): Promise<HeadmanSubject> {
    const api = port.api(), group = port.group(), current = currentOwner()
    if (!api || group === null || !port.available()) throw new Error('Чтение предмета сейчас недоступно.')
    const subject = await api.getSubject(subjectId)
    if (!current() || !port.available()) throw new Error('Контекст чтения изменился.')
    if (subject.id !== subjectId || subject.groupId !== group) throw new Error('Ответ предмета не соответствует текущей группе.')
    return subject
  }
  async function confirmAdoptions(pending: SubjectEditIntent, subjectId: number): Promise<void> {
    const api = port.api(), group = port.group(), current = currentOwner()
    if (!api || group === null || !port.available()) return
    const subject = await detail(subjectId)
    if (subject.name !== pending.name || subject.lessonTypes.length !== pending.lessonTypes.length || !pending.lessonTypes.every((type) => subject.lessonTypes.includes(type))) { port.error('Выбранный предмет не соответствует сохранённому черновику. Исходная операция остаётся неопределённой.'); return }
    const expected = pending.subjectId === null ? pending.assignments : [subjectEditAssignment(pending)].filter((item) => item !== null)
    if (!expected.length) { port.error('Нет назначения для серверного подтверждения неизвестного создания. Повтор создания остаётся заблокирован.'); return }
    const sameTuple = (a: typeof expected[number], b: typeof expected[number]) => a.teacherId === b.teacherId && a.semesterId === b.semesterId && a.lessonType === b.lessonType && a.validFrom === b.validFrom && a.validUntilExclusive === b.validUntilExclusive
    // Received503 target identities remain attributable even while PREPARED is hidden from assignments.
    const handles = [...(pending.pendingAdoptions ?? [])]
    const activeTargets = expected.flatMap((target) => subject.assignments.filter((a) => sameTuple(a, target)))
    for (const target of activeTargets) if (!handles.some((h) => h.targetAssignmentId === target.id)) handles.push({ targetAssignmentId: target.id, adoptionOperationId: subject.pendingAdoptions?.find((h) => h.targetAssignmentId === target.id)?.adoptionOperationId ?? null })
    for (const target of expected) {
      const prepared = (subject.pendingAdoptions ?? []).filter((h) => h.subjectId === subjectId && h.groupId === group && ['PREPARED', 'APPLIED'].includes(h.state) && sameTuple(h, target))
      const active = subject.assignments.filter((a) => sameTuple(a, target))
      const ids = new Set([...prepared.map((h) => h.targetAssignmentId), ...active.map((a) => a.id)])
      if (ids.size > 1) { port.error('Несколько назначений совпадают с черновиком. Исходный POST не повторяется.'); return }
      if (prepared[0] && !handles.some((h) => h.targetAssignmentId === prepared[0]!.targetAssignmentId)) handles.push(prepared[0])
    }
    if (handles.some((h) => { const record = subject.pendingAdoptions?.find((entry) => entry.targetAssignmentId === h.targetAssignmentId); return record && (record.subjectId !== subjectId || record.groupId !== group || !expected.some((target) => sameTuple(record, target))) })) { port.error('Сохранённый handle не соответствует ожидаемому назначению. Восстановление заблокировано.'); return }
    if (handles.length !== expected.length) { port.error('Не удалось однозначно связать серверные назначения с сохранённым запросом. Повтор исходного POST заблокирован.'); return }
    if (!port.persist({ ...pending, createdId: pending.subjectId === null ? subjectId : pending.createdId, pendingAdoptions: handles })) return
    const matched = new Set<number>()
    for (const handle of handles) {
      if (!current() || !port.available()) return
      const result = await api.resumeAdoption(handle.targetAssignmentId, handle.adoptionOperationId)
      if (!current() || !port.available()) return
      const index = expected.findIndex((target, i) => !matched.has(i) && sameTuple(result, target))
      if (result.id !== handle.targetAssignmentId || result.subjectId !== subjectId || result.groupId !== group || index < 0) throw new Error('Сервер подтвердил другое назначение. Исходная операция сохранена без завершения.')
      matched.add(index)
    }
    if (current() && port.available() && matched.size === expected.length) port.persist({ ...port.intent()!, step: pending.step + 1, unknown: false, pendingAdoptions: [] })
  }

  async function reconcile(selectedSubjectId: number | null = null, acceptSelectedResult = false): Promise<void> {
    if (running || !port.available()) return
    const current = currentOwner(); running = true; reconciled = false; port.busy(true); port.error(null)
    try {
      if (!await port.read() || !current() || !port.available()) return
      const pending = port.intent(); if (!pending) return
      const subjectId = pending.subjectId ?? pending.createdId ?? selectedSubjectId
      const freshSubject = subjectId === null ? null : await detail(subjectId)
      if (!current() || !port.available()) return
      let selectedResult = false
      if (pending.blockedReason) { port.error(pending.blockedReason); return }
      if (pending.unknown && pending.subjectId === null && pending.createdId === null && pending.assignments.length === 0 && selectedSubjectId !== null && acceptSelectedResult) {
        const chosen = freshSubject
        const accepted = { ...pending, createdId: selectedSubjectId, step: 1, unknown: false }
        if (!chosen || (chosen.pendingAdoptions?.length ?? 0) !== 0 || !subjectEditResultConfirmed(accepted, chosen ? [chosen] : [])) { port.error('Выбранный предмет не соответствует желаемому состоянию или его применение не завершено. Исходный POST не повторяется.'); return }
        if (!port.persist(accepted)) return
        selectedResult = true
      } else if (pending.unknown && subjectEditStepConfirmed(pending, freshSubject ? [freshSubject] : [])) { if (!port.persist({ ...pending, step: pending.step + 1, unknown: false })) return }
      else if (pending.unknown && pending.subjectId !== null && pending.step === 0) {
        if (!freshSubject) { port.error('Предмет недоступен в текущей группе. Исходный PUT сохранён.'); return }
        if (!port.persist({ ...pending, unknown: false })) return
      } else if (pending.unknown && !subjectEditRemoval(pending)) {
        const subjectId = pending.subjectId ?? pending.createdId ?? selectedSubjectId
        if (subjectId === null) { port.error('Ответ на создание потерян. Выбери конкретный сохранённый предмет для проверки; совпадения названия недостаточно. Повтор создания заблокирован.'); return }
        await confirmAdoptions(pending, subjectId)
      }
      if (!current() || !port.available()) return
      const intent = port.intent(); if (!intent) return
      if (intent.unknown && !subjectEditRemoval(intent)) { port.error('Применение назначения ещё не подтверждено. Повтор исходного POST заблокирован.'); return }
      if (intent.rejected) { port.error('Сервер отклонил оставшийся шаг. Подтверждённые изменения сохранены; можно отменить только оставшийся черновик.'); return }
      if (intent.step === subjectEditTotal(intent)) {
        if (!await port.read() || !current() || !port.available()) return
        const finalSubject = await detail((intent.subjectId ?? intent.createdId)!)
        if (!current() || !port.available() || port.intent() !== intent || (!intent.closureOnly && (finalSubject.pendingAdoptions?.length ?? 0) !== 0) || finalSubject.pendingClosures?.some((h) => intent.removals?.some((r) => r.assignmentId === h.assignmentId)) || !subjectEditResultConfirmed(intent, [finalSubject])) { if (current()) port.error('Операции завершены, но итоговый состав предмета ещё не подтверждён. Обнови состояние.'); return }
        if (port.persist(null)) port.completed(selectedResult)
      } else { reconciled = true; port.error(subjectEditRemoval(intent) && intent.unknown ? 'Продолжение повторит снятие с исходным ключом и датой.' : 'Список обновлён. Можно продолжить оставшиеся шаги сохранения.') }
    } catch (cause) { if (current()) { port.error(cause instanceof Error ? cause.message : 'Не удалось подтвердить сохранение.'); port.failed(cause) } }
    finally { if (current()) { running = false; port.busy(false) } }
  }
  async function send(fresh = false): Promise<void> {
    const api = port.api(), current = currentOwner(), initial = port.intent()
    if (!api || running || !port.available() || !initial || initial.blockedReason || initial.rejected || (!fresh && !reconciled) || (initial.unknown && !subjectEditRemoval(initial))) return
    running = true; reconciled = false; port.busy(true); port.error(null)
    let failed = false
    try {
      while (port.intent() && current() && port.available()) {
        const plan = port.intent()!; if (plan.step >= subjectEditTotal(plan)) break
        if (!port.persist({ ...plan, unknown: true })) { failed = true; break }
        try {
          if (plan.subjectId === null) {
            const created = await api.createSubject({ name: plan.name, type: plan.lessonTypes[0]!, lessonTypes: plan.lessonTypes, initialAssignments: plan.assignments })
            if (!current()) return
            if (!port.persist({ ...plan, createdId: created.id, step: 1, unknown: false })) { failed = true; break }
          } else {
            const removal = subjectEditRemoval(plan), assignment = subjectEditAssignment(plan)
            if (plan.step === 0) await api.updateSubject(plan.subjectId, { name: plan.name, type: plan.lessonTypes[0]!, lessonTypes: plan.lessonTypes })
            else if (removal) await api.removeTeacher(removal.subjectId, removal.teacherId, removal.assignmentId, removal.validUntilExclusive, removal.requestKey)
            else if (assignment) await api.addTeacher(plan.subjectId, assignment.teacherId, assignment)
            else throw new Error('Сохранённый шаг операции повреждён.')
            if (!current()) return
            if (!port.persist({ ...plan, step: plan.step + 1, unknown: false })) { failed = true; break }
          }
        } catch (cause) {
          if (!current()) return
          failed = true
          const adoption = headmanAdoptionPending(cause)
          if (adoption && (plan.subjectId === null || adoption.subjectId === plan.subjectId)) {
            port.persist({ ...plan, createdId: plan.subjectId === null ? adoption.subjectId : plan.createdId, unknown: true, pendingAdoptions: adoption.pendingAdoptions }); port.error('Назначения приняты, но применение к расписанию ещё не завершено. Проверь состояние; исходный POST повторяться не будет.')
          } else if (headmanIdempotencyMismatch(cause)) {
            const message = 'Ключ снятия связан с другим запросом. Исходное намерение сохранено; прежний результат нельзя считать отклонённым.'
            port.persist({ ...plan, unknown: true, blockedReason: message }); port.error(message)
          } else if (cause instanceof HeadmanSubjectsApiError && cause.response.status >= 400 && cause.response.status < 500) {
            if (plan.step === 0) port.persist(null)
            else if (!plan.unknown && subjectEditAssignment(plan)) port.persist({ ...plan, unknown: false, rejected: true })
            else port.persist({ ...plan, unknown: true })
            port.denied([401, 403].includes(cause.response.status)); port.error(`Сохранение остановлено: ${cause.message}. Подтверждённых шагов: ${plan.step}. Черновик сохранён.`)
          } else port.error('Ответ на запись не получен. Черновик и исходный запрос сохранены. Проверь состояние перед продолжением.')
          port.failed(cause); break
        }
      }
    } finally { if (current()) { running = false; port.busy(false) } }
    if (!failed && current() && port.available()) await reconcile()
  }
  async function cancelRejected(): Promise<void> {
    const pending = port.intent(), current = currentOwner()
    if (running || !port.available() || !pending?.rejected || pending.unknown || pending.subjectId === null || subjectEditRemoval(pending)) return
    running = true; port.busy(true); port.error(null)
    try {
      if (!await port.read() || !current() || !port.available()) return
      const subject = await detail(pending.subjectId)
      if (!current() || !port.available() || port.intent() !== pending) return
      if ((subject.pendingAdoptions?.length ?? 0) !== 0 || !subjectEditPrefixConfirmed(pending, subject)) { port.error('Подтверждённая часть ещё не согласована с сервером. Исходная запись сохранена.'); return }
      if (port.persist(null)) { reconciled = false; port.cancelled?.() }
    } catch (cause) { if (current()) { port.error(cause instanceof Error ? cause.message : 'Не удалось подтвердить сохранённые изменения.'); port.failed(cause) } }
    finally { if (current()) { running = false; port.busy(false) } }
  }
  return { reset, reconcile, send, cancelRejected, canContinue: () => reconciled }
}
