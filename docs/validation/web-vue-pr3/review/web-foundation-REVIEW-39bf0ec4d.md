# Independent narrow delegated-view review — 39bf0ec4d

**PASS source review. Подтверждённых новых P1/P2 в correction не найдено.** Повтор реального root969 сценария остаётся отдельным runtime gate после этого review.

Scope только frozen `39bf0ec4da3b4cf09fe00ce6b3232916d3e489e6`: assistant store, AppShell boundary composition, AssistantRouteBoundary и три mounted causal tests. Exact author HEAD39bf, clean tracked status подтверждены. Headman/backend/auth/profile baseline не переоткрывались; shared typed API/transport прочитаны только для async/error контракта. Продуктовые файлы/серверы/fixtures/dependencies/Git refs не менялись; без children/browser/Gradle/CI/широкого rerun. Создан только этот immutable report. Запрошенная модель/effort gpt-6.1-sol/high; независимой runtime telemetry нет.

## Проверенные границы

- **Reactive render gate.** Boundary требуется только для STUDENT route с assistantPermission. Slot/RouterView существует лишь при loaded canonical permissions, содержащих требуемое право. После canonical[] прежний private subtree действительно unmounts, запускает его обычный cleanup; скрывается не только navigation. HEADMAN/остальные роли без delegated requirement не блокируются этой boundary.
- **Сохранение drafts/focus.** Force GET при той же identity не сбрасывает loaded/последний canonical permissions до ответа. Одинаковый grant не заменяет subtree/DOM, не уничтожает unsaved selection и не перемещает focus. Это не выдаёт новый grant по ошибке/пустому ответу: canonical[] после decode заменяет permissions и закрывает view.
- **Coalescing.** Force во время current flight отмечает один pending canonical reread; ожидающие callers получают завершение всего drain. Pending invalidation проверяется и после успешного старого ответа, и после ошибки старого запроса. Следующий awaited typed GET обновляет права; нет самостоятельного background retry loop.
- **Flight cleanup.** Текущий ticket очищает flight/checking перед разрешением promise. Finally старого ticket не очищает flight/checking нового owner. Реальный listMyPermissions — async typed method, поэтому его failure проходит await/rejection path; синхронное окончание cleanup после await не оставляет settled flight для следующего force.
- **Identity/error.** Sync identity watcher очищает permissions/loaded/owner/ticket и запускает отдельный current STUDENT read. Captured transport проверяет identity после HTTP/decode, поздний A не публикуется в B. Ошибка initial read не монтирует private slot; boundary даёт retry после checking=false. Неопределённая ошибка forced reread сохраняет прежнюю canonical projection согласно заданному draft-preservation поведению, error отделён от authoritative absence[].

## Tests и evidence

Прочитаны три meaningful tests с actual mounted AssistantRouteBoundary и отдельным probe component: held oldGET + force revoke → second canonical[]/nav+tables removed/cleanup1; same-grant refresh сохраняет тот же input DOM/draft/focus; identityB не принимает lateA и не показывает old tables/nav. Авторские build/typecheck/lint и3causal PASS переданы root; reviewer повторно их не запускал без нового риска. Собственное `git diff --check 39bf0ec4d^ 39bf0ec4d` — exit0.

Прочитан исходный `/tmp/rct-headman/delegated-root969-results.json`: настоящий old-root969 grant/read200 → revoke200 → STOMP + own-permission refresh, nav removed и protected REST403, но две старые таблицы оставались. Это **воспроизведение исходного дефекта**, не corrected runtime PASS. Frozen correction устраняет именно отсутствующую render boundary; данный source review не подменяет один назначенный root/headman повтор того же сценария на assembled3f585.

Новых source corrections по этому patch не требуется. Никаких утверждений о полном HEADMAN/helpers runtime inventory, browser compatibility или повторной auth-приёмке этим узким PASS нет.
