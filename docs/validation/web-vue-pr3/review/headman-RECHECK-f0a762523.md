# Узкая независимая recheck f0a762523

Результат: **четыре исходных P2 закрыты по source; новых подтверждённых P1/P2 в correction нет**.

Scope: только correction `f0a762523026578a03f14f382ca22ccb8c4c1147` и её влияние на findings отчёта REVIEW-124334c54.md. Общий live bus/assistant access в parentf839 не переоткрывался, unfinished Home/geo/download не принимались. Read-only, без source edits/commits/push/CI/heavy/детей. Запрошенная модельgpt-6.1-sol/high, фактическая runtime telemetry недоступна.

- **OTHER reason: PASS.** choose использует initialExcuseType(entry). Поддерживаемый existing OTHER сохраняется; неизвестный reason даёт пустой required choice и не подменяется ILLNESS, save отклоняет неподтверждённый reason.
- **Unknown EXCUSED payload: PASS.** Command строится до await и readback сравнивается через markReadbackMatches по status, excuseType и comment. Выбранный новый file не подтверждается только метаданными, поскольку report не имеет digest. Несовпадение явно показывает ошибку, старый EXCUSED больше не выдаётся за подтверждение новых полей. В последующем a6 эта же ветка дополнительно сохраняет uncertainty до явного принятия текущего server state, что рассмотрено отдельно.
- **Audit page owner: PASS.** LessonActionsReader имеет request revision, поздний A result после B возвращает null. loadAudit захватывает previous/cursor только для той же пары, обновляет selection до await и применяет только latest result; двойной запрос той же страницы не добавляет её дважды к уже изменившемуся массиву.
- **Terminal export restart: PASS source.** Job state error и typed404/410 дают terminal marker и явную кнопку «Создать новую выгрузку», удаляющую старый intent перед новым UUID. Unknown network outcome сам по себе terminal не становится и сохраняет прежнюю identity. Existing ReportDownloadClient вызывает state listener до terminal-error throw, поэтому новый control достижим для рассматриваемого failure. Browser renderer не повторялся.

Авторские три regression tests прочитаны: OTHER/unsupported reason, full payload/file proof, delayed A audit versus B. Их PASS повторно не запускался. Однократный dependency-free causal harness на точном frozen journal-state.ts подтвердил OTHER→OTHER, unsupported→empty, old EXCUSED comment mismatch=false и delayed auditA result=null/latest lessonB. Это проверка async/source causality с минимальными stubs, не настоящий backend/browser PASS.

`git diff --check f0a762523^..f0a762523` — PASS. Live-refresh wiring блокирует refresh при открытом журнале/decision detail/pending mutation; owner/secret invariants прежнего scoped transport не заменяются этим hook. Применимость Stomp/runtime renderer/shared bus принимает root отдельным scope.

Этот source correction gate закрыт. Новые group/homework findings вa6 не относятся к данным четырём закрытым P2 и отражены отдельным отчётом.
