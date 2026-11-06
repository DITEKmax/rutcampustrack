import type { NotificationItem } from './notifications-v2-models'

const subjectLabel = 'предмет'
const lessonTypeLabel = 'тип занятия'
const previousRoomLabels = ['предыдущая аудитория', 'старая аудитория']
const newRoomLabels = ['новая аудитория']
const teacherLabel =
  /^(?:(?:фио|имя|старый|прежний|новый) преподавателя?|преподаватель|teacher(?: name)?)$/iu
const legacyLessonType = /^(?:лекция|практика|лабораторная(?: работа)?)$/iu
const scopeLabels = ['до изменения', 'после изменения']
const roomChangeHeading = 'Изменилась аудитория'
const legacyRoomChange = /^(.+?) · вместо (.+?) занятие пройдёт в (.+?)\.?$/u
const lessonDate = /^\d{4}-\d{2}-\d{2}$/
const lessonTime = /^\d{2}:\d{2}(?::\d{2})?$/
const lessonNumber = /^№[1-9]\d*$/

/** Old API joins date, start/end, room and slot. Only a complete position is unambiguous. */
function legacyRoomScope(value: string) {
  const [date, start, end, ...tail] = value.split(' · ')
  const slot = tail.at(-1)
  if (
    !date ||
    !lessonDate.test(date) ||
    !start ||
    !lessonTime.test(start) ||
    !end ||
    !lessonTime.test(end) ||
    !slot ||
    !lessonNumber.test(slot) ||
    (tail.length !== 1 && tail.length !== 2)
  )
    return null
  return {
    room: tail.length === 2 ? tail[0] : undefined,
    context: [date, start, end, slot].join(' · '),
  }
}

export function notificationInboxPresentation(item: NotificationItem) {
  const labelOf = (field: NotificationItem['details'][number]) =>
    field.label?.trim().toLocaleLowerCase('ru-RU') ?? ''
  const findDetail = (...labels: string[]) =>
    item.details.find((field) => labels.includes(labelOf(field)))?.value.trim()
  const knownRoomHeading =
    item.heading === roomChangeHeading || item.heading === 'Изменилась аудитория следующей пары'
  const maybeRoomChange = knownRoomHeading || item.type === 'LESSON_UPDATED'
  const summary = item.summary?.trim() ?? ''
  const teacherNames = item.details
    .filter((field) => teacherLabel.test(labelOf(field)))
    .map((field) => field.value.trim())
  const rawSummaryParts = summary.split(/\s+·\s+|\r?\n/u)
  const summaryParts = rawSummaryParts.filter(
    (part) => part && !teacherNames.includes(part) && !/^преподаватель:\s*/iu.test(part),
  )
  const structuredPrevious = findDetail(...previousRoomLabels)
  const structuredCurrent = findDetail(...newRoomLabels)
  const legacyRoom = maybeRoomChange ? legacyRoomChange.exec(summary) : null
  const legacySubject = !structuredPrevious && !structuredCurrent ? legacyRoom?.[1] : undefined
  const legacyLesson =
    item.type.startsWith('LESSON_') ||
    item.type.startsWith('HOMEWORK_') ||
    item.action?.kind === 'lesson' ||
    item.action?.kind === 'homework'
  const hasLegacyType =
    legacyLesson && summaryParts.length >= 2 && legacyLessonType.test(summaryParts[1]!)
  const subject =
    legacySubject ?? findDetail(subjectLabel) ?? (hasLegacyType ? summaryParts[0] : undefined)
  const lessonType = findDetail(lessonTypeLabel) ?? (hasLegacyType ? summaryParts[1] : undefined)
  const lesson = subject ? [subject, lessonType].filter(Boolean).join(', ') : null

  const legacyScopes = maybeRoomChange
    ? scopeLabels.map((label) => legacyRoomScope(findDetail(label) ?? ''))
    : []
  const previousRoom = maybeRoomChange
    ? (structuredPrevious ?? legacyRoom?.[2] ?? legacyScopes[0]?.room)
    : undefined
  const currentRoom = maybeRoomChange
    ? (structuredCurrent ?? legacyRoom?.[3] ?? legacyScopes[1]?.room)
    : undefined
  const oldRoom = previousRoom ?? (legacyScopes[0] && currentRoom ? 'Не указана' : undefined)
  const newRoom = currentRoom ?? (legacyScopes[1] && previousRoom ? 'Не указана' : undefined)
  const hasRoomChange = Boolean(oldRoom || newRoom) && oldRoom !== newRoom
  const roomChange = knownRoomHeading || hasRoomChange
  const summaryContent = summaryParts.filter(
    (part, index) =>
      part !== item.heading.trim() &&
      part !== subject &&
      part !== lesson &&
      (!hasLegacyType || index > 1),
  )
  const details = item.details.flatMap((field) => {
    const label = labelOf(field)
    if (teacherLabel.test(label) || /^преподаватель:\s*/iu.test(field.value)) return []
    if (!label && teacherNames.includes(field.value.trim())) return []
    if (lesson && (label === subjectLabel || label === lessonTypeLabel)) return []
    if (hasRoomChange && [...previousRoomLabels, ...newRoomLabels].includes(label)) return []
    if (hasRoomChange && label === 'аудитория' && field.value.trim() === newRoom) return []
    const scope = hasRoomChange ? legacyScopes[scopeLabels.indexOf(label)] : null
    return scope ? [{ ...field, value: scope.context }] : [field]
  })

  return {
    heading: roomChange ? roomChangeHeading : (lesson ?? item.heading),
    event: !roomChange && lesson && lesson !== item.heading ? item.heading : null,
    detailHeading: roomChange ? lesson : null,
    rooms: hasRoomChange ? { previous: oldRoom, current: newRoom } : null,
    summary: legacyRoom
      ? ''
      : summaryContent.length === rawSummaryParts.length
        ? summary
        : summaryContent.join(' · '),
    details,
  }
}
