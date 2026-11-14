import type { StoryOperation } from './story-source'

export const notificationActionOperations: StoryOperation[] = [
  {
    "id": "notification-actions-contract",
    "title": "От события к действию",
    "description": "Событие homework.published становится HOMEWORK_PUBLISHED. Для задания 901 каталог передаёт ID и дату пары 16 ноября. У действия по заявке есть ID и вид заявки. При переносе пары действие указывает на новое занятие из target. Снятое ДЗ может остаться в истории без кнопки перехода.",
    "frames": [
      {
        "title": "Тип события и открытие целевой пары",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationCatalog.java",
        "language": "java",
        "lines": [
          [
            384,
            "                            : Optional.empty();"
          ],
          [
            385,
            "            case \"homework.published\" -> Optional.of(NotificationType.HOMEWORK_PUBLISHED);"
          ],
          [
            386,
            "            case \"homework.updated\" -> Optional.of(NotificationType.HOMEWORK_UPDATED);"
          ],
          [
            668,
            "    public NotificationInboxDto.Action action("
          ],
          [
            669,
            "            NotificationType type, Map<String, Object> payload, String role) {"
          ],
          [
            670,
            "        if (payload.get(\"target\") instanceof Map<?, ?> target"
          ],
          [
            671,
            "                && Set.of(NotificationType.LESSON_TRANSFERRED, NotificationType.LESSON_UPDATED)"
          ],
          [
            672,
            "                        .contains(type)) {"
          ],
          [
            673,
            "            String id = positiveId(target.get(\"lesson_id\"));"
          ],
          [
            674,
            "            return id == null"
          ],
          [
            675,
            "                    ? null"
          ],
          [
            676,
            "                    : new NotificationInboxDto.Action("
          ],
          [
            677,
            "                            \"lesson\","
          ],
          [
            678,
            "                            role,"
          ],
          [
            679,
            "                            id,"
          ],
          [
            680,
            "                            date(target.get(\"lesson_date\")),"
          ],
          [
            681,
            "                            null,"
          ],
          [
            682,
            "                            null,"
          ],
          [
            683,
            "                            null,"
          ],
          [
            684,
            "                            null,"
          ],
          [
            685,
            "                            null);"
          ]
        ]
      },
      {
        "title": "Идентификатор заявки и дата задания",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationCatalog.java",
        "language": "java",
        "lines": [
          [
            698,
            "        if (type.name().startsWith(\"EXCUSE\") || type.name().startsWith(\"LATE_CHECKIN\")) {"
          ],
          [
            699,
            "            Object rawId ="
          ],
          [
            700,
            "                    payload.get(type.name().startsWith(\"EXCUSE\") ? \"ticket_id\" : \"request_id\");"
          ],
          [
            701,
            "            String id = rawId instanceof String value ? value : null;"
          ],
          [
            702,
            "            if (id == null || !id.matches(\"[A-Za-z0-9_:-]{1,128}\")) return null;"
          ],
          [
            703,
            "            return new NotificationInboxDto.Action("
          ],
          [
            704,
            "                    \"request\","
          ],
          [
            705,
            "                    role,"
          ],
          [
            706,
            "                    null,"
          ],
          [
            707,
            "                    null,"
          ],
          [
            708,
            "                    id,"
          ],
          [
            709,
            "                    type.name().startsWith(\"EXCUSE\") ? \"EXCUSE\" : \"LATE_CHECKIN\","
          ],
          [
            710,
            "                    null,"
          ],
          [
            711,
            "                    null,"
          ],
          [
            712,
            "                    null);"
          ],
          [
            713,
            "        }"
          ],
          [
            714,
            "        if (type == NotificationType.HOMEWORK_WITHDRAWN"
          ],
          [
            715,
            "                || (\"TEACHER\".equals(role) && type.name().startsWith(\"HOMEWORK_\"))) return null;"
          ],
          [
            736,
            "            // The student homework view uses the exact lesson date to look up the assignment."
          ],
          [
            737,
            "            return id == null || (\"STUDENT\".equals(role) && !studentHomeworkDate(date))"
          ],
          [
            738,
            "                    ? null"
          ],
          [
            739,
            "                    : new NotificationInboxDto.Action("
          ],
          [
            740,
            "                            \"homework\", role, null, date, null, null, id, null, null);"
          ],
          [
            749,
            "        return lesson == null"
          ],
          [
            750,
            "                ? null"
          ],
          [
            751,
            "                : new NotificationInboxDto.Action("
          ],
          [
            752,
            "                        \"lesson\", role, lesson, date, null, null, null, null, null);"
          ]
        ]
      },
      {
        "title": "Типы действий в мобильном клиенте",
        "path": "frontends/mobile-core/src/features/notifications/notifications-v2-models.ts",
        "language": "typescript",
        "lines": [
          [
            94,
            "export type NotificationSignedRole = 'STUDENT' | 'HEADMAN' | 'TEACHER' | 'ADMIN'"
          ],
          [
            95,
            "export type NotificationAction ="
          ],
          [
            96,
            "  | { kind: 'lesson'; role: NotificationSignedRole; lessonId: string; lessonDate?: string }"
          ],
          [
            97,
            "  | {"
          ],
          [
            98,
            "      kind: 'request'"
          ],
          [
            99,
            "      role: NotificationSignedRole"
          ],
          [
            100,
            "      requestId: string"
          ],
          [
            101,
            "      requestKind: 'EXCUSE' | 'LATE_CHECKIN'"
          ],
          [
            102,
            "    }"
          ],
          [
            103,
            "  | { kind: 'homework'; role: NotificationSignedRole; homeworkId: string; lessonDate?: string }"
          ],
          [
            104,
            "  | { kind: 'group'; role: NotificationSignedRole; groupId: string }"
          ],
          [
            105,
            "  | { kind: 'homework-week'; role: NotificationSignedRole; weekStart: string }"
          ]
        ]
      },
      {
        "title": "Поля элемента ленты PWA",
        "language": "json",
        "example": [
          "{",
          "  \"type\": \"HOMEWORK_PUBLISHED\",",
          "  \"action\": {",
          "    \"kind\": \"homework\",",
          "    \"role\": \"STUDENT\",",
          "    \"homeworkId\": \"901\",",
          "    \"lessonDate\": \"2026-11-16\"",
          "  }",
          "}"
        ]
      }
    ]
  },
  {
    "id": "notification-actions-identity",
    "title": "Сеанс и выбранная роль",
    "description": "Ссылка задаёт цель перехода. Приложение ждёт аккаунт и выбранную роль, а при несовпадении предлагает переключить роль через профиль. Если меняется пользователь, переход для прежнего пользователя сбрасывается. Экран сверяет поколение контекста, чтобы запоздалый ответ не открыл объект в новой сессии.",
    "frames": [
      {
        "title": "Переход ждёт нужный аккаунт и роль",
        "path": "frontends/mobile-core/src/features/notifications/notifications-launch.ts",
        "language": "typescript",
        "lines": [
          [
            94,
            "  async consume("
          ],
          [
            95,
            "    userId: string | null,"
          ],
          [
            96,
            "    role: NotificationSignedRole | null,"
          ],
          [
            97,
            "    open: (action: NotificationLaunchAction) => Promise<boolean>,"
          ],
          [
            98,
            "  ): Promise<'waiting' | 'role-required' | 'opened' | 'unavailable' | 'empty'> {"
          ],
          [
            99,
            "    if (!this.action) return 'empty'"
          ],
          [
            100,
            "    if (!userId || !role || this.running) return 'waiting'"
          ],
          [
            101,
            "    if (this.boundUser && this.boundUser !== userId) {"
          ],
          [
            102,
            "      this.clear()"
          ],
          [
            103,
            "      return 'unavailable'"
          ],
          [
            104,
            "    }"
          ],
          [
            105,
            "    this.boundUser = userId"
          ],
          [
            106,
            "    if (role !== this.action.role) return 'role-required'"
          ],
          [
            107,
            "    const action = this.action"
          ],
          [
            108,
            "    this.action = null"
          ],
          [
            109,
            "    this.running = true"
          ],
          [
            110,
            "    try {"
          ],
          [
            111,
            "      return (await open(action)) ? 'opened' : 'unavailable'"
          ],
          [
            112,
            "    } catch {"
          ],
          [
            113,
            "      return 'unavailable'"
          ],
          [
            114,
            "    } finally {"
          ],
          [
            115,
            "      this.running = false"
          ]
        ]
      },
      {
        "title": "Mini App предлагает выбрать роль",
        "path": "frontends/tma-vue/src/App.vue",
        "language": "typescript",
        "lines": [
          [
            671,
            "  async ([userId, role, , visible, isOffline]) => {"
          ],
          [
            672,
            "    if (!visible || isOffline) return;"
          ],
          [
            673,
            "    await nextTick();"
          ],
          [
            674,
            "    const result = await notificationLaunch.consume("
          ],
          [
            675,
            "      userId ?? null,"
          ],
          [
            676,
            "      role ?? null,"
          ],
          [
            677,
            "      openNotificationAction,"
          ],
          [
            678,
            "    );"
          ],
          [
            679,
            "    if (result === 'role-required')"
          ],
          [
            680,
            "      notificationLaunchNotice.value ="
          ],
          [
            681,
            "        'Для этого уведомления выбери нужную роль через свой профиль.';"
          ],
          [
            682,
            "    else if (result === 'unavailable')"
          ],
          [
            683,
            "      notificationLaunchNotice.value ="
          ],
          [
            684,
            "        'Объект уведомления сейчас недоступен. Ты можешь открыть свою историю уведомлений.';"
          ],
          [
            685,
            "    else if (result === 'opened') notificationLaunchNotice.value = null;"
          ]
        ]
      },
      {
        "title": "Цель и поколение проверяются в контексте студента",
        "path": "frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue",
        "language": "typescript",
        "lines": [
          [
            699,
            "function isCurrentNotificationTargetRequest(request: NotificationTargetIntent): boolean {"
          ],
          [
            700,
            "  const currentScope = scope.value;"
          ],
          [
            701,
            "  return ("
          ],
          [
            702,
            "    !disposed &&"
          ],
          [
            703,
            "    props.notificationTargetIntent?.requestId === request.requestId &&"
          ],
          [
            704,
            "    props.notificationTargetIntent.generation === request.generation &&"
          ],
          [
            705,
            "    request.ownerKey === props.ownerKey &&"
          ],
          [
            706,
            "    props.ownerKey !== null &&"
          ],
          [
            707,
            "    props.api !== null &&"
          ],
          [
            708,
            "    !offline.value &&"
          ],
          [
            709,
            "    currentScope?.activeRole === 'STUDENT' &&"
          ],
          [
            710,
            "    currentScope.resetGeneration === request.generation &&"
          ],
          [
            711,
            "    ownerIdentity.value === studentFeatureScopeIdentity(currentScope)"
          ],
          [
            712,
            "  );"
          ],
          [
            713,
            "}"
          ],
          [
            714,
            ""
          ],
          [
            715,
            "function isActiveNotificationTargetRequest(request: NotificationTargetIntent): boolean {"
          ],
          [
            716,
            "  const state = notificationTargetState.value;"
          ],
          [
            717,
            "  return ("
          ],
          [
            718,
            "    isCurrentNotificationTargetRequest(request) &&"
          ],
          [
            719,
            "    route.value.id === notificationTargetRoute(request.target).id &&"
          ],
          [
            720,
            "    state?.requestId === request.requestId &&"
          ],
          [
            721,
            "    state.target.kind === request.target.kind"
          ],
          [
            722,
            "  );"
          ]
        ]
      }
    ]
  },
  {
    "id": "notification-actions-read",
    "title": "Загрузка объекта перед показом",
    "description": "Студент открывает ДЗ в списке за точную дату. Заявка загружается по ID и виду. Для занятия экран перечитывает доступную посещаемость и выбирает найденную пару по ID. Удалённый или недоступный объект получает отдельное состояние. При сбое загрузки интерфейс предлагает повторить попытку.",
    "frames": [
      {
        "title": "Дата и ID задания должны совпасть",
        "path": "frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue",
        "language": "typescript",
        "lines": [
          [
            742,
            "  void (async () => {"
          ],
          [
            743,
            "    try {"
          ],
          [
            744,
            "      const feed = await homework.openDate(target.lessonDate);"
          ],
          [
            745,
            "      if (!isActiveNotificationTargetRequest(request)) return;"
          ],
          [
            746,
            "      if ("
          ],
          [
            747,
            "        !feed.items.some("
          ],
          [
            748,
            "          (item) => item.id === target.homeworkId && item.lessonDate === target.lessonDate,"
          ],
          [
            749,
            "        )"
          ],
          [
            750,
            "      ) {"
          ],
          [
            751,
            "        notificationTargetState.value = {"
          ],
          [
            752,
            "          kind: 'homework',"
          ],
          [
            753,
            "          requestId: request.requestId,"
          ],
          [
            754,
            "          target,"
          ],
          [
            755,
            "          status: 'unavailable',"
          ],
          [
            756,
            "          message: 'Это задание удалено или больше недоступно в твоей учебной группе.',"
          ],
          [
            757,
            "        };"
          ],
          [
            758,
            "        return;"
          ],
          [
            759,
            "      }"
          ],
          [
            766,
            "    } catch (error) {"
          ],
          [
            767,
            "      if (!isActiveNotificationTargetRequest(request)) return;"
          ],
          [
            768,
            "      const unavailable = isUnavailableHomeworkNotificationTarget(error, target.lessonDate);"
          ],
          [
            769,
            "      notificationTargetState.value = unavailable"
          ],
          [
            770,
            "        ? {"
          ],
          [
            771,
            "            kind: 'homework',"
          ],
          [
            772,
            "            requestId: request.requestId,"
          ],
          [
            773,
            "            target,"
          ],
          [
            774,
            "            status: 'unavailable',"
          ],
          [
            775,
            "            message: 'Это задание удалено или больше недоступно в твоей учебной группе.',"
          ],
          [
            776,
            "          }"
          ],
          [
            777,
            "        : {"
          ],
          [
            778,
            "            kind: 'homework',"
          ],
          [
            779,
            "            requestId: request.requestId,"
          ],
          [
            780,
            "            target,"
          ],
          [
            781,
            "            status: 'error',"
          ],
          [
            782,
            "            message: 'Не удалось загрузить это задание. Проверь подключение и попробуй ещё раз.',"
          ],
          [
            783,
            "          };"
          ]
        ]
      },
      {
        "title": "Карточка заявки и недоступность при 403 или 404",
        "path": "frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue",
        "language": "typescript",
        "lines": [
          [
            798,
            "  void (async () => {"
          ],
          [
            799,
            "    try {"
          ],
          [
            800,
            "      const detail = await requests.loadTargetDetail(target.requestId, target.requestKind);"
          ],
          [
            801,
            "      if (!isActiveNotificationTargetRequest(request)) return;"
          ],
          [
            802,
            "      notificationTargetState.value = {"
          ],
          [
            803,
            "        kind: 'request',"
          ],
          [
            804,
            "        requestId: request.requestId,"
          ],
          [
            805,
            "        target,"
          ],
          [
            806,
            "        status: 'available',"
          ],
          [
            807,
            "        detail,"
          ],
          [
            808,
            "      };"
          ],
          [
            809,
            "    } catch (error) {"
          ],
          [
            810,
            "      if (!isActiveNotificationTargetRequest(request)) return;"
          ],
          [
            811,
            "      const unavailable ="
          ],
          [
            812,
            "        (error instanceof RequestsError && error.code === 'UNAVAILABLE') ||"
          ],
          [
            813,
            "        (error instanceof StudentApiError &&"
          ],
          [
            814,
            "          (error.response.status === 403 || error.response.status === 404));"
          ],
          [
            815,
            "      if (error instanceof StudentApiError && error.response.status === 401) {"
          ],
          [
            816,
            "        handleRequestsError(error);"
          ],
          [
            817,
            "        return;"
          ],
          [
            818,
            "      }"
          ],
          [
            819,
            "      notificationTargetState.value = unavailable"
          ],
          [
            820,
            "        ? {"
          ],
          [
            821,
            "            kind: 'request',"
          ],
          [
            822,
            "            requestId: request.requestId,"
          ],
          [
            823,
            "            target,"
          ],
          [
            824,
            "            status: 'unavailable',"
          ],
          [
            825,
            "            message: 'Эта заявка удалена или больше недоступна.',"
          ]
        ]
      },
      {
        "title": "Занятие заново ищется в доступных данных",
        "path": "frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue",
        "language": "typescript",
        "lines": [
          [
            849,
            "    try {"
          ],
          [
            850,
            "      if (!scope.value?.semesterId) {"
          ],
          [
            851,
            "        notificationTargetState.value = {"
          ],
          [
            852,
            "          kind: 'lesson',"
          ],
          [
            853,
            "          requestId: request.requestId,"
          ],
          [
            854,
            "          target,"
          ],
          [
            855,
            "          status: 'unavailable',"
          ],
          [
            856,
            "          message: 'Это занятие нельзя найти в текущем учебном семестре.',"
          ],
          [
            857,
            "        };"
          ],
          [
            858,
            "        return;"
          ],
          [
            859,
            "      }"
          ],
          [
            860,
            "      await attendance.query.refetch({ throwOnError: true });"
          ],
          [
            861,
            "      if (!isActiveNotificationTargetRequest(request)) return;"
          ],
          [
            862,
            "      const currentData = attendance.data.value;"
          ],
          [
            863,
            "      const lesson = currentData ? findAttendanceLesson(currentData, target.lessonId) : null;"
          ],
          [
            864,
            "      if (!lesson) {"
          ],
          [
            865,
            "        notificationTargetState.value = {"
          ],
          [
            866,
            "          kind: 'lesson',"
          ],
          [
            867,
            "          requestId: request.requestId,"
          ],
          [
            868,
            "          target,"
          ],
          [
            869,
            "          status: 'unavailable',"
          ],
          [
            870,
            "          message: 'Это занятие больше не входит в твою доступную посещаемость.',"
          ],
          [
            871,
            "        };"
          ],
          [
            872,
            "        return;"
          ],
          [
            873,
            "      }"
          ],
          [
            874,
            "      attendanceSelectedDate.value = lesson.date;"
          ],
          [
            875,
            "      attendanceMode.value = 'days';"
          ],
          [
            876,
            "      attendanceActionLessonId.value = null;"
          ],
          [
            877,
            "      attendanceExpandedSubjectId.value = null;"
          ],
          [
            878,
            "      notificationTargetState.value = {"
          ],
          [
            879,
            "        kind: 'lesson',"
          ],
          [
            880,
            "        requestId: request.requestId,"
          ],
          [
            881,
            "        target,"
          ],
          [
            882,
            "        status: 'available',"
          ],
          [
            883,
            "        lessonDate: lesson.date,"
          ],
          [
            884,
            "      };"
          ]
        ]
      }
    ]
  }
]
