import type { HeadmanHomeworkCreateInput, HeadmanHomeworkUpdateInput, HomeworkBindingMode } from './headman-homework-client'

export interface AssistantHomeworkDraftContext {
  readonly userId: number
  readonly groupId: number
  readonly semesterId: number
  readonly selectedDate: string
  readonly lessonId: number | null
  readonly lessonDate: string
  readonly subjectId: number
  readonly lessonNumber: number | null
  readonly bindingMode: HomeworkBindingMode
}

export interface AssistantHomeworkCreateValues {
  readonly title: string
  readonly description: string
  readonly link: string
}

export interface AssistantHomeworkCreateIntent {
  readonly context: AssistantHomeworkDraftContext
  readonly input: Readonly<HeadmanHomeworkCreateInput>
}

export function createAssistantHomeworkCreateIntent(
  context: AssistantHomeworkDraftContext,
  values: AssistantHomeworkCreateValues,
  requestKey: string,
): AssistantHomeworkCreateIntent {
  const frozenContext = Object.freeze({ ...context })
  const input = Object.freeze({
    title: values.title,
    description: values.description,
    link: values.link,
    subjectId: frozenContext.subjectId,
    groupId: frozenContext.groupId,
    semesterId: frozenContext.semesterId,
    lessonDate: frozenContext.lessonDate,
    lessonNumber: frozenContext.lessonNumber,
    bindingMode: frozenContext.bindingMode,
    requestKey,
  })
  return Object.freeze({ context: frozenContext, input })
}

export function sameAssistantHomeworkDraftContext(
  left: AssistantHomeworkDraftContext,
  right: AssistantHomeworkDraftContext | null,
): boolean {
  return right !== null
    && left.userId === right.userId
    && left.groupId === right.groupId
    && left.semesterId === right.semesterId
    && left.selectedDate === right.selectedDate
    && left.lessonId === right.lessonId
    && left.lessonDate === right.lessonDate
    && left.subjectId === right.subjectId
    && left.lessonNumber === right.lessonNumber
    && left.bindingMode === right.bindingMode
}

export function isDefinitiveHomeworkCreateRejection(status: number | null): boolean {
  return status === 400 || status === 422
}

export function reuseOrCreateAssistantHomeworkIntent(
  existing: AssistantHomeworkCreateIntent | null,
  context: AssistantHomeworkDraftContext,
  values: AssistantHomeworkCreateValues,
  requestKey: () => string,
): AssistantHomeworkCreateIntent | null {
  if (existing !== null) {
    return sameAssistantHomeworkDraftContext(existing.context, context) ? existing : null
  }
  return createAssistantHomeworkCreateIntent(context, values, requestKey())
}

/** Keep uncertain and conflicting operations bound to their original request. */
export function intentAfterHomeworkCreateFailure(
  intent: AssistantHomeworkCreateIntent | null,
  status: number | null,
): AssistantHomeworkCreateIntent | null {
  return isDefinitiveHomeworkCreateRejection(status) ? null : intent
}

export interface AssistantHomeworkEditIntent {
  readonly context: AssistantHomeworkDraftContext
  readonly homeworkId: number
  readonly input: Readonly<HeadmanHomeworkUpdateInput>
}

/** A lost response must replay the exact revision/payload/key, never a fresh edit. */
export function reuseOrCreateAssistantHomeworkEditIntent(
  existing: AssistantHomeworkEditIntent | null,
  context: AssistantHomeworkDraftContext,
  homeworkId: number,
  revision: number,
  values: AssistantHomeworkCreateValues,
  requestKey: () => string,
): AssistantHomeworkEditIntent | null {
  if (existing) {
    return existing.homeworkId === homeworkId && sameAssistantHomeworkDraftContext(existing.context, context)
      ? existing : null
  }
  return Object.freeze({
    context: Object.freeze({ ...context }),
    homeworkId,
    input: Object.freeze({ ...values, expectedRevision: revision, requestKey: requestKey() }),
  })
}
