import type {
  HeadmanManagedHomework,
  HeadmanHomeworkPlacementInput,
  HeadmanHomeworkUpdateInput,
} from '../../../../mobile-core/src/features/homework/headman-homework-client'

/** Choosing an occurrence is an edit even when its calendar date and type are unchanged. */
export function homeworkPlacementChanged(
  item: HeadmanManagedHomework,
  selected: HeadmanHomeworkPlacementInput,
): boolean {
  return (
    selected.selectedOccurrenceId !== undefined ||
    selected.bindingMode !== item.bindingMode ||
    selected.lessonDate !== item.lessonDate ||
    selected.lessonNumber !== item.lessonNumber ||
    selected.lessonType !== item.lessonType
  )
}

export function homeworkEditMatches(
  item: HeadmanManagedHomework,
  input: HeadmanHomeworkUpdateInput,
): boolean {
  const normalized = (value: string | null | undefined) => value?.trim() || null
  return (
    item.title === input.title.trim() &&
    normalized(item.description) === normalized(input.description) &&
    normalized(item.link) === normalized(input.link) &&
    (input.bindingMode === undefined || item.bindingMode === input.bindingMode) &&
    (input.lessonDate === undefined || item.lessonDate === input.lessonDate) &&
    (input.lessonNumber === undefined || item.lessonNumber === input.lessonNumber) &&
    (input.lessonType === undefined || item.lessonType === input.lessonType)
  )
}

/** Validate user text before freezing an operation; a local refusal has no server outcome. */
export function canonicalHomeworkText(values: {
  title: string
  description?: string | null
  link?: string | null
}) {
  if (!values.title.trim()) throw new Error('Укажи заголовок задания.')
  for (const [value, maximum, label] of [
    [values.title, 255, 'Заголовок'],
    [values.description ?? '', 4000, 'Описание'],
    [values.link ?? '', 2048, 'Ссылка'],
  ] as const)
    if (Array.from(value).length > maximum)
      throw new Error(`${label}: не больше ${maximum} символов.`)
  const link = values.link?.trim() || null
  if (link && !/^https?:\/\//i.test(link))
    throw new Error('Основная ссылка должна начинаться с https:// или http://.')
  return { title: values.title.trim(), description: values.description?.trim() || null, link }
}
