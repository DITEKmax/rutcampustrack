# Передача координации полной роли студента

08.09.2026, новое прямое поручение владельца: ускорить разработку несколькими параллельными задачами в этом проекте, перенести оркестрацию в свежую задачу, охватить всю роль без потери качества и согласованности. Это решение заменяет прежнее ограничение координации одним старым чатом; скрытый CLI orchestrator не нужен.

## 1. Goal

Новый root принимает координацию всех 39 финальных состояний роли студента в PWA и TMA через mobile-core. Создать несколько пользовательских задач по независимым направлениям, затем автономно довести полный scope до проверенного результата. Старый чат «Реализовать роль студента», id 01a07875-93f4-7a30-80fd-f34d0741e9ce, только завершает передачу трёх активных исполнителей; новых implementation scopes он больше не назначает.

## 2. Context/evidence

Канонический существующий каталог evidence: C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02. Читай адресно active-contract.md, matrix.md, integration-order-2026-09-07.md, текущий handoff и нужные packets; не перечитывай огромную историю целиком. Все 39 Figma источников уже сверены. Исходные решения и ссылки находятся в active-contract/matrix/source notes; повторный общий аудит не нужен. Исторический PAUSED.md отменён последующими «продолжаем».

Main HEAD: 8002b9ea4356b10779c5bb9a6d99746d32d78ae2. Принятый isolated integration commit d3c31acb8cce53791a4981e5858a37d44fdc9a0e, branch codex/student-role-02-integration: 42 paths shell + Homework API. Main product не интегрирован. 47 исходных owner dirty файлов нужно сохранять; последний guard main-owner-guard-2026-09-07-2037Z.json. Config исходный limit3, SHA1CE988CB4230EDCBED60FCD3A96D583F809C8FE274F9B3277A3D3894704CEE28. Не менять лимиты вслепую. Ownership gateway поиском не доступен; это не успешный claim.

Принятые дополнительные источники:
- Homework shared UI: homework-shared-ui-acceptance.json + homework-presentation-recheck-result.md PASS. Frozen25 homework-presentation-recheck-source/manifest.json SHA6F0293C15D1FB8481813181532A12C719290E8E19E93EA232F9D2E878F3151ED. Worktree .agent/worktrees/student-role-02/homework-ui. 28 тестов, обе сборки/typecheck/lint, финальные реальные component screenshots. Это принятие shared UI, не shell/real API/TMA.
- Requests domain32: requests-domain-review-3-result.md PASS + requests-review-3-source/manifest.json. Весь transport ещё не принят.
- Dependencies combined45: dependency-acceptance.json + dependency-combined-review-result.md PASS. Frozen dependency-combined-review-source/manifest.json SHA3E795CB3F0CB8E95944BCBF1DC623E197554FAC24C5F5D2F5BB65ADFDDEF1D6A. Worktree dependency-checks. Полный gradlew check --no-parallel --console=plain exit0: 253 suites,1647 выполнено,4 старых @Disabled,0fail/error. Архив dependency-root-check. Security evidence:8 JAR,1141 packages,0HIGH/CRITICAL,57MEDIUM; точная связь JAR/исходников проверена, не выдавать это за новый scan объединённого будущего кода.

## 3. Relevant scope and ownership

Старые активные области RESERVED до отдельного handoff/release от старого root:
1. homework_adapters_fresh (Luna max developer) — .agent/worktrees/student-role-02/homework-adapters, branch codex/student-role-02-homework-adapters. Packet homework-adapters-packet.md. Baseline d3c31 + accepted25. PWA/TMA shell/auth generation/offline IDB/Today lifecycle; НЕ StudentApi/generated/backend. Нового writer этой области пока не назначать.
2. requests_transport_fresh (Luna max developer) — .agent/worktrees/student-role-02/requests-transport, branch codex/student-role-02-requests-transport. Packet requests-transport-implementation-packet.md; accepted32 imported. StudentApi, Attendance/BFF public and bot gRPC, proto, Python bot, events/retry/DLQ, Java-first artifacts. Пока нет stable acceptance. Gateway/nginx отложены.
3. gateway_forwarding_decision_fresh (Sol xhigh read-only) — packet gateway-forwarding-decision-packet.md; результат ожидается. Не дублировать консультацию и не реализовывать forwarding до решения.

Все трое получили запрос завершить близкий bounded handoff либо checkpoint после текущего безопасного шага и остановить запись. Старый root сообщит точное состояние. Новый root уже может вести независимые направления ниже. Не управляй старыми subagents через guessed IDs; взаимодействуй со старым root send_message_to_thread.

## 4. Required behavior: новая организация

Создай свой компактный registry с task ids, owner paths, frozen baseline/hash, dependency gates, runtime ports/DB и статусом. Единственный автор registry/общего status — новый root. Рабочие задачи пишут свои evidence; root принимает результаты. Сам root/explorer/reviewer не пишет product code. Fresh implementation Luna max, review Sol high; Terra только recorded defect/complexity gate. Модели/effort и fresh packets по пользовательскому AGENTS; критичные консультации Sol xhigh отдельно прямо разрешены владельцем ранее.

Рекомендуемые параллельные пользовательские задачи (новый root создаёт их сам по текущему прямому поручению владельца):
A. Авторизация и профиль: profile-auth-decision-packet.md, profile-auth-source-note.md и confirmed auth-token-purpose-probe/session-retry-root-probe. Сначала server role/session/revocation contract, затем bounded implementation и семь profile states. Согласовать Academic proto/migration reservation с B до записи. Gateway token enforcement — согласовать с результатом C, не параллельно переписывать одни фильтры.
B. Учебная модель, посещаемость и статистика: subject-occurrence-decision-packet.md, statistics-decision-result.md, statistics-root-decision.md, lesson-lifecycle-source-note.md, attendance-source-note.md. Сначала authoritative subject/type/assignment/occurrence/transfer/lifecycle contract; затем backend foundation и8+5 UI states. Сохранять историю отмены/восстановления/переноса, без name-derived IDs и arbitrary backfill. Academic migration/proto конфликт с A решать frozen reservations/отдельными worktrees и последовательной генерацией.
C. Заявления, Gateway и сборка текущих блоков: до handoff старых writers только адресный read-only preflight и план интеграции. После handoff fresh review transport, затем Gateway forwarding/single-chain/24MiB ingress repair, Requests forms+5 screens и интеграция Homework adapters. Это направление требует точного порядка общих контрактов. Может быть ответственностью самого нового root с bounded leaves вместо отдельной задачи, если это лучше ограничит число root-координаторов.
D. Карта и независимые оставшиеся UI: map-decision-packet.md и соответствующие source notes/1final state; не придумывать данные кампуса. Начать с узкого source decision, затем выделенные feature files. Никаких общих shell/api/generated изменений до выделения ownership. После freeze необходимых APIs выделить отдельные FE leaves Attendance/Statistics/Profile/Requests в независимых worktrees. Общий shell/styles/index/generated имеет одного интегратора.

Начальная разумная ширина: новый root +3–4 рабочих направления, в каждом bounded leaf/review по необходимости. Не увеличивать слепо число тяжёлых Gradle/Testcontainers процессов; reserve runtime и запускать общую батарею один раз на стабильном объединении. Разные задачи — пользовательские peer tasks, а не вложенный слой скрытых coordinators. Ограничения read-only coordinator и sole writer сохраняются.

## 5. Constraints

Никаких deploy/production migrations/удалений backup или данных/firewall/secrets/external messages. Реальные Telegram/OTP сообщения не отправлять. Genuine Telegram evidence остаётся явным gate; simulated host не является PASS. Owner стенд больше не просит, старый preview5183 остановлен. Main и чужие dirty файлы не трогать. Работать в отдельных worktrees; main .agent evidence читать, новые task artifacts писать в назначенные own каталоги. Новый root может начать в app-created worktree и читать исходный каталог по абсолютному пути; accepted uncommitted source bundles импортировать только с manifest/hash verification.

## 6. Existing patterns and known defects

Dependency и Requests пересекаются в docs/openapi/mobile-bff.json и frontends/mobile-core/src/api/generated/mobile-bff.ts. Итог регенерировать из объединённых Java contracts/dependency exporter, а не выбирать целиком одну версию. Не перезаписывать failed evidence/screenshots. Homework false generic range finding был независимо снят фактическим composable probe; не открывать его снова без нового evidence.

Подтверждены Auth refresh-as-access acceptance в реальном filter probe (ROLE_null), Gateway double chain и forwarding trust boundary; полную эксплуатацию не утверждали. Shell lifecycle pending-refresh/logout resurrection и old-A request retry under B подтверждены локальными actual-source probes; Homework adapters исправляют client boundaries, server revocation отдельная задача A.

Последний root transport audit: RabbitConfig factory теперь bounded3attempts/DLQ на всю Attendance queue, real RabbitDecisionRetryIT2test PASS author. Ещё НЕ root accepted. Root обнаружил два production null-fallback studentRequestService==null → old LateCheckin/Excuse service в EventConsumer; отправил текущему writer correction gate: удалить fallback, обновить stale tests, прогнать consumer/Rabbit checks. Это известный незакрытый дефект до handoff. Проверить влияние factory на lesson/semester событийные пути и не называть source строку криптографической auth.

## 7. Acceptance criteria

Полные39: Today6 partial; Homework5 shared accepted/adapters pending; Attendance8 pending; excuse form2 pending; Statistics5 pending; Map1 pending; Requests5 pending; More/Profile7 pending. Обе оболочки, реальные APIs, loading/empty/errors/retry/recovery/authz/offline boundaries, responsive/theme/font/focus, final-source Figma screenshots и межэкранные переходы. Fresh independent important reviews; закрытые critical findings; итоговый scope diff и owner guard. Не объявлять full-role PASS по component fixtures.

## 8. Verification and takeover

Новый root сначала прочитывает этот документ и active-contract, создаёт registry и независимые задачи, сообщает старому root свой id/пути и подтверждает ownership rules. Старый root остаётся только drain трёх текущих агентов и передаёт manifests/checkpoints/results. После release новый root принимает их дальнейшее review/implementation. Оба root не назначают одну область одновременно. Использовать wait_threads с cursors для рабочих задач; не создавать дубликаты. Пакеты по девяти разделам, source/evidence критичных решений проверять оригиналами. Общие backend/frontend checks запускать после интеграции; более узкие реальные negative checks — по каждой области.

## 9. Do not

Не перечитывать всю старую задачу; не копировать огромный transcript. Не считать активных старых writers завершёнными. Не пересоздавать принятый код, не забывать uncommitted accepted bundles. Не снижать review/runtime quality ради ширины. Не менять глобальные configs или persistent orchestration plumbing. Не спрашивать повторно разрешение на уже порученные локальные задачи/создание параллельных чатов; недостающие продуктовые решения уточнять только после адресной сверки источников.

## Transfer updates (binding latest state)

Новый root подтвердил takeover: task 01a07dbc-9d4c-7140-a019-886cf482d09a, cwd C:/Users/maksd/.codex/worktrees/1267/rutcampustrack. Его единственный текущий registry: C:/Users/maksd/.codex/worktrees/1267/rutcampustrack/.agent/student-role-orchestrator/registry.md. Создание трёх независимых пользовательских задач A/B/D отправлено им в приложение; их ids и дальнейший статус принадлежат этому registry. Старый root больше не назначает implementation/review; только проверяет и передаёт stable checkpoints двух оставшихся областей.

07.09.2026 21:20 UTC: Homework adapters writer STOPPED, checkpoint only, НЕ implementation PASS. Root прочитал checkpoint и проверил35listed hashes:0mismatch, checkpoint SHA C8503AF7ED5B4DE2A1970FD264606148BD4598387B29339DE6AC866E5DBED74B. Evidence homework-adapters-transfer-verification.json. Checkpoint по абсолютному пути C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/homework-adapters/.agent/student-role-02/homework-adapters-checkpoint.md. Scope RELEASED новому root для fresh bounded continuation. Уже изменены session-owner/auth/tma-session/offline/Today helper boundaries; App.vue обеих оболочек НЕ подключены, tests/typecheck/build/runtime НЕ запускались, node_modules отсутствует, процессов нет. Продолжение уже разрешено текущим поручением владельца; фраза автора «только после явного продолжения» означает ownership handoff, а не необходимость нового согласия пользователя. Сохрани текущие bytes, назначь свежий Luna max leaf с root packet+checkpoint, не перезапускай завершённого автора для новой работы.

Gateway consultation STOPPED/RELEASED новому root: полный результат сохранён неизменным в gateway-forwarding-decision-result.md, SHA AC51CEB7099DB442AE8264EFDBDABC0A59D8B44C8C37874F37027D0AA8330187. Четыре findings и9sectioncontract переданы; adoption и implementation ещё впереди, runtime N/A, current gate FAIL. Новый root уведомлён. Осталась RESERVED только Requests transport до checkpoint.

07.09.2026 21:38 UTC: Requests transport STOPPED/RELEASED новому root. Root прочитал summary/runtime/checks и проверил91listed hashes0mismatch. Manifest old WT/.agent/student-role-02/diff.json SHA4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4; root requests-transport-transfer-verification.json. 91paths включает evidence/imports:46modified45untracked, отделить product перед импортом. INCOMPLETE_CHECKPOINT_NOT_ACCEPTED: combined Rabbit11tests1FAIL WantedButNotInvoked93, isolated green; Pythonpytest/ruff unavailable; full transport runtime/review OPEN. Nullfallback corrected author+focused10PASS, rootfinalproductreview ещё нет. Owned runtime none. Все три прежние области переданы; дальнейшая координация и новые assignments исключительно у task01a07dbc-9d4c-7140-a019-886cf482d09a. Старый root завершает передачу, не продолжает параллельную оркестрацию.
