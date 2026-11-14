import type { StoryOperation } from './story-source'

export const telegramBotOperations: StoryOperation[] = [
  {
    "id": "telegram-bot-menu",
    "title": "Личный чат и привязка",
    "description": "Команда /start работает в личном чате. Без привязки бот показывает общее меню и предлагает обратиться к старосте. С привязанным аккаунтом он заново получает действующие роли. ДЗ появляется при одной студенческой группе, «Учёт группы» — у старосты, «Мои пары» — у преподавателя.",
    "frames": [
      {
        "title": "Команда доступна в личном чате",
        "path": "services/notification-bot/bot/handlers/start.py",
        "language": "python",
        "lines": [
          [
            102,
            "@start_router.message(Command(\"start\"))"
          ],
          [
            103,
            "async def cmd_start("
          ],
          [
            104,
            "    message: Message, academic_client, prefs_client, keyboard_sync=None, credential_cards=None, bot_menu_client=None"
          ],
          [
            105,
            ") -> None:"
          ],
          [
            106,
            "    \"\"\"Handle /start command — account linking (D-02, D-03).\"\"\""
          ],
          [
            107,
            "    if not await _private_only(message):"
          ],
          [
            108,
            "        return"
          ],
          [
            109,
            "    telegram_id = private_sender(message)"
          ],
          [
            110,
            "    async with keyboard_sync.publication(telegram_id) if keyboard_sync is not None else nullcontext():"
          ],
          [
            111,
            "        await _publish_start(message, telegram_id, keyboard_sync, credential_cards, bot_menu_client)"
          ]
        ]
      },
      {
        "title": "Общее меню и меню действующих ролей",
        "path": "services/notification-bot/bot/handlers/start.py",
        "language": "python",
        "lines": [
          [
            123,
            "    keyboard = main_keyboard()"
          ],
          [
            124,
            "    try:"
          ],
          [
            125,
            "        if bot_menu_client is None:"
          ],
          [
            126,
            "            raise RuntimeError(\"Own Auth profile capability unavailable\")"
          ],
          [
            127,
            "        response = await bot_menu_client.own_profile(telegram_id)"
          ],
          [
            128,
            "        if not response.found:"
          ],
          [
            129,
            "            # D-03: unknown telegram_id"
          ],
          [
            130,
            "            await message.answer("
          ],
          [
            131,
            "                \"⚠️ <b>Аккаунт не найден</b>\\n\\n\""
          ],
          [
            132,
            "                \"Ваш Telegram пока не привязан к системе.\\n\\n\""
          ],
          [
            133,
            "                \"Обратитесь к старосте вашей группы, чтобы он привязал ваш аккаунт.\","
          ],
          [
            134,
            "                reply_markup=keyboard if keyboard_pending else None,"
          ],
          [
            135,
            "                parse_mode=\"HTML\","
          ],
          [
            136,
            "            )"
          ],
          [
            137,
            "            if keyboard_sync is not None and keyboard_pending:"
          ],
          [
            138,
            "                await keyboard_sync.mark_common_keyboard(telegram_id)"
          ],
          [
            139,
            "            return"
          ],
          [
            140,
            ""
          ],
          [
            141,
            "        # D-02: a found profile needs positive current authority before any own data/card."
          ],
          [
            142,
            "        scope = await _require_profile_scope(bot_menu_client, telegram_id, response)"
          ],
          [
            143,
            "        options = scope.keyboard_options"
          ],
          [
            144,
            "        keyboard = main_keyboard(**options)"
          ]
        ]
      },
      {
        "title": "Опции меню и фильтр активных ролей",
        "path": "services/notification-bot/bot/services/bot_menu.py",
        "language": "python",
        "lines": [
          [
            31,
            "    @property"
          ],
          [
            32,
            "    def student_groups(self) -> frozenset[int]:"
          ],
          [
            33,
            "        return self.groups_for(\"STUDENT\")"
          ],
          [
            34,
            ""
          ],
          [
            35,
            "    def groups_for(self, role: str) -> frozenset[int]:"
          ],
          [
            36,
            "        return frozenset(grant.group_id for grant in self.grants if grant.role == role and grant.group_id > 0)"
          ],
          [
            37,
            ""
          ],
          [
            38,
            "    @property"
          ],
          [
            39,
            "    def homework_enabled(self) -> bool:"
          ],
          [
            40,
            "        return len(self.student_groups) == 1"
          ],
          [
            41,
            ""
          ],
          [
            42,
            "    @property"
          ],
          [
            43,
            "    def headman_enabled(self) -> bool:"
          ],
          [
            44,
            "        return bool(self.groups_for(\"HEADMAN\"))"
          ],
          [
            45,
            ""
          ],
          [
            46,
            "    @property"
          ],
          [
            47,
            "    def teacher_enabled(self) -> bool:"
          ],
          [
            48,
            "        return any(grant.role == \"TEACHER\" for grant in self.grants)"
          ],
          [
            49,
            ""
          ],
          [
            50,
            "    @property"
          ],
          [
            51,
            "    def keyboard_options(self) -> dict[str, bool]:"
          ],
          [
            52,
            "        return dict("
          ],
          [
            53,
            "            homework_enabled=self.homework_enabled,"
          ],
          [
            54,
            "            headman_enabled=self.headman_enabled,"
          ],
          [
            55,
            "            teacher_enabled=self.teacher_enabled,"
          ],
          [
            56,
            "        )"
          ],
          [
            125,
            "        grants = tuple("
          ],
          [
            126,
            "            sorted("
          ],
          [
            127,
            "                {"
          ],
          [
            128,
            "                    grant"
          ],
          [
            129,
            "                    for grant in result.grants"
          ],
          [
            130,
            "                    if isinstance(grant, BotGrant)"
          ],
          [
            131,
            "                    and grant.status == \"ACTIVE\""
          ],
          [
            132,
            "                    and grant.role in {\"STUDENT\", \"HEADMAN\", \"TEACHER\", \"ADMIN\"}"
          ],
          [
            133,
            "                    and type(grant.group_id) is int"
          ],
          [
            134,
            "                    and grant.group_id >= 0"
          ],
          [
            135,
            "                },"
          ],
          [
            136,
            "                key=lambda grant: (grant.role, grant.group_id),"
          ],
          [
            137,
            "            )"
          ],
          [
            138,
            "        )"
          ],
          [
            139,
            "        return MenuScope(result.user_id, result.telegram_binding_version, result.roles_version, grants)"
          ]
        ]
      }
    ]
  },
  {
    "id": "telegram-bot-reads",
    "title": "ДЗ, пары и учёт группы",
    "description": "Студент может запросить ДЗ на сегодня или на ближайшие семь дней. Бот сверяет привязку и роль при чтении. Преподавателю «Мои пары» возвращает свой день по Москве. Для старосты «Учёт группы» показывает текущую пару или предлагает выбрать одну из нескольких. Если текущих занятий нет, бот отвечает об этом в чате.",
    "frames": [
      {
        "title": "ДЗ на один день и на неделю",
        "path": "services/notification-bot/bot/handlers/homework.py",
        "language": "python",
        "lines": [
          [
            20,
            "@homework_router.message(Command(\"homework\"))"
          ],
          [
            21,
            "@homework_router.message(F.text == HOMEWORK_WEEK_LABEL)"
          ],
          [
            22,
            "async def cmd_homework_week("
          ],
          [
            23,
            "    message: Message,"
          ],
          [
            24,
            "    academic_client,"
          ],
          [
            25,
            "    today: date | None = None,"
          ],
          [
            26,
            "    bot_homework_client=None,"
          ],
          [
            27,
            "    bot_menu_client=None,"
          ],
          [
            28,
            ") -> None:"
          ],
          [
            29,
            "    \"\"\"Show homework for the nearest 7 days for the current Telegram student.\"\"\""
          ],
          [
            30,
            "    await _show_homework(message, academic_client, bot_homework_client, today, days=7, bot_menu_client=bot_menu_client)"
          ],
          [
            31,
            ""
          ],
          [
            32,
            ""
          ],
          [
            33,
            "@homework_router.message(Command(\"homework_today\"))"
          ],
          [
            34,
            "@homework_router.message(F.text == HOMEWORK_TODAY_LABEL)"
          ],
          [
            35,
            "async def cmd_homework_today("
          ],
          [
            36,
            "    message: Message, academic_client, today: date | None = None, bot_homework_client=None, bot_menu_client=None"
          ],
          [
            37,
            ") -> None:"
          ],
          [
            38,
            "    await _show_homework(message, academic_client, bot_homework_client, today, days=1, bot_menu_client=bot_menu_client)"
          ],
          [
            44,
            "    telegram_id = private_sender(message)"
          ],
          [
            45,
            "    if telegram_id is None:"
          ],
          [
            46,
            "        await message.answer(\"Открой личный чат с ботом, чтобы посмотреть свои задания.\")"
          ],
          [
            47,
            "        return"
          ],
          [
            48,
            "    try:"
          ],
          [
            49,
            "        scope = await resolve_menu_scope(bot_menu_client, telegram_id)"
          ],
          [
            50,
            "        if scope is None or not scope.homework_enabled:"
          ],
          [
            51,
            "            await message.answer(\"ДЗ недоступно. Нужна действующая роль студента с учебной группой.\")"
          ],
          [
            52,
            "            return"
          ],
          [
            53,
            ""
          ],
          [
            54,
            "        start = today or datetime.now(_MOSCOW_TZ).date()"
          ],
          [
            55,
            "        end = start + timedelta(days=days - 1)"
          ],
          [
            56,
            "        semester = await academic_client.get_active_semester()"
          ],
          [
            57,
            "        if semester is None:"
          ],
          [
            58,
            "            await message.answer(\"Сейчас нет активного семестра, поэтому список ДЗ недоступен.\")"
          ],
          [
            74,
            "        upcoming = _filter_week_homeworks(homeworks, start, end)"
          ],
          [
            75,
            ""
          ],
          [
            76,
            "        if await resolve_menu_scope(bot_menu_client, telegram_id) != scope:"
          ],
          [
            77,
            "            raise RuntimeError(\"Own homework authority changed\")"
          ],
          [
            78,
            "        await message.answer(_build_homework_week_text(upcoming, {}, start, end))"
          ]
        ]
      },
      {
        "title": "День преподавателя и повторная проверка доступа",
        "path": "services/notification-bot/bot/handlers/role_reads.py",
        "language": "python",
        "lines": [
          [
            123,
            "@role_reads_router.message(F.text == TEACHER_TODAY_LABEL)"
          ],
          [
            124,
            "async def cmd_teacher_today(message: Message, bot_menu_client=None):"
          ],
          [
            125,
            "    sender = private_sender(message)"
          ],
          [
            126,
            "    if sender is None:"
          ],
          [
            127,
            "        await message.answer(\"Открой личный чат с ботом, чтобы посмотреть свои пары.\")"
          ],
          [
            128,
            "        return"
          ],
          [
            129,
            "    scope = await resolve_menu_scope(bot_menu_client, sender)"
          ],
          [
            130,
            "    if scope is None or not scope.teacher_enabled:"
          ],
          [
            131,
            "        await message.answer(\"Мои пары недоступны. Нужна действующая роль преподавателя.\")"
          ],
          [
            132,
            "        return"
          ],
          [
            133,
            "    try:"
          ],
          [
            134,
            "        result = await bot_menu_client.teacher_today(sender, scope)"
          ],
          [
            135,
            "        now = educational_now(result.server_now, result.educational_date)"
          ],
          [
            136,
            "        if any("
          ],
          [
            137,
            "            lesson.lesson_date != result.educational_date or type(lesson.lesson_id) is not int or lesson.lesson_id <= 0"
          ],
          [
            138,
            "            for lesson in result.lessons"
          ],
          [
            139,
            "        ):"
          ],
          [
            140,
            "            raise BotReadUnavailable(\"Invalid teacher day snapshot\")"
          ],
          [
            141,
            "        await _unchanged(bot_menu_client, sender, scope)"
          ],
          [
            142,
            "        lines = [\"Мои пары сегодня\", f\"{now:%d.%m.%Y} · по Москве\", f\"Данные на {now:%H:%M:%S} МСК\", \"\"]"
          ],
          [
            143,
            "        lines.extend(_lesson_text(lesson) + \"\\n\" for lesson in result.lessons)"
          ],
          [
            144,
            "        if not result.lessons:"
          ],
          [
            145,
            "            lines.append(\"Сегодня у тебя нет пар.\")"
          ]
        ]
      },
      {
        "title": "Нет текущей пары или есть выбор",
        "path": "services/notification-bot/bot/handlers/role_reads.py",
        "language": "python",
        "lines": [
          [
            167,
            "async def _show_headman(message, client, choices, sender: int, scope: MenuScope, selected_lesson_id=0, *, bot=None):"
          ],
          [
            168,
            "    result = await client.headman_current(sender, scope, selected_lesson_id)"
          ],
          [
            169,
            "    now = educational_now(result.server_now, result.educational_date)"
          ],
          [
            170,
            "    await _unchanged(client, sender, scope)"
          ],
          [
            171,
            "    if result.state == \"NO_CURRENT\":"
          ],
          [
            172,
            "        await message.answer(f\"Учёт группы\\nСейчас нет текущей пары.\\nДанные на {now:%d.%m.%Y %H:%M:%S} МСК\")"
          ],
          [
            173,
            "        return"
          ],
          [
            174,
            "    if result.state == \"CHOOSE\":"
          ],
          [
            175,
            "        if choices is None or selected_lesson_id or len(result.choices) < 2:"
          ],
          [
            176,
            "            raise BotReadUnavailable(\"Lesson selection unavailable\")"
          ],
          [
            177,
            "        if len({lesson.lesson_id for lesson in result.choices}) != len(result.choices):"
          ],
          [
            178,
            "            raise BotReadUnavailable(\"Ambiguous lesson selection\")"
          ],
          [
            179,
            "        for lesson in result.choices:"
          ],
          [
            180,
            "            _validate_current_lesson(lesson, result.educational_date, scope)"
          ],
          [
            193,
            "        sent = await message.answer("
          ],
          [
            194,
            "            \"Сейчас доступны несколько пар твоих групп. Выбери пару:\","
          ],
          [
            195,
            "            reply_markup=InlineKeyboardMarkup(inline_keyboard=buttons),"
          ],
          [
            196,
            "        )"
          ],
          [
            197,
            "        choices.register(token, sender, scope, result.choices, sent.message_id)"
          ],
          [
            198,
            "        return"
          ],
          [
            199,
            "    if result.state != \"READY\" or result.lesson is None:"
          ],
          [
            200,
            "        raise BotReadUnavailable(\"Unknown lesson snapshot\")"
          ],
          [
            201,
            "    lesson = result.lesson"
          ],
          [
            202,
            "    _validate_current_lesson(lesson, result.educational_date, scope)"
          ],
          [
            203,
            "    if selected_lesson_id and selected_lesson_id != lesson.lesson_id:"
          ],
          [
            204,
            "        raise BotReadDenied(\"Selected lesson changed\")"
          ]
        ]
      }
    ]
  },
  {
    "id": "telegram-bot-button",
    "title": "Кнопка ведёт в Mini App",
    "description": "В уведомлении о задании 901 кнопка «Открыть задание» передаёт действие версии 2 в base64url с префиксом n2_. Для ссылки t.me используется startapp, для прямой HTTPS-ссылки — tgWebAppStartParam. Mini App сначала читает параметр URL, затем start_param из Telegram. Доступ к заданию проверяется в приложении.",
    "frames": [
      {
        "title": "Задание, дата и подпись кнопки",
        "path": "services/notification-bot/bot/notifications/presentation.py",
        "language": "python",
        "lines": [
          [
            303,
            "        else:"
          ],
          [
            304,
            "            homework = homework if isinstance(homework, dict) else {}"
          ],
          [
            305,
            "            homework_id = _positive_id(payload.get(\"homework_id\") or homework.get(\"homework_id\") or homework.get(\"id\"))"
          ],
          [
            306,
            "        # The student homework view requires a lesson date. The encoder checks ISO date validity."
          ],
          [
            307,
            "        if role == \"STUDENT\" and (not isinstance(day, str) or not day or day.startswith(\"00\")):"
          ],
          [
            308,
            "            return None"
          ],
          [
            309,
            "        action = {"
          ],
          [
            310,
            "            \"kind\": \"homework\","
          ],
          [
            311,
            "            \"role\": role,"
          ],
          [
            312,
            "            \"homeworkId\": homework_id,"
          ],
          [
            313,
            "        }"
          ],
          [
            330,
            "    if day and action[\"kind\"] in {\"lesson\", \"homework\"}:"
          ],
          [
            331,
            "        action[\"lessonDate\"] = day"
          ],
          [
            332,
            "    return action if encode_notification_action(action) is not None else None"
          ],
          [
            333,
            ""
          ],
          [
            334,
            ""
          ],
          [
            335,
            "def message_options(event, *, config=None, facts=None, fallback_markup=None, role=\"STUDENT\"):"
          ],
          [
            336,
            "    options = {\"text\": render(event, facts=facts), \"parse_mode\": \"HTML\"}"
          ],
          [
            337,
            "    base_url = None"
          ],
          [
            338,
            "    if config is not None:"
          ],
          [
            339,
            "        base_url = getattr(config, \"mini_app_web_url\", None) or getattr(config, \"mini_app_url\", None)"
          ],
          [
            340,
            "    action = notification_action(event, facts=facts, role=role)"
          ],
          [
            341,
            "    encoded = encode_notification_action(action) if action is not None else None"
          ],
          [
            342,
            "    labels = {"
          ],
          [
            343,
            "        \"checkin\": \"Открыть отметку\","
          ],
          [
            344,
            "        \"lesson\": \"Открыть занятие\","
          ],
          [
            345,
            "        \"request\": \"Открыть заявку\","
          ],
          [
            346,
            "        \"homework\": \"Открыть задание\","
          ],
          [
            347,
            "        \"group\": \"Открыть группу\","
          ],
          [
            348,
            "        \"homework-week\": \"Задания на неделю\","
          ],
          [
            349,
            "    }"
          ],
          [
            350,
            "    button = build_mini_app_button("
          ],
          [
            351,
            "        text=labels[action[\"kind\"]] if action else \"Открыть приложение\","
          ],
          [
            352,
            "        base_url=base_url,"
          ],
          [
            353,
            "        start_param=f\"n2_{encoded}\" if encoded else None,"
          ],
          [
            354,
            "    )"
          ]
        ]
      },
      {
        "title": "Два параметра URL и конверт версии 2",
        "path": "services/notification-bot/bot/services/mini_app_links.py",
        "language": "python",
        "lines": [
          [
            46,
            "    if not start_param:"
          ],
          [
            47,
            "        return base_url"
          ],
          [
            48,
            ""
          ],
          [
            49,
            "    query = ["
          ],
          [
            50,
            "        (key, value) for key, value in parse_qsl(parts.query, keep_blank_values=True) if key not in _START_PARAM_KEYS"
          ],
          [
            51,
            "    ]"
          ],
          [
            52,
            "    start_key = \"startapp\" if is_telegram_mini_app_link(base_url) else \"tgWebAppStartParam\""
          ],
          [
            53,
            "    query.append((start_key, start_param))"
          ],
          [
            54,
            ""
          ],
          [
            102,
            "    if is_telegram_mini_app_link(url):"
          ],
          [
            103,
            "        return InlineKeyboardButton(text=text, url=url)"
          ],
          [
            104,
            ""
          ],
          [
            105,
            "    return InlineKeyboardButton(text=text, web_app=WebAppInfo(url=url))"
          ],
          [
            158,
            "    raw = json.dumps({\"v\": 2, \"action\": action}, separators=(\",\", \":\"), ensure_ascii=False).encode(\"utf-8\")"
          ],
          [
            159,
            "    encoded = base64.urlsafe_b64encode(raw).decode(\"ascii\").rstrip(\"=\")"
          ],
          [
            160,
            "    return encoded if len(encoded) <= 384 else None"
          ]
        ]
      },
      {
        "title": "Параметр запуска в Mini App",
        "path": "frontends/tma-vue/src/App.vue",
        "language": "typescript",
        "lines": [
          [
            233,
            "const notificationStartParam ="
          ],
          [
            234,
            "  new URLSearchParams(window.location.search).get('tgWebAppStartParam') ??"
          ],
          [
            235,
            "  new URLSearchParams(host.app?.initData ?? '').get('start_param');"
          ],
          [
            236,
            "const notificationLaunchEncoded = notificationStartParam?.startsWith('n2_')"
          ],
          [
            237,
            "  ? notificationStartParam.slice(3)"
          ],
          [
            238,
            "  : null;"
          ],
          [
            239,
            "const notificationLaunchAction = decodeNotificationLaunch(notificationLaunchEncoded);"
          ],
          [
            240,
            "const notificationLaunch = new NotificationLaunchOwner(notificationLaunchAction);"
          ],
          [
            241,
            "const notificationLaunchNotice = ref<string | null>("
          ],
          [
            242,
            "  notificationLaunchEncoded && !notificationLaunchAction"
          ],
          [
            243,
            "    ? 'Ссылка уведомления недействительна.'"
          ],
          [
            244,
            "    : null,"
          ],
          [
            245,
            ");"
          ]
        ]
      },
      {
        "title": "Данные до кодирования ссылки",
        "language": "json",
        "example": [
          "{",
          "  \"v\": 2,",
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
  }
]
