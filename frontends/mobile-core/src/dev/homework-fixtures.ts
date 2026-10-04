import type { StudentHomework, StudentHomeworkItem } from '../api/types'
import type { HomeworkDateRange } from '../domain/homework'

export interface HomeworkReviewScenario {
  id: string
  label: string
  feed: StudentHomework | null
  expandedId?: string
  offline?: boolean
  readOnly?: boolean
  unavailable?: string
  feedError?: boolean
  loading?: boolean
  initialRange?: HomeworkDateRange
  initialCommand?: boolean
  commandFailure?: boolean
  holdCommand?: boolean
  enlargedText?: boolean
}
function item(overrides: Partial<StudentHomeworkItem> = {}): StudentHomeworkItem {
  return { id: 'homework-programming', subject: { id: 'programming', name: 'Основы программирования' },
    title: 'Решить задачи 1–8 и приложить ссылку на репозиторий.',
    description: 'Используй циклы и условия. В README добавь инструкцию запуска и примеры входных данных. Проверь решение на граничных случаях.',
    link: 'https://example.org/materials/programming', lessonDate: '2026-09-01', lessonNumber: 1, bindingMode: 'LESSON',
    archived: false, completed: false, completedAt: null, ...overrides }
}
function feed(items: StudentHomeworkItem[]): StudentHomework {
  return { semester: { id: 'semester-review', name: 'Осень 2026', dateFrom: '2026-08-20', dateTo: '2026-12-31' },
    from: '2026-09-01', to: '2026-12-31', serverNow: '2026-09-01T09:30:00Z', items }
}
export function createHomeworkReviewScenarios(): HomeworkReviewScenario[] {
  const normal = feed([item(), item({ id: 'homework-networks', subject: { id: 'networks', name: 'Компьютерные сети' },
    lessonDate: '2026-09-02', title: 'Подготовить схему адресации для лабораторной работы.', description: 'Покажи подсети и адреса шлюзов. Сохрани схему в PDF.', link: 'https://example.org/materials/networks' })])
  const done = item({ completed: true, completedAt: '2026-09-01T08:00:00Z' })
  return [
    { id: 'homework-feed', label: 'Лента · Figma', feed: normal },
    { id: 'homework-expanded', label: 'Раскрытое описание · Figma', feed: feed([item({ title: 'Собери консольное приложение и добавь тесты.', description: 'В README опиши запуск, формат входных данных и принятые ограничения. Добавь тесты для основных сценариев и граничных случаев.' })]), expandedId: 'homework-programming' },
    { id: 'homework-completed', label: 'Выполнено сегодня · Figma', feed: feed([done, { ...normal.items[1]!, completed: true, completedAt: '2026-09-01T08:10:00Z' }]) },
    { id: 'homework-history', label: 'Предыдущие задания · Figma', feed: feed([item({ lessonDate: '2026-08-28' }), { ...normal.items[1]!, lessonDate: '2026-08-29' }]), initialRange: { from: '2026-08-25', to: '2026-08-31' } },
    { id: 'homework-no-material', label: 'Описание без материалов · Figma', feed: feed([item({ link: null }), normal.items[1]!]) },
    { id: 'homework-material-only', label: 'Материалы без описания', feed: feed([item({ description: '' })]) },
    { id: 'homework-description-only', label: 'Описание без материалов раскрыто', feed: feed([item({ link: null })]), expandedId: 'homework-programming' },
    { id: 'homework-no-extras', label: 'Без описания и материалов', feed: feed([item({ link: null, description: '   ' })]) },
    { id: 'homework-unsafe-link', label: 'Неподдерживаемая ссылка', feed: feed([item({ link: 'javascript:alert(1)', description: '' })]) },
    { id: 'homework-date-bound', label: 'Привязка к дате', feed: feed([item({ bindingMode: 'DATE', lessonNumber: null })]) },
    { id: 'homework-date-union', label: 'Выполнено сегодня вне диапазона дат', feed: feed([item({ ...done, lessonDate: '2026-08-25', archived: true }), normal.items[1]!]) },
    { id: 'homework-archived', label: 'Архив · только чтение', feed: feed([item({ archived: true }), item({ ...done, id: 'homework-archived-done', archived: true })]) },
    { id: 'homework-loading', label: 'Загрузка', feed: null, loading: true },
    { id: 'homework-empty', label: 'Заданий пока нет', feed: feed([]) },
    { id: 'homework-feed-error', label: 'Ошибка загрузки', feed: null, feedError: true },
    { id: 'homework-cached-error', label: 'Ошибка обновления · сохранённая лента', feed: normal, feedError: true },
    { id: 'homework-offline', label: 'Офлайн · сохранённая лента', feed: normal, offline: true },
    { id: 'homework-offline-empty', label: 'Офлайн · нет сохранённых заданий', feed: null, offline: true, unavailable: 'Сохранённых заданий пока нет. Подключись к интернету, чтобы загрузить их.' },
    { id: 'homework-readonly', label: 'Только просмотр', feed: normal, readOnly: true },
    { id: 'homework-permission', label: 'Нет доступа', feed: null, unavailable: 'У тебя нет доступа к заданиям этой группы.' },
    { id: 'homework-session', label: 'Сессия завершена', feed: null, unavailable: 'Сессия завершена. Войди снова, чтобы увидеть задания.' },
    { id: 'homework-no-semester', label: 'Нет активного семестра', feed: null, unavailable: 'Задания появятся после начала активного семестра.' },
    { id: 'homework-pending-complete', label: 'Сохранение выполнения · ожидание ACK', feed: feed([item()]), initialCommand: true, holdCommand: true },
    { id: 'homework-pending-undo', label: 'Снятие отметки · ожидание ACK', feed: feed([done]), initialCommand: false, holdCommand: true },
    { id: 'homework-failed-complete', label: 'Ошибка выполнения · повтор той же команды', feed: feed([item()]), initialCommand: true, commandFailure: true },
    { id: 'homework-failed-undo', label: 'Ошибка снятия отметки · повтор той же команды', feed: feed([done]), initialCommand: false, commandFailure: true },
    { id: 'homework-long-text', label: 'Длинные тексты и URL', feed: feed([item({ subject: { id: 'long', name: 'ОченьДлинноеНазваниеПредметаБезПробелов'.repeat(8) }, title: 'ДлинныйТекстЗаданияБезПробелов'.repeat(25).slice(0, 255), description: 'ПодробноеОписаниеБезПробелов'.repeat(160).slice(0, 4000), link: `https://example.org/${'long-material-path'.repeat(112)}` })]), expandedId: 'homework-programming' },
    { id: 'homework-root20', label: 'Увеличенный текст · root20', feed: normal, enlargedText: true },
  ]
}
