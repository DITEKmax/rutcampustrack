interface FlowNode {
  id: string
  label: string
  detail: string
}

interface FlowStage {
  incoming?: string
  nodes: readonly [FlowNode, ...FlowNode[]]
}

export interface TopicFlow {
  title: string
  context: string
  stages: readonly [FlowStage, ...FlowStage[]]
}

export const topicFlows: Readonly<Partial<Record<string, TopicFlow>>> = {
  'security-qr': {
    title: 'Как QR превращается в сессию',
    context: 'Web и Mini App',
    stages: [
      { nodes: [{ id: 'browser', label: 'Браузер показывает QR', detail: 'Web создаёт заявку на вход. QR содержит данные для сканирования, а секрет для обмена остаётся в исходном браузере.' }] },
      { incoming: 'Сканирование', nodes: [{ id: 'preview', label: 'Mini App показывает заявку', detail: 'Приложение с действующей сессией показывает браузер и срок заявки. Пользователь выбирает, подтверждать ли вход.' }] },
      { incoming: 'Решение', nodes: [
        { id: 'confirmed', label: 'Подтвердить', detail: 'Auth сохраняет confirmed. Браузер использует свой секрет, чтобы обменять подтверждение на одну браузерную сессию.' },
        { id: 'rejected', label: 'Отклонить', detail: 'Auth сохраняет rejected. Исходный браузер получает этот статус при опросе, обмен на сессию недоступен.' },
      ] },
    ],
  },
  'geo-checkin-outcomes': {
    title: 'Что вернёт геоотметка',
    context: 'Условный радиус 400 м',
    stages: [
      { nodes: [{ id: 'request', label: 'Студент отправляет отметку', detail: 'При открытом окне отметки приложение отправляет координаты либо причину недоступности позиции.' }] },
      { incoming: 'POST', nodes: [{ id: 'geofence', label: 'Attendance проверяет зону', detail: 'Если координаты есть, сервер сравнивает расстояние до кампуса с настроенным радиусом. Точка на границе проходит сравнение.' }] },
      { incoming: 'HTTP 200', nodes: [
        { id: 'inside', label: '399 м · присутствие', detail: 'Сервер сохраняет PRESENT с источником STUDENT_GEO.' },
        { id: 'outside', label: '401 м · заявка', detail: 'Сервер создаёт или переиспользует заявку AUTO_GEO_FAILURE на эту пару. Результат — PENDING_CONFIRMATION.' },
        { id: 'unavailable', label: 'Нет позиции · заявка', detail: 'Ответ PENDING_CONFIRMATION содержит ожидающую заявку и retryAt. Поле attendance остаётся пустым.' },
      ] },
    ],
  },
  'semester-lifecycle-activate': {
    title: 'Путь семестра от создания до восстановления',
    context: 'Панель администратора',
    stages: [
      { nodes: [{ id: 'created', label: 'Неактивный', detail: 'Администратор задаёт тип, учебный год и диапазон дат. Academic проверяет даты и сохраняет новый семестр как неактивный.' }] },
      { incoming: 'Активация', nodes: [{ id: 'active', label: 'Текущий', detail: 'Команда делает семестр текущим либо планирует активацию на дату начала. До этой даты текущий семестр продолжает действовать.' }] },
      { incoming: 'Архивация', nodes: [{ id: 'archived', label: 'Архивный', detail: 'Сервисы подтверждают переход. В архиве сохраняются расписание, отметки, заявки и задания.' }] },
      { incoming: 'Возврат', nodes: [{ id: 'restored', label: 'Снова неактивный', detail: 'После восстановления администратор может отдельно сделать семестр текущим. Для семестра с уже истёкшим сроком сначала нужно расширить диапазон дат.' }] },
    ],
  },
  'realtime-rabbit': {
    title: 'Одно событие в нескольких очередях',
    context: 'RabbitMQ · rut-uit.events',
    stages: [
      { nodes: [{ id: 'outbox', label: 'Outbox Attendance', detail: 'При создании заявки Attendance сохраняет late_checkin.requested в одной транзакции с результатом геоотметки.' }] },
      { incoming: 'Публикация', nodes: [{ id: 'rabbitmq', label: 'RabbitMQ fanout', detail: 'Диспетчер отправляет запись в rut-uit.events с пустым routing key. Брокер копирует сообщение в каждую привязанную очередь.' }] },
      { incoming: 'Копии', nodes: [
        { id: 'events', label: 'События Notification Web', detail: 'Очередь notification-web.events получает свою копию события. Обработчики применяют правила соответствующего типа события.' },
        { id: 'history', label: 'История Notification Web', detail: 'Очередь notification-web.history получает копию. После записи истории разрешённый получатель получает приватный STOMP-сигнал, по которому PWA перечитывает ленту.' },
        { id: 'bot', label: 'Telegram-бот', detail: 'Очередь notification-bot.events получает свою копию. Отправка зависит от типа события, настроек и актуальных получателей.' },
      ] },
    ],
  },
}
