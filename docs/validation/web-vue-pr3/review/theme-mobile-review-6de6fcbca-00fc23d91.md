# Независимое review mobile/device theme — 6de6fcbca + 00fc23d91

**PASS source review; открытых P1/P2 в просмотренном diff не найдено.** Два P2 предыдущего отчёта исправлены и адресно перепроверены. Это не объявление готовности всех тем или сквозной runtime приёмки.

## Scope и независимость

- Narrow recheck предыдущих findings: shared foundation theme-preference.ts из 6de6fcbca.
- Новый scope: `git diff 4c020f332..6de6fcbca`, 51 файл; device-theme mirror/transport, PWA/TMA adapters, Telegram system source, mobile semantic mappings и SVG component.
- Targeted дополнение: `git diff 6de6fcbca..00fc23d91`, 9 файлов; status text/fill/inverse mappings и meaningful SVG labels.
- Root сообщил integrated revisions b1f2705e6 и d8802de25. Исходники theme.ts/theme-preference.ts/device-theme.ts/SvgIcon.vue перед адресным harness совпадали с 00fc23d91: scoped `git diff 00fc23d91 -- <эти пути>` пуст.
- Продуктовые файлы, Git refs, dependencies, серверы и чужие runtime resources reviewer не менял; commits/install/build/CI/Gradle/Docker не запускал, children не создавал. Изменён только этот новый immutable отчёт. Предыдущий отчёт `/workspace/rct-pr3-review/foundation/review-4c020f332.md` сохранён.
- Запрошенная модель/effort — gpt-6.1-sol/high; независимого runtime metadata API reviewer не получал и runtime model не утверждает.

## Narrow recheck прежних P2

1. **Реальные legacy web keys — FIXED.** `LEGACY_THEME_PREFERENCE_KEYS` содержит `ruttrack.theme` и `web-panel.theme`, а также прежний mobile и переходный web key. Валидный canonical `rct.theme` читается первым. Cleanup выполняется после успешной canonical записи.
2. **Ошибка migration write — FIXED.** Best-effort запись/cleanup обёрнуты отдельным try/catch; валидный legacy выбор возвращается и при `setItem` exception.

Адресный dependency-free Node harness, v24.19.0: все четыре legacy keys × обычная/заблокированная запись возвращают `light`; доступная запись создаёт canonical `rct.theme=light`; blocked write не мешает чтению. Посторонний `account=untouched` сохраняется. При canonical dark + legacy light возвращается dark без записи. Все assertions PASS.

Команда: `node --input-type=module` с built-in `node:module.stripTypeScriptTypes`, прямое чтение проверенных исходников и ESM data imports; shared node_modules не использовались. Один запуск в этом recheck, exit 0. Fake Storage, без настоящего browser storage; это реальная логика reader на синтетическом хранилище.

## Новый diff — результаты source review

**Mirror/transport.** Transport отправляет bearer и device cookie на единственный endpoint, `cache: no-store`; валидирует nullable theme и decimal revision. GET/error/PUT response не представлены UI как ложный ACK. Mirror применяет локальный выбор до admission, сериализует чтение/CAS, сохраняет текущую choice вне auth lifecycle. Новая локальная выборка повышает epoch; поздний PUT не меняет DOM; при смене session key повышается generation, поздний GET не принимается. Ошибка/conflict/неизвестный исход PUT приводит к GET readback до нового CAS, без повторной записи со слепо переиспользованной revision. Цикл ограничен тремя reconciliation attempts; transport failure не создаёт автономный background retry loop. Live system change не превращается в manual preference. Storage events применяются без обратной записи. Disposal снимает подписки и инвалидирует generation.

**Adapters.** PWA и TMA создают global controller с локальной темой либо system; watch передаёт mirror admitted user/session identity + reset generation и обновляемый access token. Logout не удаляет preference и не пересоздаёт theme owner. В TMA system читает Telegram WebApp.colorScheme, подписывается на themeChanged и снимает подписку; manual light/dark от source не меняется. MapScreen/admin screens получают resolvedTheme вместо фиксированного dark. Existing Student/Teacher/AdminProfile/Headman owners передают controller/resolved values вложенным экранам; новые component-local theme controllers не введены.

**Semantics/SVG.** Attendance aliases переключены на shared semantic values; удалён дублирующий light override notifications поверх глобальных токенов, при этом notification type-dot light palette сохранена. Mobile gradients получили theme-dependent aliases; role/status controls и текст на заливке используют подходящие пары. Дополнение 00fc23d91 адресно заменяет status-fill на status-text для чисел/символов, none-circle background, inverse calendar/clear controls и approve button pair. SvgIcon использует исходный SVG как alpha mask с inherited semantic color. Проверка SVG imports в изменённых Vue: использованные assets одноцветные; многоцветные glyphs в этот перевод не попали. Произвольные map/attachment/image previews не заменены маской. Meaningful alt в дополнении превращается в role=img/aria-label, декоративные assets остаются aria-hidden.

**Tests.** Прочитаны новые device-theme.test.ts, telegram-theme.test.ts и изменения theme.test.ts. Они содержат сценарии selection при pending GET (включая явный system), unknown committed PUT readback, CAS conflict/new choice, logout/late response, system-only changes, blocked migration и tab synchronization. Авторские 17 tests/build/typecheck PASS переданы как чужие результаты, reviewer их повторно не запускал и собственными не объявляет. Старое ожидание `data-theme=system` заменено на resolved light по актуальному контракту.

`git diff --check 4c020f332 6de6fcbca` и `git diff --check 6de6fcbca 00fc23d91`: exit 0.

## Явные пределы

- Reviewer не выполнял browser/visual/keyboard/contrast/viewport приёмку. Source review не доказывает, что все существующие PWA/TMA screens уже визуально полноценны в light; это остаётся задачей runtime acceptance владельца пакета/root.
- Реальный Telegram host не использовался; themeChanged проверен чтением adapter/test source, без заявления живого Telegram PASS.
- Device backend mirror HTTP flow не запускался. На момент задания live stand ещё имел старый Auth artifact; backend review/HTTP/Postgres PASS переданы root как отдельное evidence, не reviewer result. Fetch credentials/cookie/CAS/logout/account-switch должны быть подтверждены на актуальном локальном backend.
- Reviewer не запускал новые builds/typechecks/generated-type checks; их переданные PASS не заменяют интегрированную проверку root. Author evidence source-only tsc прежнего patch с отсутствующим @sentry/vue из первого review не переименовано в PASS.

Итог: прежние два P2 закрыты адресным recheck; в дополнительном stable source scope новых actionable P1/P2 не обнаружено. Browser/backend integration limits остаются явно непроверенными этим review.
