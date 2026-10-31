# Narrow source qualification — direct Auth guest refusal after host-v4

**SOURCE QUALIFICATION: direct anonymous `/auth/session`403 является корректным отказом Authf3. Real-password negative criterion выполнен существующей core evidence; повтор confirmations не нужен. Raw helper native1/passedfalse/GUEST_NOT_401 остаётся неизменным.** Полная локальная acceptance дополнительно требует отдельный actual own-PG/session/runtime post-read; он этим report пока не принят.

Дата2026-10-24. Root10d8/product0421, actual Auth sourcef3c03bd81b3d44873abff2ad7126d9a000e51381; reviewed PREP host-v4 SHA3f283ec9. Назначение Sol6.1/high сохраняется; runtime model telemetry недоступна.

## Контрольные пути и статус

Прочитан существующий `host-v4-attempt/RESULT.json`; его SHA256 независимо совпал: `2aa1db8451ff65ebd3bb55e4cbe119c9b6f005fa27bcd0a918436087ecae8ba3`.

- normal login200, ADMIN role200 в same own session, genuine live admit200;
- production AuthMapDeletionClient correct-password204, затем ровно один wrong-password403 с **CURRENT_PASSWORD_INVALID**; confirmationCalls2, failureCounterAfter1/accountBlockedfalse/proofCompletetrue;
- ordinary logout204; последующий **anonymous direct Auth** guestStatus403;
- nativeExit1, probe.passedfalse, failureStageLOGOUT/failureCodeGUEST_NOT_401; Academic deletion calls0;
- container projection unchanged; recorded memory.events до/после одинаковы. Исторические ненулевые oom/oom_kill counters не выдаются за отсутствие исторических OOM; нового increment в этом receipt нет. Global Academic healthyfalse сохраняется.

Таким образом отказ не произошёл в issuer/authority/password controls: после всей доказанной core sequence helper упал на отдельном overly-specific guest status assertion.

## Почему direct403 совместим с Auth source

1. Frozen Authf3 `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java:39–73`: stateless custom SecurityFilterChain. `/auth/session` отсутствует в permitAll и попадает в `.anyRequest().authenticated()`. Form login, HTTP basic или custom authenticationEntryPoint здесь не включены. Стандартный Spring Security fallback для такой chain — Http403ForbiddenEntryPoint, то есть403 при попытке anonymous protected access.
2. Authf3 `config/JwtAuthenticationFilter.java:65–171`: специальный no-Bearer writeSessionFailure применяется к device-preferences/generated-password paths; для `/auth/session` без Bearer основная Bearer branch пропускается, authentication не устанавливается, запрос доходит до security authorization. Observed403 соответствует этому пути. AuthSessionController.currentSession():53–55 вызывает admit только после authenticated admission; anonymous403 не является успешным current-session read.
3. Probe `AuthMapConfirmationProbe.java:26,51–60,165–170`: base URL именно `http://auth-service:9090`, logout с Bearer, затем GET `/auth/session` с `bearer=false/service=false`. Это другой boundary, чем browser `/api/auth/session` через Gateway. Guest request не проверяет повторное использование отозванного bearer.
4. Frozen Gateway0421 `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java:114–128`: missing/blank Bearer на non-public route явно возвращает **401 INVALID_SESSION**. `/api/auth/session` не public login/logout route. Поэтому сохранённые browser logout/guest401 результаты не противоречат direct Auth403. Прямое перенесение Gateway401 expectation на host-helper было ошибкой harness expectation, не подтверждённым Auth authorization defect.

Никакая policy не переписывается:403 означает refusal, не access success; публичный Gateway contract401 остаётся неизменным. Generic403 на wrong-password confirmation по-прежнему недостаточен: принята только ранее проверенная explicit CURRENT_PASSWORD_INVALID после successful204 и Redis0→0→1.

## Как принять action без нового запроса

Для ограниченного MAP criterion «real persisted Auth current-password negative» core actual evidence достаточна: positive204 исключает ошибку issuer/config/authority, specific wrong403 доказывает password rejection, own counter1/unblocked доказывает ожидаемый server-side budget effect. Source reviewed f3 confirmation path действительно проверяет current account BCrypt credential и live ADMIN permissions. Требование не требует ровно401 на отдельном anonymous direct guest GET.

После independent own-PG post-read можно оформить **отдельную qualified local acceptance**, связав native RESULT hash, этот report и post-read hash. Post-read должен подтвердить ровно одну новую normal own session, теперь revoked; baseline sessions сохранены; catalog12/current authority/credentials/grants/profile/map/semester и critical/main/clock projections не изменились кроме ожидаемого failure counter. Тогда logout204 + actual DB revocation закрывают session cleanup без повторного использования credentials/token. Anonymous403 сам по себе доказывает только anonymous denial, не token revocation; controller logout response сам по себе не заменяет DB witness.

На момент записи отчёта Root сообщил, что post-read выполняется; его новых bytes/path/hash reviewer не получил и не принимал. Этот report не выдаёт незавершённый postguard за PASS. Нельзя менять исходный nativeExit1/passedfalse на0/true, удалять failureCode или переиздавать raw receipt как successful. Не нужны новый login, guest request, confirmation, deletion, counter reset, browser/Gateway retry или fresh helper attempt только ради формата отчёта. Actual MAP UI/PG evidence остаётся ранее выполненной отдельной evidence; здесь не утверждается повтор MAP.

## Пределы и STOP

Только прочитаны public source Git blobs и allowlisted existing RESULT; SHA вычислен локально. Никаких HTTP/API/DB/Redis/Docker/browser/compile/whole-auth-suite/runtime/private-values/secret/context операций не выполнялось. Artifact/source runtime attribution и reviewed PREP guards переиспользованы, не переисполнялись. Отдельные отсутствующие Attendance/downstream/global-health возможности не приняты. Единственная запись — этот immutable qualification report. STOP; процессов и ресурсов reviewer нет.
