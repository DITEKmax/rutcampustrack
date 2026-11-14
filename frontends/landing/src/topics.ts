export const topics = [
  { slug: "architecture", title: "Архитектура и API",
    lead: "Как запрос проходит от Nginx до сервисов и каким контрактом возвращается ответ.",
    chapters: ["architecture", "api-statuses"], },
  { slug: "security", title: "Безопасность",
    lead: "Вход, сеансы, управление ролями и доступ к конкретному объекту.",
    chapters: ["security", "profile-device-settings", "account-sessions-history", "headman-assistants", "lesson-request-access"], },
  { slug: "data", title: "Данные",
    lead: "Хранилища, транзакции, кэш и общие механизмы записи и доставки.",
    chapters: ["databases", "transactions", "cache", "patterns"], },
  { slug: "reliability", title: "Надёжность",
    lead: "Как ограничивают операции, расследуют сбои, проверяют изменения и доставляют сборку.",
    chapters: ["resilience", "operation-budgets", "performance", "request-diagnostics", "recovery-retention-verification", "tests", "ci-cd"], },
  { slug: "academic", title: "Администрирование",
    lead: "Как создают и поддерживают учебную структуру, составы, назначения и учётные записи.",
    chapters: ["semester-lifecycle", "group-lifecycle", "subject-lifecycle", "teacher-assignments", "user-lifecycle", "student-transfer-history", "admin-overview"], },
  { slug: "schedule", title: "Расписание",
    lead: "Как шаблоны становятся занятиями и как расписание, переносы и ДЗ видны по ролям.",
    chapters: ["schedule-constructor", "schedule-by-role", "lesson-lifecycle", "lesson-changes", "homework-lifecycle"], },
  { slug: "attendance", title: "Посещаемость",
    lead: "Как появляется отметка, как её пересматривают и где живут подтверждающие документы.",
    chapters: ["geo-checkin", "attendance-mark-lifecycle", "request-lifecycle", "request-attachments"], },
  { slug: "reports", title: "Журнал и отчёты",
    lead: "Как из отметок получаются таблица, проценты, рейтинг и готовые файлы.",
    chapters: ["attendance-journal", "attendance-statistics", "student-ranking", "attendance-journal-export", "report-export", "report-data-contract"], },
  { slug: "notifications", title: "Уведомления и PWA",
    lead: "Как событие доходит до пользователя и что происходит на клиенте при открытии или потере сети.",
    chapters: ["realtime", "notification-lifecycle", "notification-actions", "telegram-bot", "web-push-subscription", "pwa-offline"], },
  { slug: "campus", title: "Карта кампуса",
    lead: "Как публикуют планы этажей, читают версии и учитывают открытия.",
    chapters: ["campus-map", "campus-map-statistics"], },
  { slug: "design", title: "Дизайн и типографика",
    lead: "", chapters: [], },
] as const

export type Topic = (typeof topics)[number]
export type ChapterId = Topic['chapters'][number]

export const presentationHome = '/presentation/'

export function topicHref(topic: Topic, hash = '') {
  return presentationHome + topic.slug + '/' + hash
}
