import type { StoryOperation } from './story-source'

export const accountSessionsHistoryOperations: StoryOperation[] = [
  {
    "id": "account-sessions-history-live",
    "title": "Активные сеансы и это устройство",
    "description": "Auth допускает текущий запрос и читает сеансы этого пользователя, у которых ещё действителен срок обновления и нет отзыва. «Это устройство» означает совпадение sessionId с текущим сеансом. Устройство может иметь несколько входов. В списке есть способ входа, время создания и последней активности.",
    "frames": [
      {
        "title": "GET возвращает только собственные сеансы",
        "path": "services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/AuthSessionApi.java",
        "language": "java",
        "lines": [
          [
            43,
            "    @Operation(summary = \"List the current user's live sessions\")"
          ],
          [
            44,
            "    @ApiResponse(responseCode = \"200\", description = \"Own non-expired sessions\")"
          ],
          [
            45,
            "    @GetMapping(\"/sessions\")"
          ],
          [
            46,
            "    ResponseEntity<AuthSessionsPage> sessions("
          ],
          [
            47,
            "            @RequestParam(name = \"cursor\", required = false) String cursor,"
          ],
          [
            48,
            "            @RequestParam(name = \"limit\", required = false) Integer limit,"
          ],
          [
            49,
            "            Authentication authentication);"
          ]
        ]
      },
      {
        "title": "Сеансы фильтруются по владельцу и сроку",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcAuthSessionQueryAdapter.java",
        "language": "java",
        "lines": [
          [
            52,
            "    private static final String LIVE_SESSIONS_SQL ="
          ],
          [
            53,
            "            \"\"\""
          ],
          [
            54,
            "            SELECT sid, auth_method, client_label, location_label,"
          ],
          [
            55,
            "                   created_at, last_seen_at"
          ],
          [
            56,
            "            FROM auth_sessions"
          ],
          [
            57,
            "            WHERE user_id = ?"
          ],
          [
            58,
            "              AND revoked_at IS NULL"
          ],
          [
            59,
            "              AND refresh_expires_at > ?"
          ],
          [
            60,
            "            ORDER BY created_at DESC, sid DESC"
          ],
          [
            61,
            "            LIMIT ?"
          ],
          [
            62,
            "            \"\"\";"
          ],
          [
            72,
            "    private static final String LIVE_SESSIONS_AFTER_SQL ="
          ],
          [
            73,
            "            \"\"\""
          ],
          [
            74,
            "            SELECT sid, auth_method, client_label, location_label,"
          ],
          [
            75,
            "                   created_at, last_seen_at"
          ],
          [
            76,
            "            FROM auth_sessions"
          ],
          [
            77,
            "            WHERE user_id = ?"
          ],
          [
            78,
            "              AND revoked_at IS NULL"
          ],
          [
            79,
            "              AND refresh_expires_at > ?"
          ],
          [
            80,
            "              AND (created_at, sid) < (?, ?)"
          ],
          [
            81,
            "            ORDER BY created_at DESC, sid DESC"
          ],
          [
            82,
            "            LIMIT ?"
          ],
          [
            83,
            "            \"\"\";"
          ]
        ]
      },
      {
        "title": "Сервер определяет текущий сеанс по SID",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthSessionController.java",
        "language": "java",
        "lines": [
          [
            75,
            "    public ResponseEntity<AuthSessionsPage> sessions("
          ],
          [
            76,
            "            String cursor, Integer limit, Authentication authentication) {"
          ],
          [
            77,
            "        SessionPrincipal principal = principal(authentication);"
          ],
          [
            78,
            "        authService.admit(principal);"
          ],
          [
            79,
            "        AuthSessionQueryPort.SessionPage page ="
          ],
          [
            80,
            "                queryPort.findLiveSessions("
          ],
          [
            81,
            "                        principal.userId(),"
          ],
          [
            82,
            "                        principal.sessionId(),"
          ],
          [
            83,
            "                        cursor,"
          ],
          [
            84,
            "                        limit == null ? 0 : limit);"
          ],
          [
            85,
            "        List<AuthSessionSummary> items ="
          ],
          [
            86,
            "                page.items().stream()"
          ],
          [
            87,
            "                        .map("
          ],
          [
            88,
            "                                item ->"
          ],
          [
            89,
            "                                        new AuthSessionSummary("
          ],
          [
            90,
            "                                                item.sessionId().toString(),"
          ],
          [
            91,
            "                                                item.authMethod(),"
          ],
          [
            92,
            "                                                item.clientLabel(),"
          ],
          [
            93,
            "                                                item.locationLabel(),"
          ],
          [
            94,
            "                                                item.createdAt(),"
          ],
          [
            95,
            "                                                item.lastSeenAt(),"
          ],
          [
            96,
            "                                                item.sessionId().equals(principal.sessionId())))"
          ],
          [
            97,
            "                        .toList();"
          ],
          [
            98,
            "        return noStore(ResponseEntity.ok(new AuthSessionsPage(items, page.nextCursor())));"
          ]
        ]
      },
      {
        "title": "Экран показывает текущее устройство",
        "path": "frontends/mobile-core/src/features/profile/SessionsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            159,
            "          <div class=\"profile-session-card__top\">"
          ],
          [
            160,
            "            <p class=\"profile-session-card__title\">"
          ],
          [
            161,
            "              {{ title(session) }}"
          ],
          [
            162,
            "            </p>"
          ],
          [
            163,
            "            <span"
          ],
          [
            164,
            "              v-if=\"session.current\""
          ],
          [
            165,
            "              class=\"profile-session-card__current\""
          ],
          [
            166,
            "            >Это устройство</span>"
          ],
          [
            167,
            "          </div>"
          ],
          [
            168,
            "          <p class=\"profile-session-card__meta\">"
          ],
          [
            169,
            "            {{ metadata(session) }}"
          ],
          [
            170,
            "          </p>"
          ],
          [
            171,
            "          <button"
          ],
          [
            172,
            "            class=\"profile-session-terminate\""
          ],
          [
            173,
            "            type=\"button\""
          ],
          [
            174,
            "            :disabled=\""
          ],
          [
            175,
            "              busy || loading || offline || terminatingSessionId !== null || !onTerminateSession"
          ],
          [
            176,
            "            \""
          ],
          [
            177,
            "            :aria-busy=\"terminatingSessionId === session.sessionId\""
          ],
          [
            178,
            "            @click=\"terminate(session.sessionId)\""
          ],
          [
            179,
            "          >"
          ]
        ]
      },
      {
        "title": "HTTP 200 · страница активных сеансов",
        "language": "json",
        "example": [
          "{",
          "  \"items\": [",
          "    {",
          "      \"sessionId\": \"11111111-1111-4111-8111-111111111111\",",
          "      \"authMethod\": \"PASSWORD\",",
          "      \"createdAt\": \"2026-11-16T07:30:00Z\",",
          "      \"lastSeenAt\": \"2026-11-16T09:00:00Z\",",
          "      \"current\": true",
          "    },",
          "    {",
          "      \"sessionId\": \"22222222-2222-4222-8222-222222222222\",",
          "      \"authMethod\": \"QR\",",
          "      \"createdAt\": \"2026-11-15T13:05:00Z\",",
          "      \"lastSeenAt\": \"2026-11-15T13:09:00Z\",",
          "      \"current\": false",
          "    }",
          "  ]",
          "}"
        ]
      }
    ]
  },
  {
    "id": "account-sessions-history-revoke",
    "title": "Завершение одного сеанса",
    "description": "DELETE завершает свой сеанс и отвечает 204 без тела. Чужой ID даёт 404. Повтор для уже отозванного своего сеанса из другого действующего входа снова даёт 204 без второго события. Если завершён текущий сеанс, клиент выходит. Если завершён другой, экран убирает его из списка и перечитывает данные.",
    "frames": [
      {
        "title": "HTTP 204 или 404 по контракту",
        "path": "services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/AuthSessionApi.java",
        "language": "java",
        "lines": [
          [
            51,
            "    @Operation(summary = \"Revoke one of the current user's sessions\")"
          ],
          [
            52,
            "    @ApiResponse(responseCode = \"204\", description = \"Own session revoked\")"
          ],
          [
            53,
            "    @ApiResponse(responseCode = \"404\", description = \"SESSION_NOT_FOUND\")"
          ],
          [
            54,
            "    @DeleteMapping(\"/sessions/{sessionId}\")"
          ],
          [
            55,
            "    ResponseEntity<Void> revokeSession("
          ],
          [
            56,
            "            @PathVariable(\"sessionId\") UUID sessionId, Authentication authentication);"
          ]
        ]
      },
      {
        "title": "Проверка вызывающего и поиск только своих сеансов",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java",
        "language": "java",
        "lines": [
          [
            626,
            "        SessionRow actor = findSession(command.userId(), command.actorSessionId(), true);"
          ],
          [
            627,
            "        if (actor == null) {"
          ],
          [
            628,
            "            return RevokeResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);"
          ],
          [
            629,
            "        }"
          ],
          [
            630,
            "        if (!isLive(actor, command.now())) {"
          ],
          [
            631,
            "            return RevokeResult.failure(SessionStatePort.FailureCode.SESSION_REVOKED);"
          ],
          [
            632,
            "        }"
          ],
          [
            633,
            "        if (actor.sessionVersion() != command.expectedSessionVersion()"
          ],
          [
            634,
            "                || user.rolesVersion() != command.expectedRolesVersion()) {"
          ],
          [
            635,
            "            return RevokeResult.failure(SessionStatePort.FailureCode.SESSION_STATE_STALE);"
          ],
          [
            636,
            "        }"
          ],
          [
            644,
            "        SessionRow target ="
          ],
          [
            645,
            "                command.actorSessionId().equals(command.targetSessionId())"
          ],
          [
            646,
            "                        ? actor"
          ],
          [
            647,
            "                        : findSession(command.userId(), command.targetSessionId(), true);"
          ],
          [
            648,
            "        if (target == null) {"
          ],
          [
            649,
            "            return RevokeResult.failure(SessionStatePort.FailureCode.SESSION_NOT_FOUND);"
          ],
          [
            650,
            "        }"
          ],
          [
            651,
            "        if (target.revokedAt() != null) {"
          ],
          [
            652,
            "            return RevokeResult.success("
          ],
          [
            653,
            "                    selectedRevokedSnapshot(target, user.rolesVersion(), grants), true);"
          ],
          [
            654,
            "        }"
          ]
        ]
      },
      {
        "title": "Первый отзыв сохраняет событие в истории",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java",
        "language": "java",
        "lines": [
          [
            655,
            "        int updated ="
          ],
          [
            656,
            "                jdbcTemplate.update("
          ],
          [
            657,
            "                        REVOKE_CURRENT_SQL,"
          ],
          [
            658,
            "                        timestamp(command.now()),"
          ],
          [
            659,
            "                        SessionRevokeReason.CURRENT_LOGOUT.name(),"
          ],
          [
            660,
            "                        command.targetSessionId(),"
          ],
          [
            661,
            "                        command.userId());"
          ],
          [
            662,
            "        if (updated != 1) {"
          ],
          [
            663,
            "            throw new IllegalStateException(\"selected session revoke did not update one row\");"
          ],
          [
            664,
            "        }"
          ],
          [
            665,
            "        failureInjector.after(\"revoke.selected\");"
          ],
          [
            666,
            "        insertEvent("
          ],
          [
            667,
            "                new SecurityEvent("
          ],
          [
            668,
            "                        command.userId(),"
          ],
          [
            669,
            "                        target.sessionId(),"
          ],
          [
            670,
            "                        SecurityEvent.Type.CURRENT_LOGOUT,"
          ],
          [
            671,
            "                        command.now(),"
          ],
          [
            672,
            "                        target.authMethod(),"
          ],
          [
            673,
            "                        target.clientLabel(),"
          ],
          [
            674,
            "                        target.locationLabel()));"
          ]
        ]
      },
      {
        "title": "Успех текущего или другого сеанса в интерфейсе",
        "path": "frontends/mobile-core/src/features/profile/profile-state.ts",
        "language": "typescript",
        "lines": [
          [
            333,
            "    try {"
          ],
          [
            334,
            "      await this.port.terminateSession(sessionId)"
          ],
          [
            335,
            "      if (!this.canApplyDurableSuccess(guard, accountId)) return"
          ],
          [
            336,
            "      if (this.view.snapshot?.sessionId === sessionId) {"
          ],
          [
            337,
            "        this.invalidate('session-terminated')"
          ],
          [
            338,
            "        await this.notifyInvalidated('session-terminated')"
          ],
          [
            339,
            "        return"
          ],
          [
            340,
            "      }"
          ],
          [
            341,
            "      // Fence pre-mutation pages so their late results cannot resurrect the revoked card."
          ],
          [
            342,
            "      this.sessionsGeneration += 1"
          ],
          [
            343,
            "      this.sessionsRequest = null"
          ],
          [
            344,
            "      this.sessionsRequestGuard = null"
          ],
          [
            345,
            "      this.allSessionsRequest = null"
          ],
          [
            346,
            "      this.allSessionsGuard = null"
          ],
          [
            347,
            "      this.view.sessions = this.view.sessions.filter((session) => session.sessionId !== sessionId)"
          ],
          [
            348,
            "      this.view.sessionsNextCursor = null"
          ],
          [
            349,
            "      this.view.sessionsStatus = 'ready'"
          ],
          [
            350,
            "      try {"
          ],
          [
            351,
            "        await this.loadAllSessions()"
          ]
        ]
      }
    ]
  },
  {
    "id": "account-sessions-history-all",
    "title": "Выход на всех устройствах",
    "description": "«Выйти на всех устройствах» отправляет отдельную команду. Она отзывает все ещё не отозванные сеансы пользователя, включая текущий. Auth записывает событие общего выхода и возвращает 204. Это действие доступно онлайн. При очистке входа PWA оповещает вкладки того же браузера через BroadcastChannel, если он поддерживается. Соседняя вкладка убирает прежний экран и возвращает пользователя на вход.",
    "frames": [
      {
        "title": "Кнопка общего выхода учитывает онлайн-состояние",
        "path": "frontends/mobile-core/src/features/profile/SessionsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            191,
            "      <button"
          ],
          [
            192,
            "        class=\"profile-danger-button\""
          ],
          [
            193,
            "        type=\"button\""
          ],
          [
            194,
            "        :disabled=\""
          ],
          [
            195,
            "          busy ||"
          ],
          [
            196,
            "            loading ||"
          ],
          [
            197,
            "            offline ||"
          ],
          [
            198,
            "            terminatingSessionId !== null ||"
          ],
          [
            199,
            "            sessions.length === 0 ||"
          ],
          [
            200,
            "            !onLogoutAll"
          ],
          [
            201,
            "        \""
          ],
          [
            202,
            "        @click=\"onLogoutAll?.()\""
          ],
          [
            203,
            "      >"
          ],
          [
            204,
            "        {{"
          ],
          [
            205,
            "          busy && terminatingSessionId === null ? 'Завершаем сеансы…' : 'Выйти на всех устройствах'"
          ],
          [
            206,
            "        }}"
          ],
          [
            207,
            "      </button>"
          ]
        ]
      },
      {
        "title": "Отдельная команда и ответ без тела",
        "path": "services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/AuthSessionApi.java",
        "language": "java",
        "lines": [
          [
            65,
            "    @Operation(summary = \"Revoke all sessions for the current user\")"
          ],
          [
            66,
            "    @ApiResponse(responseCode = \"204\", description = \"All sessions revoked\")"
          ],
          [
            67,
            "    @PostMapping(\"/logout-all\")"
          ],
          [
            68,
            "    ResponseEntity<Void> logoutAll(Authentication authentication);"
          ]
        ]
      },
      {
        "title": "Обновление всех сеансов владельца",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java",
        "language": "java",
        "lines": [
          [
            146,
            "    private static final String REVOKE_ALL_SQL ="
          ],
          [
            147,
            "            \"\"\""
          ],
          [
            148,
            "            UPDATE auth_sessions"
          ],
          [
            149,
            "            SET revoked_at = ?, revoke_reason = ?"
          ],
          [
            150,
            "            WHERE user_id = ? AND revoked_at IS NULL"
          ],
          [
            151,
            "            \"\"\";"
          ],
          [
            707,
            "    private RevokeAllResult revokeAllInTransaction(RevokeAllCommand command) {"
          ],
          [
            708,
            "        UserRow user = findUser(command.userId(), true);"
          ],
          [
            709,
            "        if (user == null) {"
          ],
          [
            710,
            "            return RevokeAllResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);"
          ],
          [
            711,
            "        }"
          ],
          [
            712,
            "        List<SessionRow> sessions = findAllSessions(command.userId());"
          ],
          [
            713,
            "        if (sessions.stream()"
          ],
          [
            714,
            "                .noneMatch(session -> session.sessionId().equals(command.currentSessionId()))) {"
          ],
          [
            715,
            "            return RevokeAllResult.failure(SessionStatePort.FailureCode.INVALID_SESSION);"
          ],
          [
            716,
            "        }"
          ],
          [
            717,
            ""
          ],
          [
            718,
            "        int newlyRevoked ="
          ],
          [
            719,
            "                jdbcTemplate.update("
          ],
          [
            720,
            "                        REVOKE_ALL_SQL,"
          ],
          [
            721,
            "                        timestamp(command.now()),"
          ],
          [
            722,
            "                        SessionRevokeReason.LOGOUT_ALL.name(),"
          ],
          [
            723,
            "                        command.userId());"
          ],
          [
            724,
            "        failureInjector.after(\"revoke.all\");"
          ],
          [
            725,
            "        insertEvent(command.logoutAllEvent());"
          ],
          [
            726,
            "        failureInjector.after(\"revoke-all.event\");"
          ]
        ]
      },
      {
        "title": "Браузер передаёт очистку входа соседней вкладке",
        "path": "frontends/pwa-vue/src/auth.ts",
        "language": "typescript",
        "lines": [
          [
            670,
            "    accessToken.value = null"
          ],
          [
            671,
            "    knownProfile = null"
          ],
          [
            672,
            "    knownProfileGeneration = null"
          ],
          [
            673,
            "    if (clearOptions.broadcast !== false) {"
          ],
          [
            674,
            "      try {"
          ],
          [
            675,
            "        channel?.postMessage({"
          ],
          [
            676,
            "          type: INVALIDATION_MESSAGE,"
          ],
          [
            677,
            "          ...(preserved"
          ],
          [
            678,
            "            ? { deviceScope: { userId: preserved.userId, sessionId: preserved.sessionId } }"
          ],
          [
            679,
            "            : {}),"
          ],
          [
            680,
            "        })"
          ],
          [
            761,
            "  function onChannelMessage(event: MessageEvent<unknown>): void {"
          ],
          [
            762,
            "    if (!isInvalidationMessage(event.data)) return"
          ],
          [
            763,
            "    const scope = (event.data as { deviceScope?: unknown }).deviceScope"
          ],
          [
            764,
            "    const previous = deviceSession.value"
          ],
          [
            765,
            "    const sameSession ="
          ],
          [
            773,
            "    clear({"
          ],
          [
            774,
            "      broadcast: false,"
          ],
          [
            775,
            "      ...(sameSession && previous"
          ],
          [
            776,
            "        ? { preserveDevice: { userId: previous.userId, sessionId: previous.sessionId } }"
          ],
          [
            777,
            "        : {}),"
          ],
          [
            778,
            "    })"
          ],
          [
            779,
            "    notifyInvalidation('external')"
          ],
          [
            780,
            "  }"
          ],
          [
            781,
            ""
          ],
          [
            782,
            "  channel?.addEventListener('message', onChannelMessage)"
          ],
          [
            859,
            "function createBrowserChannel(): PwaAuthInvalidationChannel | undefined {"
          ],
          [
            860,
            "  if (typeof window === 'undefined' || typeof window.BroadcastChannel !== 'function')"
          ],
          [
            861,
            "    return undefined"
          ],
          [
            862,
            "  return new window.BroadcastChannel("
          ],
          [
            863,
            "    'rct-auth-invalidation',"
          ],
          [
            864,
            "  ) as unknown as PwaAuthInvalidationChannel"
          ]
        ]
      },
      {
        "title": "PWA возвращает вкладку на экран входа",
        "path": "frontends/pwa-vue/src/App.vue",
        "language": "typescript",
        "lines": [
          [
            2045,
            "function handleExternalInvalidation(reason: PwaAuthInvalidationReason = 'external'): void {"
          ],
          [
            2046,
            "  if (reason === 'authority-changed') {"
          ],
          [
            2047,
            "    // The auth layer has already advanced the generation and installed the"
          ],
          [
            2048,
            "    // validated candidate token. Detach the old owner synchronously, then"
          ],
          [
            2049,
            "    // reconcile that token/session without asking refresh to rotate again."
          ],
          [
            2070,
            "    void bootstrap({ refresh: false });"
          ],
          [
            2071,
            "    return;"
          ],
          [
            2072,
            "  }"
          ],
          [
            2073,
            "  void clearOwnerSnapshot(returnToLogin({ clearAuth: false }));"
          ],
          [
            2074,
            "}"
          ],
          [
            2245,
            "onMounted(() => {"
          ],
          [
            2246,
            "  moscowDayClock.start();"
          ],
          [
            2247,
            "  stopAuthInvalidation = auth.subscribeInvalidation(handleExternalInvalidation);"
          ]
        ]
      }
    ]
  },
  {
    "id": "account-sessions-history-history",
    "title": "История действий аккаунта",
    "description": "История хранит входы, смену роли, выход с одного или всех устройств, смену пароля и отзыв по безопасности. Auth читает только события текущего пользователя. Записи идут от новых к старым по времени и ID. «Показать ещё» передаёт nextCursor. Когда страниц больше нет, сервер опускает это поле.",
    "frames": [
      {
        "title": "Типы событий и подписи",
        "path": "frontends/mobile-core/src/features/profile/profile-types.ts",
        "language": "typescript",
        "lines": [
          [
            214,
            "const HISTORY_LABELS: Record<ProfileHistoryType, string> = {"
          ],
          [
            215,
            "  LOGIN: 'Вход в аккаунт',"
          ],
          [
            216,
            "  ROLE_CHANGED: 'Смена роли',"
          ],
          [
            217,
            "  CURRENT_LOGOUT: 'Выход с устройства',"
          ],
          [
            218,
            "  LOGOUT_ALL: 'Выход со всех устройств',"
          ],
          [
            219,
            "  PASSWORD_CHANGED: 'Смена пароля',"
          ],
          [
            220,
            "  SECURITY_REVOKED: 'Сессии отозваны',"
          ],
          [
            221,
            "}"
          ]
        ]
      },
      {
        "title": "Сервер читает события владельца",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthSessionController.java",
        "language": "java",
        "lines": [
          [
            142,
            "    public ResponseEntity<AccountHistoryPage> accountHistory("
          ],
          [
            143,
            "            String cursor, Integer limit, Authentication authentication) {"
          ],
          [
            144,
            "        SessionPrincipal principal = principal(authentication);"
          ],
          [
            145,
            "        if (principal.isBootstrap()) {"
          ],
          [
            146,
            "            throw new ru.rutcampustrack.auth.session.AuthSessionException("
          ],
          [
            147,
            "                    ru.rutcampustrack.auth.session.AuthSessionException.Code"
          ],
          [
            148,
            "                            .BOOTSTRAP_SCOPE_DENIED);"
          ],
          [
            149,
            "        }"
          ],
          [
            150,
            "        authService.admit(principal);"
          ],
          [
            151,
            "        AuthSessionQueryPort.HistoryPage page ="
          ],
          [
            152,
            "                queryPort.findHistory(principal.userId(), cursor, limit == null ? 0 : limit);"
          ],
          [
            153,
            "        List<AccountHistoryEvent> items ="
          ],
          [
            154,
            "                page.items().stream()"
          ],
          [
            155,
            "                        .map("
          ],
          [
            156,
            "                                item ->"
          ],
          [
            157,
            "                                        new AccountHistoryEvent("
          ],
          [
            158,
            "                                                Long.toString(item.id()),"
          ],
          [
            159,
            "                                                item.type(),"
          ],
          [
            160,
            "                                                item.occurredAt(),"
          ],
          [
            161,
            "                                                item.authMethod(),"
          ],
          [
            162,
            "                                                item.clientLabel(),"
          ],
          [
            163,
            "                                                item.locationLabel()))"
          ],
          [
            164,
            "                        .toList();"
          ],
          [
            165,
            "        return noStore(ResponseEntity.ok(new AccountHistoryPage(items, page.nextCursor())));"
          ]
        ]
      },
      {
        "title": "Курсор сравнивает время и ID записи",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcAuthSessionQueryAdapter.java",
        "language": "java",
        "lines": [
          [
            85,
            "    private static final String HISTORY_SQL ="
          ],
          [
            86,
            "            \"\"\""
          ],
          [
            87,
            "            SELECT id, event_type, occurred_at, auth_method,"
          ],
          [
            88,
            "                   client_label, location_label"
          ],
          [
            89,
            "            FROM account_security_events"
          ],
          [
            90,
            "            WHERE user_id = ?"
          ],
          [
            91,
            "            ORDER BY occurred_at DESC, id DESC"
          ],
          [
            92,
            "            LIMIT ?"
          ],
          [
            93,
            "            \"\"\";"
          ],
          [
            94,
            ""
          ],
          [
            95,
            "    private static final String HISTORY_AFTER_SQL ="
          ],
          [
            96,
            "            \"\"\""
          ],
          [
            97,
            "            SELECT id, event_type, occurred_at, auth_method,"
          ],
          [
            98,
            "                   client_label, location_label"
          ],
          [
            99,
            "            FROM account_security_events"
          ],
          [
            100,
            "            WHERE user_id = ?"
          ],
          [
            101,
            "              AND (occurred_at, id) < (?, ?)"
          ],
          [
            102,
            "            ORDER BY occurred_at DESC, id DESC"
          ],
          [
            103,
            "            LIMIT ?"
          ],
          [
            104,
            "            \"\"\";"
          ],
          [
            283,
            "            List<HistoryView> items ="
          ],
          [
            284,
            "                    hasMore ? List.copyOf(rows.subList(0, boundedLimit)) : List.copyOf(rows);"
          ],
          [
            285,
            "            String nextCursor = hasMore ? encodeHistoryCursor(items.get(items.size() - 1)) : null;"
          ],
          [
            286,
            "            return new HistoryPage(items, nextCursor);"
          ]
        ]
      },
      {
        "title": "Клиент запрашивает следующую страницу по курсору",
        "path": "frontends/mobile-core/src/features/profile/AccountHistoryScreen.vue",
        "language": "typescript",
        "lines": [
          [
            42,
            "function metadata(event: ProfileHistoryEvent): string | null {"
          ],
          [
            43,
            "  return truthfulMetadata("
          ],
          [
            44,
            "    event.clientLabel,"
          ],
          [
            45,
            "    event.locationLabel,"
          ],
          [
            46,
            "    authMethodLabel(event.authMethod),"
          ],
          [
            47,
            "  );"
          ],
          [
            48,
            "}"
          ],
          [
            49,
            ""
          ],
          [
            50,
            "function loadMore(): void {"
          ],
          [
            51,
            "  if (props.nextCursor && !props.loading) void props.onLoadMore?.(props.nextCursor);"
          ],
          [
            52,
            "}"
          ],
          [
            131,
            "        v-if=\"nextCursor\""
          ],
          [
            132,
            "        class=\"profile-show-more\""
          ],
          [
            133,
            "        type=\"button\""
          ],
          [
            134,
            "        :disabled=\"loading\""
          ],
          [
            135,
            "        @click=\"loadMore\""
          ],
          [
            136,
            "      >"
          ],
          [
            137,
            "        {{ loading ? 'Загружаем…' : 'Показать ещё' }}"
          ]
        ]
      },
      {
        "title": "HTTP 200 · последние события аккаунта",
        "language": "json",
        "example": [
          "{",
          "  \"items\": [",
          "    {",
          "      \"id\": \"58\",",
          "      \"type\": \"CURRENT_LOGOUT\",",
          "      \"occurredAt\": \"2026-11-16T09:10:00Z\",",
          "      \"authMethod\": \"QR\"",
          "    },",
          "    {",
          "      \"id\": \"57\",",
          "      \"type\": \"LOGIN\",",
          "      \"occurredAt\": \"2026-11-16T07:30:00Z\",",
          "      \"authMethod\": \"PASSWORD\"",
          "    },",
          "    {",
          "      \"id\": \"56\",",
          "      \"type\": \"LOGIN\",",
          "      \"occurredAt\": \"2026-11-15T13:05:00Z\",",
          "      \"authMethod\": \"QR\"",
          "    }",
          "  ]",
          "}"
        ]
      }
    ]
  }
]
