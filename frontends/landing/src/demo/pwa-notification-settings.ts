import {
  notificationPaletteIds,
  notificationTypeIds,
  type NotificationCatalog,
  type NotificationPreferencesV2,
  type NotificationSignedRole,
  type NotificationTypeId,
} from '../../../mobile-core/src/features/notifications/notifications-v2-models'

const student = ['STUDENT'] as const
const group = ['STUDENT', 'HEADMAN'] as const
const education = ['STUDENT', 'HEADMAN', 'TEACHER'] as const
const groupAdmin = ['STUDENT', 'HEADMAN', 'ADMIN'] as const
const admin = ['ADMIN'] as const
const groups = {
  lessons: 'Занятия',
  reminders: 'Напоминания',
  tickets: 'Заявки и посещаемость',
  schedule: 'Расписание',
  homework: 'Домашние задания',
  group: 'Группа',
}
const meanings: Record<NotificationTypeId, [keyof typeof groups, string, readonly NotificationSignedRole[]]> = {
  LESSON_STARTED: ['reminders', 'Занятие началось', group],
  LESSON_REMINDER: ['reminders', 'Напоминание об отметке', group],
  LESSON_CLOSED: ['lessons', 'Занятие завершено', group],
  LESSON_CANCELLED: ['lessons', 'Занятие отменено', education],
  LESSON_BLOCKED: ['lessons', 'Самостоятельная отметка закрыта', group],
  LESSON_ONE_OFF_CREATED: ['schedule', 'Добавлено разовое занятие', education],
  LESSON_TRANSFERRED: ['lessons', 'Занятие перенесено', education],
  LESSON_RESTORED: ['lessons', 'Занятие восстановлено', education],
  LESSON_UPDATED: ['lessons', 'Занятие изменено', education],
  EXCUSE_REQUESTED: ['tickets', 'Новая заявка на уважительную причину', group],
  EXCUSE_APPROVED: ['tickets', 'Уважительная причина одобрена', student],
  EXCUSE_REJECTED: ['tickets', 'Уважительная причина отклонена', student],
  EXCUSE_CANCELLED: ['tickets', 'Заявка на уважительную причину отменена', group],
  LATE_CHECKIN_REQUESTED: ['tickets', 'Новая заявка на отметку', group],
  LATE_CHECKIN_APPROVED: ['tickets', 'Заявка на отметку одобрена', student],
  LATE_CHECKIN_REJECTED: ['tickets', 'Заявка на отметку отклонена', student],
  LATE_CHECKIN_CANCELLED: ['tickets', 'Заявка на отметку отменена', group],
  ATTENDANCE_MARKED_BY_HEADMAN: ['tickets', 'Отметка посещаемости изменена', student],
  HOMEWORK_PUBLISHED: ['homework', 'Новое домашнее задание', education],
  HOMEWORK_UPDATED: ['homework', 'Домашнее задание изменено', education],
  HOMEWORK_WEEKLY_DIGEST: ['homework', 'Домашние задания на неделю', student],
  HOMEWORK_DUE_REMINDER: ['homework', 'Скоро срок домашнего задания', student],
  HOMEWORK_WITHDRAWN: ['homework', 'Домашнее задание снято', education],
  GROUP_RENAMED: ['group', 'Название группы изменено', groupAdmin],
  GROUP_ARCHIVED: ['group', 'Группа архивирована', groupAdmin],
  GROUP_MEMBERSHIP_CHANGED: ['group', 'Твоя группа изменена', student],
  HEADMAN_ACCESS_CHANGED: ['group', 'Полномочия старосты изменены', student],
  ASSISTANT_ACCESS_CHANGED: ['group', 'Полномочия помощника изменены', student],
  ADMIN_ACCESS_CHANGED: ['group', 'Административные полномочия изменены', admin],
  SEMESTER_ACTIVATED: ['group', 'Семестр активирован', admin],
  SEMESTER_OPERATION_COMPLETED: ['group', 'Операция с семестром завершена', admin],
  GROUP_PROMOTION_COMPLETED: ['group', 'Обработана партия автоматического перевода групп', admin],
}
const paletteLabels = {
  violet: 'Фиолетовый', lavender: 'Лавандовый', purple: 'Пурпурный', indigo: 'Индиго',
  blue: 'Синий', cobalt: 'Кобальтовый', sky: 'Небесный', cyan: 'Голубой',
  turquoise: 'Бирюзовый', teal: 'Морской', green: 'Зелёный', mint: 'Мятный',
  lime: 'Лаймовый', olive: 'Оливковый', yellow: 'Жёлтый', amber: 'Янтарный',
  orange: 'Оранжевый', coral: 'Коралловый', red: 'Красный', raspberry: 'Малиновый',
  pink: 'Розовый', rose: 'Розовый пастельный', brown: 'Коричневый', grey: 'Серый',
} satisfies Record<(typeof notificationPaletteIds)[number], string>

// Filtering happens in the notification screen. The V2 wire profile always contains all types.
export const demoNotificationCatalog: NotificationCatalog = {
  schemaVersion: 2,
  revision: 'demo-notifications-v2-32',
  groups: Object.entries(groups).map(([id, label]) => ({ id, label })),
  types: notificationTypeIds.map(id => {
    const [groupId, label, roles] = meanings[id]
    return { id, groupId, label, roles }
  }),
  palette: notificationPaletteIds.map(id => ({ id, label: paletteLabels[id] })),
}

export function initialDemoNotificationPreferences(): NotificationPreferencesV2 {
  return {
    revision: '1',
    splitChannels: false,
    telegramLinked: true,
    mutedUntil: null,
    types: Object.fromEntries(notificationTypeIds.map(id => [
      id, { appPush: true, telegram: true, colorId: 'violet' },
    ])) as NotificationPreferencesV2['types'],
  }
}
