FAIL — два MEDIUM finding, HIGH/CRITICAL нет.

1. **MEDIUM — скрытый успешный retry не отражается в H85 evidence.**  
   **File:line:** [probe.mjs](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/probe.mjs:828), [runner.ps1](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/runner.ps1:2918).  
   **Evidence:** первая create-операция при transport exception автоматически повторяется на строках 830–833. Маркер `retriedAfterAmbiguousTransport` остаётся свойством внутреннего response и отсутствует в возвращаемом I1 payload на строках 868–885. Runner переносит в отчёт только выбранные I1-поля; `h85-report.json` не содержит retry/ambiguous marker. Хотя probe frozen и не входит в новый diff, packet прямо требует «no hidden successful retry», поэтому это критичная acceptance-зависимость.  
   **Impact:** H85 `I1 PASS` не доказывает, что первый create завершился без transport failure. Второй запрос с тем же idempotency key может скрыть сбой первого прохода или нестабильность transport.  
   **Reproduction:** вызвать однократный client-side transport abort после отправки первого create; второй запрос успешно вернёт результат, а итоговый report останется `PASS` без признака retry.

   **Repair contract:**  
   **Defect:** неразличимы чистый первый проход и успех после скрытого retry.  
   **Correction:** либо исключить автоматический retry из acceptance-run, либо вернуть typed retry marker из probe и сделать его явным fail/non-clean result в runner.  
   **Scope:** только `probe.mjs` и отображение I1 evidence в `runner.ps1`; не менять продуктовый idempotency contract.  
   **Verification:** fault injection первого transport attempt плюс чистый реальный прогон; отчёт обязан различать случаи. После правки требуется независимая recheck.

2. **MEDIUM — H68 diagnostic helper перезаписывает evidence вопреки no-overwrite contract.**  
   **File:line:** [h68-probe-check.ps1](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/h68-probe-diagnosis/h68-probe-check.ps1:18), [h68-probe-check.ps1](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/h68-probe-diagnosis/h68-probe-check.ps1:429).  
   **Evidence:** output закреплён за постоянным `h68-probe-correction-evidence.json`, затем записывается через `WriteAllText` без проверки существования. В том же frozen batch H82 helper уже использует timestamp+GUID и отказ от overwrite. Packet требует unique paths/refuses overwrite.  
   **Impact:** повтор helper уничтожает предыдущий PASS/FAIL и его source attribution; это повторяет уже зафиксированный класс evidence-loss incident и делает последовательные результаты неаудируемыми.  
   **Reproduction:** запустить helper дважды — второй запуск заменит тот же файл.

   **Repair contract:**  
   **Defect:** evidence path не уникален и допускает overwrite.  
   **Correction:** timestamp+GUID/run-id path и явный отказ при занятом пути; существующий artifact сохранить неизменным.  
   **Scope:** только H68 helper и его output metadata.  
   **Verification:** два последовательных targeted запуска создают два разных файла, hash первого не меняется; затем независимая recheck.

Подтверждённые части: RULES SHA совпал; freeze SHA `3F583A…` совпал; все пять frozen source hashes совпадают; H85 выполнен на clean revision `426a15…`, exit 0, I1/I2/cleanup PASS; настоящие Gateway/BFF counters `2→2`; loopback vendor `+json` прошёл как strict UTF-8 `System.Byte[]`; dedicated Gateway route, V17 immutable identity и Attendance group eligibility подтверждены оригиналами.

Новые tests/runtime не запускались. Review ограничен Requests final batch и не означает product readiness или проверку более новой union. Read-only review slot **RELEASED**.
