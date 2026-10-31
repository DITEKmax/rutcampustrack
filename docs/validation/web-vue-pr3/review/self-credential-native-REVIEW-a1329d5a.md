# Self8 native harness — независимое source review

**CHANGES REQUIRED: три P2 в qualification own-session cleanup и terminal-close boundary.** Runtime/browser/API/DB не запускались; private credentials/handles не читались. Application source fix c586 PASS не пересматривается и остаётся действительным.

Frozen source: `/workspace/rct-pr3-runtime/self-credential-native-after-fix-20261024/prep/native-self-credentials.cjs`, SHA256 `a1329d5aff615379c6975ccfd258319ab8ad0f107d193644f988ce3d96323ec6`. Прочитан весь121-line source, PLAN/FREEZE/четыре публичных templates, предыдущий application review и public final Q3 post19PASS. Пересчитан harness hash и все7 FREEZE hashes: совпали; templates JSON parse PASS. Author syntax-only node-check0 принят без повторения. Requested Sol6.1/high; actual served-model telemetry недоступна.

## F1 — P2: stale bearer401 ошибочно квалифицирует session revocation

**Путь:** `native-self-credentials.cjs:99–102`, особенно `:100`.

`cleanup()` сначала отзывает ADMIN, затем считает любой401 от `/auth/session` признаком `PASSWORD_CHANGED_401` и пропускает exact-own logout. Но отзыв ADMIN в `AuthIdentityOperationStore.java:286–297` очищает selected grant и увеличивает session_version, сохраняя строку session без revoked_at. Поэтому старый bearer становится невалидным при живой сессии, которую обычный refresh может восстановить с retained STUDENT.

**Конкретный путь:** grant CAS4→5 и own login успешно завершены; сбой disclosure/DOM происходит до первого credential POST. `generations=[]` проходит `.every(COMMITTED)`, cleanup делает revoke5→6; прежний ADMIN bearer получает401, код помечает session очищенной, выходит из seed и закрывает contexts с `cleanupDone=true`. Пароль в этом пути не менялся, значит реальной PASSWORD_CHANGED revocation не было. Это ложная cleanup qualification и оставленная новая own8 session.

**Минимальное исправление:** не принимать одиночный401 за revocation. Для own handles нужен canonical exact-SID status/live-own-list либо независимый Root terminal proof; если session ещё жива — только разрешённый normal exact-own logout и проверка. Когда genuine credential COMMIT уже доказан, его реальная all-session revocation может быть causal evidence для ранее существовавших exact SIDs; это не распространяется на путь без credential COMMIT или на последующий login.

## F2 — P2: cleanupDone возможен после ambiguous own login без captured SID

**Путь:** `:48–49,53,69–73,98–102,120–121`.

Attempt сохраняется до POST, но cleanup не требует разрешения всех attempts. Если настоящий own8 login создал session, а `route.fetch` потерял ответ до `note()`, attempt остаётся attempted=true/accepted=false/sessionId=null, список own.sessions пуст. После grant этот failure попадает в automatic cleanup: `.every(COMMITTED)` на пустом generations проходит, ADMIN revoke может успешно завершиться, пустой session loop ничего не проверяет, seed logout выставляет cleanupDone. Hold/Root correlation обходятся, хотя login outcome неизвестен и новая own session может существовать.

**Минимальное исправление:** automatic cleanup completion разрешать только после квалификации **каждого** login attempt, включая attempted без captured response/SID. После безопасного известного revoke сохранять unknown login и держать module/handles до Root квалификации actor/time window; отсутствие captured SID не является отсутствием session. Не выполнять повторный login/password command ради выяснения результата.

## F3 — P2: Root terminal close не фиксирует полный quiescent текущий scope

**Путь:** `:103–111,121`, async interception `:53,60–66`.

Proof привязан к run/admission/root и access keys, но `handlesSha256` сравнивается с actors из сохранённого файла; credential generations/version/SID/state и armed/pending facts в correlation digest не входят. Нет связи checkedAt с последним изменением scope. До qualification не блокируются новые browser API и не дожидаются in-flight login/credential `route.fetch`/response observers; `closing=true` ставится лишь в finally после принятия proof, без повторной проверки handles.

Это оставляет окно для late accepted login/response: Root proof квалифицирует сохранённый handle set, затем ранее начатый route.fetch завершает login и `note()` добавляет SID уже после qualification, во время закрытия context. Та новая session не входит в принятый proof. Другой run proof уже отвергается, но это не закрывает изменение того же текущего run после observation. Аналогично stale same-run proof не связан с точным credential-outcome inventory.

**Минимальное исправление:** использовать уже принятый компактный pattern ACLv2: exact current-run realpath/0600; полный текущий scope digest (all access originals + credential attempts/outcomes без секрета в public output + actors/login windows/SIDs); observedAt>=last scope change и короткий bounded expiry. Для close seal новых browser API, drain уже запущенных authority/credential interceptors и observers, сравнить proof с actual in-memory scope и повторить проверку непосредственно перед close. При изменении — hold, никаких recovery mutations. Новый framework или успешная runtime кампания для исправления source predicates не нужны.

## Что проходит и сохраняется

- Scope ограничен main own8, исходным profile4/unassigned/RV6/Cred5 и fresh readiness/baseline; seed1 используется как sole FULL grant/revoke writer, own8 account/STUDENTgroup1 сохраняются. Currentpassword5 читается только при исполнении из exact разрешённого private source. Reviewer этот файл не открывал.
- Admission требует actual exact Root HEAD, individual hashes relevant dependencies и clean tracked bytes; reviewed fix marker и proof остаются Root integration gate. Нет hard whole0421 rollback. Реальный main public-key digest сверяется напрямую и через UI proxy, backend artifact/readiness claims привязаны к fresh Root proof.
- Access original UUID/body/CAS/seed SID и credential attempt persist+fsync происходят до forwarding. Неизвестный credential outcome оставляет UNKNOWN и блокирует новый credential POST/revoke; не выполняется blind retry. Grant/revoke ambiguous outcome сохраняет original и требует его SUCCEEDED перед продолжением. Два credential POST максимум, grant4→5/revoke5→6 максимум; old Q3 commands не повторяются.
- Known native response используется без подстановки password/receipt/session; unknown case теряет только настоящий client ACK после genuine typed200. Safe natural retirement может остаться `KNOWN_OUTPUT_UNOBSERVED`, без дополнительного POST ради зелёного результата. Successor normal own login использует видимый login form и должен очистить старый receipt; source owner isolation остаётся по принятому c586 review.
- Вывод не печатает raw errors/password/token/session keys; secret остаётся в памяти и private0600 handles. Private fixture значения не читались. Ошибка продукта не исчезает из диагностики от последующей known cleanup.

Создан только этот отчёт. Существующие ACLv2 qualification/rawFAIL, Q3 evidence и product source не изменены. Reviewer test/build/runtime/API/browser/private reads=0. Перед Root execution нужны узкие corrections/recheck перечисленных cleanup/close predicates; ещё одна credential generation для source review не требуется.
