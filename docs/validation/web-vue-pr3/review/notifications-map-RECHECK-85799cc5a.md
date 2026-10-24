# Narrow independent recheck — 85799cc5a

**Notifications: PASS — прежние два P2 закрыты.**

**Map: PASS — прежний P2 закрыт.** Новых P1/P2 в correction и её затронутых границах не найдено.

Scope: только corrections frozen `85799cc5afbdfc089978c36d79891915e073fff1` относительно REVIEW-5c547ccf9.md: latest head cursor/read serialization, reachable unknown recovery, completion missing map format при preserved preview. Exact author HEAD85799cc5a и clean tracked status подтверждены. Никакого повторного fullsource/Figma/browser/Gradle/CI/test suite или product write; создан только этот immutable report. Запрошенная модель/effort gpt-6.1-sol/high; независимая runtime telemetry отсутствует.

## Notifications P2 cursor gap — CLOSED

Latest accepted head теперь всегда задаёт `nextCursor`, а head и More проходят общую serialRead chain. Queued More читает cursor уже принятого нового head, не перескакивает burst gap по прежнему cursor. Id dedup и mutation/readRevision fences сохранены. Addressed regression test использует initial[a,b,c,d]→[x,y,z,a,b,c,d] с задержанным head и queued More, проверяет присутствие z, полный union и завершение chain; тест не навязывает конкретный DOM/layout.

## Notifications P2 unreachable recovery — CLOSED

Новый NotificationRecovery подключён и в bell, и в полной истории. При uncertain есть доступная кнопка проверки, disabled пока pending/reconciling. Сам reconcile выполняет canonical GET/readback, не повторяет mutation, различает завершённый actual state/absence и bounded incomplete chain. Текущая подтверждённая revision возвращается в cards; если прежний эффект не доказан, UI честно сообщает это и разрешает новую явную команду, сохраняя CAS. Existing mutation queue/identity guards сохранены. После keyboard recovery focus переходит на confirmation status только для того же store.

Прочитан exact-source857 внешний receipt `/workspace/rct-pr3-runtime/web-foundation/notification-recovery.json`: owned actor11/read-state200 с потерянным ACK и недоступным readback → uncertain control; Enter recovery → GET-only canonical reads, controls enabled, confirmation focus, no repeated mutation; затем отдельная явная команда восстановила исходный read state. `writesAtRecovery=1`, errors[]. Это авторское реальное runtime evidence; reviewer browser/публикации не запускал.

## Map P2 missing sibling format — CLOSED

Same-plan preservation больше не делает complete early return по единственному preview. Готовые assets/URL сохраняются; fetch запускается для каждого missing ready format. Прежний PNG sequence после abort не публикуется, replacement PNG загружается под новым sequence. Existing SVG URL, open intent и uncertain analytics state при same plan не сбрасываются. Targeted regression test удерживает исходный PNG, получает SVG, refresh того же плана, завершает старый PNG и проверяет сохранённый SVG object, новый published PNG, guardAsset, два активных URL и cleanup до0.

## Evidence и пределы

Прочитаны новые адресные tests для трёх причинных условий, включая unknown outcome с актуальной revision и новым explicit CAS. Переданные10targeted tests PASS повторно не запускались без нового дефекта/риска. Собственное `git diff --check 5c547ccf9 85799cc5a -- frontends/web-vue/src/features/notifications frontends/web-vue/src/features/map` — exit0. Ранее прочитанный source/diffs не переоткрывались за пределами влияния correction.

Общую map visual/native-download/keyboard/runtime приёмку этот narrow source recheck не повторяет и не объявляет выполненной: она относится к отдельным реальным receipts/root. Notifications actual recovery receipt подтверждает именно corrected flow, не все роли/браузеры/viewport combinations. Предыдущий immutable REVIEW сохранён. Все три отмеченных там P2 теперь закрыты; отдельного расширенного review для этой correction не требуется.
