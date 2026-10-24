# Theme foundation и device persistence — технический контракт

**Актуальный статус2026-10-24: реализовано, API существует и проверен.**
Независимые foundation/theme/device reviews и actual cookie/CAS/device/
logout-login smoke доступны в API-CHANGES и review manifest. Mobile source24
families,140 retained visual samples и narrow09e7 recheck12 кадров не равны
функциональной проверке каждого состояния; signed native Telegram не заявлен.

Ниже сохранён технический proposal, исследованный на4ae4e69e; его формулировки
«сейчас lockedMode/API ещё не существует» описывают исторический baseline,
а не состояние текущего checkout. Поведение/границы остаются контрактом.
Поведение уже принято владельцем: light/dark/system; manual приоритетнее;
browser device preference переживает logout/login; TMA system=Telegram,
web/PWA system=ОС; live system change без reload. Без fingerprinting.

## Повторное использование

- mobile-core/src/shared/theme.ts — один DOM owner, subscribe/dispose;
  shared/theme-preference.ts — validated rct.mobile.theme вне auth.
- PWA/TMA App.vue сейчас lockedMode dark; MapScreen получает dark явно.
  Appearance имеет отдельный OS preview, который больше не соответствует GO.
- Общий нейтральный источник font/tokens/type/focus/responsive mixins может жить
  в frontends/shared/foundation. Web импортирует нейтральную основу, без
  mobile scrollbar suppression/viewport/dock globals из mobile base.pcss.
- Existing MobileTheme/ProfileTheme exports сохранить compatibility aliases.
  Controller получает system source read/subscribe; он не знает auth/Telegram.
  Browser source=matchMedia; Telegram source=WebApp.colorScheme/themeChanged
  в платформенном adapter. data-theme содержит resolved light/dark.
- Единый validated storage key мигрирует rct.mobile.theme и legacy web keys.
  Storage exceptions безопасны; storage events синхронизируют вкладки.
- Light semantic palette нужна глобально. Отдельно проверить attendance-tokens,
  headman-group accent aliases, notifications palette, Today gradients и SVG
  с fixed colors. Одного удаления lockedMode недостаточно.

## Минимальный backend

Browser-facing GET/PUT /api/auth/device-preferences, внутренний путь
/auth/device-preferences. Existing gateway auth route StripPrefix=1 подходит.

- GET: bearer + opaque device cookie → theme:null|light|dark|system и revision
  decimal string. Null только для ещё не выбранной темы.
- PUT: theme и expectedRevision → сохранённая theme/revision, атомарный CAS.
  Нет user/session/device ID в body, нет списков устройств/телеметрии.
- Каждый вызов требует живую bearer session; cookie только selector.
  Разрешить точные GET/PUT bootstrap-allowlist в ДВУХ JwtAuthenticationFilter
  (auth и gateway), без cookie-auth/public fallback/расширения соседних путей.
- Cookie: случайный32-byte selector, Secure/HttpOnly/SameSite=Strict, без Domain;
  при __Host- prefix Path=/. Не менять существующий refresh cookie/CSRF protocol.
- GET неизвестного/истёкшего контекста создаёт новый. PUT без контекста→409,
  затем GET и reconcile актуального локального значения.
- Отдельная auth table хранит selector hash, nullable theme, revision, expiry,
  без account/SID/IP/UA. Номер новой Flyway migration перепроверить при записи.
- Reuse JdbcTemplate/TransactionTemplate REQUIRED и AuthService.admit:
  user→session admission locks живут до commit preference; затем device lock.
  Existing JdbcSessionAuthority.snapshot присоединяется к outer transaction.
- Ограниченный expiry cleanup по существующему QR cleanup pattern; срок и
  batch — технические настройки, без удаления sessions/audit.
- no-store. RFC9457 validation/auth/revision/context-lost/unavailable errors.
  Не включать selector/cookie/SQL в JSON/logs. Logout очищает только auth,
  сохраняет theme storage/cookie. Backend это durable mirror/fallback.

## Reconciliation и неизвестный исход

Локальная тема применяется до входа/offline. После admission GET:
если локальный выбор есть, он авторитетен и сохраняется CAS; иначе принять
server choice, null=system. Поздний response не откатывает более свежий
selection epoch/auth generation. Последовательная запись; при conflict
bounded reconcile, при неизвестном исходе сначала readback, без blind retry.
Другие browser profiles независимы даже при общем аккаунте.

## Проверки при реализации

Реальный HTTP + PostgreSQL/Auth Flyway через существующий DevicePreferencesHttpIT
по AbstractIntegrationTest/AuthDatabaseFixture patterns. Fixture cleanup должен
учесть новую таблицу. Проверить: reload/logout/login и смена аккаунта в одном
cookie jar; независимый jar; cookie без bearer/revoked/stale denial;
bootstrap/read-only admission; CAS conflict; invalid input/expired context;
unavailable DB без ложного ACK; cookie attributes и отсутствие selector leakage.
Адресные gateway allowlist и existing SameSiteCookieContractIT сохраняются.
Отдельное независимое review auth/persistence diff.

Команды и test names уточняются автором по реальному checkout; никакой PASS
этим документом не заявляется. Реальные изменения затем записать в API-CHANGES.md.
GitHub CI/Coverage/Security/OpenAPI PAUSED; production migration не выполняется.
