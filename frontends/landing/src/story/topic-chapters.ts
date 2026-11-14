import type { ChapterId, Topic } from '../topics'
import type { StoryOperation } from './story-source'

interface Chapter {
  id: ChapterId
  title?: string
  lead?: string
  load: () => Promise<readonly StoryOperation[]>
}

const chapters: Record<ChapterId, Omit<Chapter, 'id'>> = {
  "architecture": {
    load: () => import('./story-source').then(mod => [...mod.entryOperations, ...mod.serviceOperations]),
  },
  "security": {
    title: "Безопасность",
    lead: "Вход, завершение сессии и восстановление пароля. Затем — проверки прав и запросов в Gateway и сервисах.",
    load: () => Promise.all([
      import('./auth-lifecycle-source').then(mod => mod.authLifecycleOperations),
      import('./password-recovery-source').then(mod => mod.passwordRecoveryOperations),
      import('./security-source').then(mod => mod.securityOperations),
    ]).then(parts => parts.flat()),
  },
  "patterns": {
    title: "Паттерны проекта",
    lead: "Как сервисы сохраняют результат, обрабатывают повторные доставки и согласуют данные с интерфейсами.",
    load: () => import('./patterns-source').then(mod => mod.patternOperations),
  },
  "ci-cd": {
    title: "CI/CD",
    lead: "Автоматические проверки исходников и ручной путь от выбранного коммита до релиза.",
    load: () => import('./ci-cd-source').then(mod => mod.ciCdOperations),
  },
  "tests": {
    title: "Тесты",
    lead: "От проверки одного правила до действий в браузере на изолированном стенде.",
    load: () => import('./tests-source').then(mod => mod.testOperations),
  },
  "databases": {
    title: "Базы данных",
    lead: "Какие данные принадлежат сервисам и как меняются схемы, индексы и временные записи.",
    load: () => import('./database-source').then(mod => mod.databaseOperations),
  },
  "api-statuses": {
    title: "Статусы HTTP и gRPC",
    lead: "Что отвечают REST API и прокси, как сервисы переводят статусы RPC и какие поля читает клиент.",
    load: () => import('./api-status-source').then(mod => mod.apiStatusOperations),
  },
  "realtime": {
    title: "События в реальном времени",
    lead: "Как журнал перечитывает данные после сигнала и как «Занятие началось» приходит в приложение и Telegram.",
    load: () => import('./realtime-source').then(mod => mod.realtimeOperations),
  },
  "cache": {
    title: "Кэширование",
    lead: "Что Academic хранит в Redis, когда читает PostgreSQL и как обновляет кэш после записи.",
    load: () => import('./cache-source').then(mod => mod.cacheOperations),
  },
  "transactions": {
    title: "Транзакции и конкурентность",
    lead: "Что происходит, когда два запроса одновременно активируют семестр или сохраняют одну отметку.",
    load: () => import('./transactions-source').then(mod => mod.transactionOperations),
  },
  "resilience": {
    title: "Отказоустойчивость",
    lead: "Как сервисы ограничивают ожидание, что видит пользователь при сбое и откуда продолжаются сохранённые операции.",
    load: () => import('./resilience-source').then(mod => mod.resilienceOperations),
  },
  "operation-budgets": {
    title: "Бюджеты операций",
    lead: "Сколько операция может ждать, повторяться и занимать ресурсов. На примере выгрузки недельного отчёта, отметки и доставки события.",
    load: () => import('./operation-budgets-source').then(mod => mod.operationBudgetOperations),
  },
  "performance": {
    title: "Производительность",
    lead: "Как сервисы ограничивают объём чтения и как Vue разбивает приложение на файлы.",
    load: () => import('./performance-source').then(mod => mod.performanceOperations),
  },
  "pwa-offline": {
    title: "Офлайн-режим PWA",
    lead: "Какие данные остаются на устройстве и когда снова доступны действия с записью.",
    load: () => import('./pwa-offline-source').then(mod => mod.pwaOfflineOperations),
  },
  "report-export": {
    title: "Экспорт отчётов",
    lead: "Как недельный журнал становится PDF и попадает в браузер или Telegram Mini App.",
    load: () => import('./report-export-source').then(mod => mod.reportExportOperations),
  },
  "request-attachments": {
    title: "Вложения в заявках",
    lead: "Как файл проходит проверку, кто может его открыть и когда сервер очищает байты.",
    load: () => import('./request-attachments-source').then(mod => mod.requestAttachmentOperations),
  },
  "geo-checkin": {
    title: "Геоотметка",
    lead: "Как приложение получает позицию и как сервер решает, записать присутствие или запросить подтверждение.",
    load: () => import('./geo-checkin-source').then(mod => mod.geoCheckinOperations),
  },
  "request-lifecycle": {
    title: "Жизненный цикл заявки",
    lead: "От подачи уважительной причины или поздней отметки до решения и записи в журнале.",
    load: () => import('./request-lifecycle-source').then(mod => mod.requestLifecycleOperations),
  },
  "lesson-changes": {
    title: "Перенос и отмена пары",
    lead: "От решения в журнале до нового расписания, изменений посещаемости и уведомлений.",
    load: () => import('./lesson-changes-source').then(mod => mod.lessonChangeOperations),
  },
  "homework-lifecycle": {
    title: "Домашние задания",
    lead: "Как староста выдаёт задание, студент отмечает выполнение, а перенос пары меняет срок.",
    load: () => import('./homework-lifecycle-source').then(mod => mod.homeworkLifecycleOperations),
  },
  "schedule-constructor": {
    title: "Конструктор расписания",
    lead: "Как шаблон превращается в пары на семестр и что меняется, если переставить слот.",
    load: () => import('./schedule-constructor-source').then(mod => mod.scheduleConstructorOperations),
  },
  "semester-lifecycle": {
    title: "Жизненный цикл семестра",
    lead: "Как администратор создаёт и активирует семестр, сохраняет его в архиве и возвращает к работе.",
    load: () => import('./semester-lifecycle-source').then(mod => mod.semesterLifecycleOperations),
  },
  "group-lifecycle": {
    title: "Жизненный цикл группы",
    lead: "Как администратор создаёт группу, назначает старосту, переводит студентов и сохраняет историю.",
    load: () => import('./group-lifecycle-source').then(mod => mod.groupLifecycleOperations),
  },
  "teacher-assignments": {
    title: "Назначения преподавателей",
    lead: "Как староста назначает преподавателя, что меняется при замене и кто видит историю журнала.",
    load: () => import('./teacher-assignments-source').then(mod => mod.teacherAssignmentOperations),
  },
  "subject-lifecycle": {
    title: "Жизненный цикл предмета",
    lead: "Как староста создаёт предмет, меняет типы занятий и проверяет последствия удаления.",
    load: () => import('./subject-lifecycle-source').then(mod => mod.subjectLifecycleOperations),
  },
  "user-lifecycle": {
    title: "Жизненный цикл пользователя",
    lead: "Как администратор создаёт аккаунт, архивирует его и возвращает доступ с нужными ролями.",
    load: () => import('./user-lifecycle-source').then(mod => mod.userLifecycleOperations),
  },
  "notification-lifecycle": {
    title: "Жизненный цикл уведомления",
    lead: "Кто получает событие, как работают каналы доставки и где сохраняется прочтение.",
    load: () => import('./notification-lifecycle-source').then(mod => mod.notificationLifecycleOperations),
  },
  "lesson-lifecycle": {
    title: "Жизненный цикл пары",
    lead: "Как пара появляется в расписании, когда студент может отметиться и что меняется после закрытия.",
    load: () => import('./lesson-lifecycle-source').then(mod => mod.lessonLifecycleOperations),
  },
  "attendance-mark-lifecycle": {
    title: "Жизненный цикл отметки",
    lead: "Как отметка появляется в журнале, кто может её исправить и как изменение отражается в истории и статистике.",
    load: () => import('./attendance-mark-lifecycle-source').then(mod => mod.attendanceMarkLifecycleOperations),
  },
  "attendance-statistics": {
    title: "Статистика посещаемости",
    lead: "Какие пары и отметки входят в проценты, как действуют фильтры и что видит каждая роль.",
    load: () => import('./attendance-statistics-source').then(mod => mod.attendanceStatisticsOperations),
  },
  "student-ranking": {
    title: "Рейтинг студентов",
    lead: "Как считается место в группе, почему места могут совпасть и кому доступен рейтинг.",
    load: () => import('./student-ranking-source').then(mod => mod.studentRankingOperations),
  },
  "attendance-journal": {
    title: "Журнал посещаемости",
    lead: "Что видят староста и преподаватель, как собирается таблица и откуда берутся график и проценты.",
    load: () => import('./attendance-journal-source').then(mod => mod.attendanceJournalOperations),
  },
  "attendance-journal-export": {
    title: "Выгрузка журнала посещаемости",
    lead: "Что меняют выбранные недели, типы и пары и какие данные попадают в файл.",
    load: () => import('./attendance-journal-export-source').then(mod => mod.attendanceJournalExportOperations),
  },
  "campus-map": {
    title: "Карта кампуса",
    lead: "Как публикуется схема этажа, что хранится в каталоге и какой файл открывает приложение.",
    load: () => import('./campus-map-source').then(mod => mod.campusMapOperations),
  },
  "campus-map-statistics": {
    title: "Статистика карты",
    lead: "Когда засчитывается открытие схемы, что происходит при повторе и где видны счётчики.",
    load: () => import('./campus-map-statistics-source').then(mod => mod.campusMapStatisticsOperations),
  },
  "headman-assistants": {
    title: "Помощники старосты",
    lead: "Как староста выдаёт права, меняет их и отзывает доступ.",
    load: () => import('./headman-assistants-source').then(mod => mod.headmanAssistantsOperations),
  },
  "lesson-request-access": {
    title: "Доступ к паре и заявке",
    lead: "Какие проверки проходят студент, помощник, староста и преподаватель.",
    load: () => import('./lesson-request-access-source').then(mod => mod.lessonRequestAccessOperations),
  },
  "admin-overview": {
    title: "Обзор администратора",
    lead: "Откуда берутся текущие числа, сравнение со вчерашним днём и точки графика.",
    load: () => import('./admin-overview-source').then(mod => mod.adminOverviewOperations),
  },
  "schedule-by-role": {
    title: "Расписание по ролям",
    lead: "Одна пара в четырёх сценариях чтения. Студент видит свой день, староста открывает журнал, преподаватель сверяет занятия с назначениями, администратор читает расписание группы через API.",
    load: () => import('./schedule-by-role-source').then(mod => mod.scheduleByRoleOperations),
  },
  "student-transfer-history": {
    title: "История после перевода студента",
    lead: "При смене группы дата занятия по-прежнему определяет состав журнала. Личная история и рейтинг применяют разные правила.",
    load: () => import('./student-transfer-history-source').then(mod => mod.studentTransferHistoryOperations),
  },
  "notification-actions": {
    title: "Переходы из уведомлений",
    lead: "Кнопка в уведомлении указывает на пару, задание или заявку. Приложение проверяет доступ и загружает объект заново.",
    load: () => import('./notification-actions-source').then(mod => mod.notificationActionOperations),
  },
  "telegram-bot": {
    title: "Telegram-бот",
    lead: "В личном чате бот строит меню по доступным ролям и может открыть конкретный объект в Mini App.",
    load: () => import('./telegram-bot-source').then(mod => mod.telegramBotOperations),
  },
  "web-push-subscription": {
    title: "Подписка Web Push",
    lead: "Разрешение браузера, подписка устройства и настройки доставки действуют на разных этапах.",
    load: () => import('./web-push-subscription-source').then(mod => mod.webPushSubscriptionOperations),
  },
  "profile-device-settings": {
    title: "Профиль и устройство",
    lead: "Аватар относится к аккаунту, тема сохраняется для браузерного контекста, разрешениями управляет устройство.",
    load: () => import('./profile-device-settings-source').then(mod => mod.profileDeviceSettingsOperations),
  },
  "account-sessions-history": {
    title: "Сеансы и история аккаунта",
    lead: "Можно увидеть действующие входы, завершить один или все и проверить события безопасности.",
    load: () => import('./account-sessions-history-source').then(mod => mod.accountSessionsHistoryOperations),
  },
  "request-diagnostics": {
    title: "Диагностика и согласованность",
    lead: "Как проследить запрос, проверить итог операции и объяснить расхождение между экраном и данными.",
    load: () => import('./request-diagnostics-source').then(mod => mod.requestDiagnosticsOperations),
  },
  "report-data-contract": {
    title: "Данные отчёта и контракт",
    lead: "Что определяет состав отчёта, как формируется файл и как приложение проверяет ответ сервера.",
    load: () => import('./report-data-contract-source').then(mod => mod.reportDataContractOperations),
  },
  "recovery-retention-verification": {
    title: "Восстановление, хранение и проверки",
    lead: "Как проверяют копию и восстановленный стенд, когда истекает доступ к данным и что покрывают тесты.",
    load: () => import('./recovery-retention-verification-source').then(mod => mod.recoveryRetentionVerificationOperations),
  },
}

export interface LoadedChapter extends Omit<Chapter, 'load'> {
  operations: readonly StoryOperation[]
}

export function loadTopic(topic: Topic): Promise<LoadedChapter[]> {
  return Promise.all(topic.chapters.map(async (id) => {
    const { load, ...heading } = chapters[id]
    return { id, ...heading, operations: await load() }
  }))
}
