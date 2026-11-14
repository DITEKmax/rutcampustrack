import type { StoryOperation } from './story-source'

export const webPushSubscriptionOperations: StoryOperation[] = [
  {
    "id": "web-push-subscription-enable",
    "title": "Разрешение и подписка",
    "description": "PWA проверяет поддержку браузера и при необходимости просит открыть установленное приложение. Запрос разрешения начинается прямо из нажатия кнопки. После согласия PWA создаёт подписку и отправляет её серверу от имени текущего аккаунта. Только после HTTP 201 браузер сохраняет привязку для service worker.",
    "frames": [
      {
        "title": "Разрешение запрашивается из нажатия",
        "path": "frontends/pwa-vue/src/features/push/push-controller.ts",
        "language": "typescript",
        "lines": [
          [
            235,
            "  async function enable(): Promise<void> {"
          ],
          [
            236,
            "    const captured = owner"
          ],
          [
            237,
            "    const version = epoch"
          ],
          [
            238,
            "    if (!captured?.token || !browser.supported() || state.status === 'busy') return"
          ],
          [
            239,
            "    if (browser.installationRequired()) {"
          ],
          [
            240,
            "      publish("
          ],
          [
            241,
            "        'off',"
          ],
          [
            242,
            "        'На iPhone и iPad сначала добавь приложение на домашний экран и открой его оттуда.',"
          ],
          [
            243,
            "      )"
          ],
          [
            244,
            "      return"
          ],
          [
            245,
            "    }"
          ],
          [
            246,
            "    publish('busy', 'Включаем Web Push…')"
          ],
          [
            247,
            "    // Called synchronously from the button's user gesture, before any other await."
          ],
          [
            248,
            "    let permissionPromise: Promise<{ permission: NotificationPermission } | { error: unknown }>"
          ],
          [
            249,
            "    try {"
          ],
          [
            250,
            "      permissionPromise = browser.requestPermission().then("
          ],
          [
            251,
            "        (permission) => ({ permission }),"
          ],
          [
            252,
            "        (error: unknown) => ({ error }),"
          ],
          [
            258,
            "    const result = await permissionPromise"
          ],
          [
            259,
            "    if (!current(captured, version)) return"
          ],
          [
            260,
            "    if ('error' in result) {"
          ],
          [
            261,
            "      publish('error', 'Не удалось запросить разрешение браузера.')"
          ],
          [
            262,
            "      return"
          ],
          [
            263,
            "    }"
          ],
          [
            264,
            "    if (result.permission !== 'granted') {"
          ],
          [
            265,
            "      publish("
          ],
          [
            266,
            "        result.permission === 'denied' ? 'denied' : 'off',"
          ],
          [
            267,
            "        result.permission === 'denied'"
          ],
          [
            268,
            "          ? 'Браузер запретил уведомления. Разреши их в настройках сайта.'"
          ],
          [
            269,
            "          : 'Разрешение на уведомления не выдано.',"
          ],
          [
            270,
            "      )"
          ],
          [
            271,
            "      return"
          ]
        ]
      },
      {
        "title": "Регистрация браузерной подписки и локальная привязка",
        "path": "frontends/pwa-vue/src/features/push/push-controller.ts",
        "language": "typescript",
        "lines": [
          [
            277,
            "        if (!current(captured, version)) return"
          ],
          [
            278,
            "        const response = await api(captured, 'GET')"
          ],
          [
            279,
            "        if (!response.ok) throw new Error('Не удалось получить настройки Web Push с сервера.')"
          ],
          [
            280,
            "        const key = vapidKey(((await response.json()) as { publicKey?: unknown }).publicKey)"
          ],
          [
            281,
            "        if (!current(captured, version)) return"
          ],
          [
            291,
            "        if (!current(captured, version)) return"
          ],
          [
            292,
            "        created = await browser.createSubscription(key)"
          ],
          [
            293,
            "        if (!current(captured, version)) {"
          ],
          [
            294,
            "          await created.unsubscribe()"
          ],
          [
            295,
            "          return"
          ],
          [
            296,
            "        }"
          ],
          [
            297,
            "        createdBinding = {"
          ],
          [
            298,
            "          userId: captured.userId,"
          ],
          [
            299,
            "          fingerprint: await browser.fingerprint(created.endpoint),"
          ],
          [
            300,
            "        }"
          ],
          [
            305,
            "        ownedSubscription = created"
          ],
          [
            306,
            "        ownedBinding = createdBinding"
          ],
          [
            307,
            "        const json = created.toJSON()"
          ],
          [
            308,
            "        if (!json.endpoint || !json.keys?.p256dh || !json.keys.auth)"
          ],
          [
            309,
            "          throw new Error('Браузер вернул неполную подписку.')"
          ],
          [
            310,
            "        const bound = await api(captured, 'POST', { endpoint: json.endpoint, keys: json.keys })"
          ],
          [
            311,
            "        if (!bound.ok) throw new Error('Сервер не подтвердил подписку. Повтори включение.')"
          ],
          [
            312,
            "        if (!current(captured, version)) {"
          ],
          [
            313,
            "          await remove(captured, created, createdBinding)"
          ],
          [
            314,
            "          return"
          ],
          [
            315,
            "        }"
          ],
          [
            316,
            "        await browser.bind(createdBinding)"
          ],
          [
            317,
            "        if (!current(captured, version)) {"
          ],
          [
            318,
            "          await remove(captured, created, createdBinding)"
          ],
          [
            319,
            "          return"
          ],
          [
            320,
            "        }"
          ],
          [
            321,
            "        publish('enabled', 'Web Push включён на этом устройстве.')"
          ]
        ]
      },
      {
        "title": "Сервер сохраняет подписку пользователя и отвечает 201",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/PushController.java",
        "language": "java",
        "lines": [
          [
            52,
            "    @Override"
          ],
          [
            53,
            "    @RequireRole({UserRole.STUDENT, UserRole.TEACHER, UserRole.ADMIN})"
          ],
          [
            54,
            "    public ResponseEntity<Void> subscribe(@Valid @RequestBody SubscribeRequest request) {"
          ],
          [
            55,
            "        requestContext.requireSelectedRole();"
          ],
          [
            56,
            "        Instant now = Instant.now();"
          ],
          [
            57,
            "        Long userId = requestContext.getUserId();"
          ],
          [
            58,
            "        PushSubscriptionDocument doc ="
          ],
          [
            59,
            "                repository"
          ],
          [
            60,
            "                        .findByUserIdAndEndpoint(userId, request.endpoint())"
          ],
          [
            61,
            "                        .orElseGet("
          ],
          [
            69,
            "        doc.setGroupId(requestContext.getGroupId());"
          ],
          [
            70,
            "        doc.setP256dh(request.keys().p256dh());"
          ],
          [
            71,
            "        doc.setAuth(request.keys().auth());"
          ],
          [
            72,
            "        doc.setHeadman(requestContext.isHeadman());"
          ],
          [
            73,
            "        doc.setLastSeen(now);"
          ],
          [
            74,
            "        if (doc.getCreatedAt() == null) {"
          ],
          [
            75,
            "            doc.setCreatedAt(now);"
          ],
          [
            76,
            "        }"
          ],
          [
            77,
            ""
          ],
          [
            78,
            "        repository.save(doc);"
          ],
          [
            79,
            "        return ResponseEntity.status(HttpStatus.CREATED).build();"
          ]
        ]
      }
    ]
  },
  {
    "id": "web-push-subscription-deliver",
    "title": "Доставка и открытие PWA",
    "description": "Перед отправкой сервер проверяет настройки Web Push и актуальную аудиторию. Подписка отвечает за устройство, настройки определяют, какие события могут прийти. Service worker сверяет владельца и отпечаток подписки при показе и при клике. Он активирует открытую вкладку или открывает PWA и передаёт то же действие для задания 901.",
    "frames": [
      {
        "title": "Настройки и аудитория проверяются перед отправкой",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/NotificationPushIntentDispatcher.java",
        "language": "java",
        "lines": [
          [
            68,
            "            Set<String> processed = new HashSet<>(job.getList(\"delivered\", String.class));"
          ],
          [
            69,
            "            var candidates = subscriptions.findAllByUserIdIn(original.keySet());"
          ],
          [
            70,
            "            if (candidates.isEmpty())"
          ],
          [
            71,
            "                metrics.dispatch(WebPushOutcomeMetrics.DispatchOutcome.SKIPPED_NO_SUBSCRIPTIONS);"
          ],
          [
            72,
            "            for (PushSubscriptionDocument sub : candidates) {"
          ],
          [
            73,
            "                if (sub.getUserId() == null"
          ],
          [
            74,
            "                        || sub.getId() == null"
          ],
          [
            75,
            "                        || !original.containsKey(sub.getUserId()))"
          ],
          [
            76,
            "                    throw new IllegalStateException(\"Delivery subscription recipient is invalid\");"
          ],
          [
            77,
            "                String key = sub.getUserId() + \":\" + sub.getId();"
          ],
          [
            78,
            "                if (processed.contains(key)) continue;"
          ],
          [
            79,
            "                if (!preferences.isEnabledForUser(sub.getUserId(), eventType, payload)) {"
          ],
          [
            80,
            "                    metrics.dispatch(WebPushOutcomeMetrics.DispatchOutcome.SKIPPED_PREFERENCES);"
          ],
          [
            81,
            "                    continue;"
          ],
          [
            82,
            "                }"
          ],
          [
            83,
            "                Map<Long, Set<String>> current = audience.current(envelope, original);"
          ],
          [
            84,
            "                if (!current.containsKey(sub.getUserId())) {"
          ],
          [
            85,
            "                    metrics.dispatch(WebPushOutcomeMetrics.DispatchOutcome.SKIPPED_AUDIENCE);"
          ],
          [
            86,
            "                    continue;"
          ],
          [
            87,
            "                }"
          ],
          [
            88,
            "                // The stored is_headman/group flag is never an authority for role selection."
          ],
          [
            89,
            "                Set<String> roles = current.get(sub.getUserId());"
          ],
          [
            90,
            "                String role ="
          ],
          [
            91,
            "                        List.of(\"STUDENT\", \"HEADMAN\", \"TEACHER\", \"ADMIN\").stream()"
          ],
          [
            92,
            "                                .filter(roles::contains)"
          ],
          [
            93,
            "                                .findFirst()"
          ],
          [
            94,
            "                                .orElseThrow();"
          ]
        ]
      },
      {
        "title": "Конверт для устройства и действие перехода",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/WebPushDeliveryService.java",
        "language": "java",
        "lines": [
          [
            209,
            "        Map<String, Object> json = new LinkedHashMap<>();"
          ],
          [
            210,
            "        json.put(\"recipientUserId\", subscription.getUserId().toString());"
          ],
          [
            211,
            "        json.put(\"subscriptionFingerprint\", fingerprint);"
          ],
          [
            212,
            "        json.put(\"title\", item.heading());"
          ],
          [
            213,
            "        json.put(\"body\", boundedText(body, 600));"
          ],
          [
            214,
            "        json.put(\"event_type\", eventType);"
          ],
          [
            215,
            "        json.put(\"data\", Map.of());"
          ],
          [
            216,
            "        if (item.action() != null) {"
          ],
          [
            217,
            "            String encoded ="
          ],
          [
            218,
            "                    java.util.Base64.getUrlEncoder()"
          ],
          [
            219,
            "                            .withoutPadding()"
          ],
          [
            220,
            "                            .encodeToString("
          ],
          [
            221,
            "                                    objectMapper.writeValueAsBytes("
          ],
          [
            222,
            "                                            Map.of(\"v\", 2, \"action\", item.action())));"
          ],
          [
            223,
            "            if (encoded.length() <= 384) json.put(\"notificationAction\", encoded);"
          ],
          [
            224,
            "        }"
          ]
        ]
      },
      {
        "title": "Сообщение Web Push · поля без данных привязки",
        "language": "json",
        "example": [
          "{",
          "  \"title\": \"Новое домашнее задание\",",
          "  \"body\": \"Открой приложение для подробностей\",",
          "  \"event_type\": \"homework.published\",",
          "  \"data\": {}",
          "}"
        ]
      },
      {
        "title": "Сверка привязки перед показом и при клике",
        "path": "frontends/pwa-vue/public/sw.js",
        "language": "typescript",
        "lines": [
          [
            125,
            "function matchesPush(binding, envelope) {"
          ],
          [
            126,
            "  return ("
          ],
          [
            127,
            "    binding &&"
          ],
          [
            128,
            "    envelope &&"
          ],
          [
            129,
            "    envelope.recipientUserId === binding.userId &&"
          ],
          [
            130,
            "    envelope.subscriptionFingerprint === binding.fingerprint"
          ],
          [
            131,
            "  )"
          ],
          [
            136,
            "self.addEventListener('push', (event) => {"
          ],
          [
            137,
            "  event.waitUntil("
          ],
          [
            138,
            "    pushSerial(async () => {"
          ],
          [
            139,
            "      let payload"
          ],
          [
            140,
            "      try {"
          ],
          [
            141,
            "        payload = event.data?.json()"
          ],
          [
            142,
            "      } catch {"
          ],
          [
            143,
            "        return"
          ],
          [
            144,
            "      }"
          ],
          [
            145,
            "      const binding = await pushBinding()"
          ],
          [
            146,
            "      if (pushGateClosed || !matchesPush(binding, payload)) return"
          ],
          [
            147,
            "      await self.registration.showNotification("
          ],
          [
            164,
            "self.addEventListener('notificationclick', (event) => {"
          ],
          [
            165,
            "  event.notification.close()"
          ],
          [
            166,
            "  event.waitUntil("
          ],
          [
            167,
            "    pushSerial(async () => {"
          ],
          [
            168,
            "      const binding = await pushBinding()"
          ],
          [
            169,
            "      if (pushGateClosed || !matchesPush(binding, event.notification.data)) return"
          ],
          [
            170,
            "      const action = boundedPushAction(event.notification.data.notificationAction)"
          ],
          [
            171,
            "      const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })"
          ],
          [
            172,
            "      for (const client of windows) {"
          ],
          [
            173,
            "        if (client.url.startsWith(self.registration.scope)) {"
          ],
          [
            174,
            "          await client.focus()"
          ],
          [
            175,
            "          client.postMessage({"
          ],
          [
            176,
            "            type: 'RCT_PUSH_OPEN',"
          ],
          [
            177,
            "            userId: binding.userId,"
          ],
          [
            178,
            "            ...(action ? { notificationAction: action } : {}),"
          ],
          [
            179,
            "          })"
          ],
          [
            180,
            "          return"
          ],
          [
            181,
            "        }"
          ],
          [
            182,
            "      }"
          ],
          [
            183,
            "      // The typed launch remains intent; the signed app session owns target authorization."
          ],
          [
            184,
            "      const url = new URL(self.registration.scope)"
          ],
          [
            185,
            "      url.searchParams.set('pushNotifications', '1')"
          ],
          [
            186,
            "      url.searchParams.set('pushOwner', binding.userId)"
          ],
          [
            187,
            "      if (action) url.searchParams.set('notificationAction', action)"
          ],
          [
            188,
            "      await self.clients.openWindow(url.href)"
          ]
        ]
      }
    ]
  },
  {
    "id": "web-push-subscription-disable",
    "title": "Отключение и очистка",
    "description": "При отключении PWA закрывает показ, удаляет подписку у сервера и вызывает unsubscribe в браузере. Локальное отключение выполняется и при сетевой ошибке. Смена владельца очищает старую привязку. Сервер удаляет подписку при ответе провайдера 404 или 410. Отдельная еженедельная задача удаляет подписки по last_seen, по умолчанию старше 90 дней.",
    "frames": [
      {
        "title": "Локальный показ отключается раньше удаления",
        "path": "frontends/pwa-vue/src/features/push/push-controller.ts",
        "language": "typescript",
        "lines": [
          [
            97,
            "    // Disable display first, including notifications already in the system tray."
          ],
          [
            98,
            "    // An old/unresponsive worker or unavailable IDB must not skip browser retirement."
          ],
          [
            99,
            "    let gateFailure: unknown"
          ],
          [
            100,
            "    const closed = browser.clearBinding(binding).catch((error: unknown) => {"
          ],
          [
            101,
            "      gateFailure = error"
          ],
          [
            102,
            "    })"
          ],
          [
            103,
            "    if (!sub) {"
          ],
          [
            104,
            "      await closed"
          ],
          [
            105,
            "      if (gateFailure) throw gateFailure"
          ],
          [
            106,
            "      return"
          ],
          [
            107,
            "    }"
          ],
          [
            108,
            "    let failure: unknown"
          ],
          [
            109,
            "    try {"
          ],
          [
            110,
            "      if (captured) {"
          ],
          [
            111,
            "        const response = await api(captured, 'DELETE', { endpoint: sub.endpoint })"
          ],
          [
            112,
            "        if (!response.ok)"
          ],
          [
            117,
            "    } catch {"
          ],
          [
            118,
            "      failure = new Error('Сервер недоступен. На этом устройстве уведомления отключены.')"
          ],
          [
            119,
            "    }"
          ],
          [
            120,
            "    // Even after auth/network failure retire the endpoint so it cannot be reused by a new account."
          ],
          [
            121,
            "    let retirementFailure: unknown"
          ],
          [
            122,
            "    try {"
          ],
          [
            123,
            "      if (!retired.has(sub)) {"
          ],
          [
            124,
            "        if (!(await sub.unsubscribe())) {"
          ],
          [
            125,
            "          const active = await browser.subscription()"
          ],
          [
            126,
            "          if (active?.endpoint === sub.endpoint)"
          ],
          [
            127,
            "            retirementFailure = new Error("
          ],
          [
            128,
            "              'Не удалось удалить подписку браузера. Повтори отключение.',"
          ],
          [
            129,
            "            )"
          ],
          [
            145,
            "  function invalidate(): Promise<void> {"
          ],
          [
            146,
            "    const previous = owner"
          ],
          [
            147,
            "    const sub = ownedSubscription"
          ],
          [
            148,
            "    const binding = ownedBinding"
          ],
          [
            149,
            "    ownedSubscription = null"
          ],
          [
            150,
            "    ownedBinding = null"
          ],
          [
            151,
            "    owner = null"
          ],
          [
            152,
            "    const version = ++epoch"
          ],
          [
            153,
            "    if (!browser.supported()) return Promise.resolve()"
          ],
          [
            154,
            "    publish('signed-out', 'Войди в аккаунт студента, чтобы включить Web Push.')"
          ],
          [
            155,
            "    // Close the worker gate immediately while any old subscribe request completes."
          ],
          [
            156,
            "    const closed = binding"
          ],
          [
            157,
            "      ? browser.clearBinding(binding).catch(() => undefined)"
          ],
          [
            158,
            "      : Promise.resolve()"
          ],
          [
            159,
            "    return serial(async () => {"
          ],
          [
            160,
            "      await closed"
          ],
          [
            161,
            "      try {"
          ],
          [
            162,
            "        if (sub || binding) await remove(previous, sub, binding)"
          ],
          [
            335,
            "  async function disable(): Promise<void> {"
          ],
          [
            336,
            "    const captured = owner"
          ],
          [
            337,
            "    const capturedSub = ownedSubscription"
          ],
          [
            338,
            "    const capturedBinding = ownedBinding"
          ],
          [
            339,
            "    const version = ++epoch"
          ],
          [
            340,
            "    if (!browser.supported()) return"
          ],
          [
            341,
            "    publish('busy', 'Отключаем Web Push…')"
          ],
          [
            342,
            "    await serial(async () => {"
          ],
          [
            343,
            "      try {"
          ],
          [
            344,
            "        if (!captured || !current(captured, version)) return"
          ],
          [
            345,
            "        // An explicit disable may target this account's newest binding from another tab."
          ],
          [
            346,
            "        const binding = await browser.readBinding().catch(() => capturedBinding)"
          ],
          [
            347,
            "        const sub = binding?.userId === captured.userId ? await browser.subscription() : capturedSub"
          ],
          [
            348,
            "        await remove(captured, sub, binding?.userId === captured.userId ? binding : capturedBinding)"
          ],
          [
            349,
            "        if (epoch === version) publish('off', 'Web Push выключен на этом устройстве.')"
          ]
        ]
      },
      {
        "title": "Удаление подписки текущего пользователя",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/PushController.java",
        "language": "java",
        "lines": [
          [
            82,
            "    @Override"
          ],
          [
            83,
            "    @RequireRole({UserRole.STUDENT, UserRole.TEACHER, UserRole.ADMIN})"
          ],
          [
            84,
            "    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {"
          ],
          [
            85,
            "        requestContext.requireSelectedRole();"
          ],
          [
            86,
            "        repository.deleteByUserIdAndEndpoint(requestContext.getUserId(), request.endpoint());"
          ],
          [
            87,
            "        return ResponseEntity.noContent().build();"
          ]
        ]
      },
      {
        "title": "Ответ провайдера 404 или 410",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/WebPushDeliveryService.java",
        "language": "java",
        "lines": [
          [
            231,
            "        int status;"
          ],
          [
            232,
            "        try {"
          ],
          [
            233,
            "            status = response.getStatusLine().getStatusCode();"
          ],
          [
            234,
            "            metrics.providerResponse(status);"
          ],
          [
            235,
            "        } finally {"
          ],
          [
            236,
            "            EntityUtils.consumeQuietly(response.getEntity());"
          ],
          [
            237,
            "        }"
          ],
          [
            238,
            "        if (status == 404 || status == 410) repository.deleteByEndpoint(subscription.getEndpoint());"
          ],
          [
            239,
            "        if (status >= 200 && status < 300) touchLastSeen(List.of(subscription.getEndpoint()));"
          ],
          [
            240,
            "        return status;"
          ]
        ]
      },
      {
        "title": "Фоновая очистка по last_seen",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/PushSubscriptionCleanupJob.java",
        "language": "java",
        "lines": [
          [
            46,
            "    public PushSubscriptionCleanupJob("
          ],
          [
            47,
            "            PushSubscriptionRepository repository,"
          ],
          [
            48,
            "            MongoTemplate mongoTemplate,"
          ],
          [
            49,
            "            Clock clock,"
          ],
          [
            50,
            "            @Value(\"${rutcampustrack.push.cleanup.retention-days:90}\") long retentionDays) {"
          ],
          [
            51,
            "        this.repository = repository;"
          ],
          [
            52,
            "        this.mongoTemplate = mongoTemplate;"
          ],
          [
            53,
            "        this.clock = clock;"
          ],
          [
            54,
            "        this.retentionDays = retentionDays;"
          ],
          [
            63,
            "    @Scheduled(cron = \"${rutcampustrack.push.cleanup.cron:0 0 3 * * SUN}\", zone = \"UTC\")"
          ],
          [
            64,
            "    @SchedulerLock(name = \"cleanupStalePushSubs\", lockAtMostFor = \"PT10M\", lockAtLeastFor = \"PT1M\")"
          ],
          [
            65,
            "    public void cleanupStalePushSubs() {"
          ],
          [
            66,
            "        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(retentionDays));"
          ],
          [
            67,
            "        long deleted = repository.deleteByLastSeenBefore(cutoff);"
          ]
        ]
      }
    ]
  }
]
