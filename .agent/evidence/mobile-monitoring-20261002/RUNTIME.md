# BFF metrics runtime — PASS 2026-10-02

Реальный отдельный BFF JAR на loopback вернул GET `http://127.0.0.1:49771/actuator/prometheus` → **200**, `text/plain;version=0.0.4;charset=utf-8`, Prometheus HELP/TYPE и samples `jvm_memory_used_bytes`, `process_uptime_seconds`, `process_cpu_usage`. Endpoint вызван без auth headers; существующая identity policy не изменялась. Revision **3db2cc02f35801176ce0bdc15b55319cfc962090**, JAR SHA **b0e401c2bbbe9d573855364bb6296473c2abb383ab0a76d6cc132adc1b7ede3b**. Полный body — `r2/runtime-metrics.txt`, response/process evidence — `r2/runtime-result.json`.

Root выдал один heavy lease после cleanup общего стенда. Работа только в author worktree; main/frozen runtime2948 не менялись. JDK `C:/Users/maksd/.jdks/ms-21.0.10`, Gradle8.12, PowerShell7.6.5, Python3.12.10. Все starts/stops только своих процессов. Lease освобождён после exact cleanup.

## Запуски, команды, exit codes

Одна build-команда во всех трёх запусках (JAVA_HOME — указанный JDK21):
`./gradlew.bat :services:mobile-bff:mobile-bff-app:bootJar --max-workers=1 --no-parallel --system-prop=org.gradle.java.compile-classpath-packaging=true --no-problems-report --no-daemon`.

| Run | Revision | Handle | Result | Evidence |
|---|---|---|---|---|
| Build R1 sandbox | 33b72599 | 83786 terminal | exit1, 2m14s; shared JAR AccessDeniedException | build-r1.log |
| Build R2 scoped escalation | 33b72599 | 18925 terminal | exit0, 1m18s | build-r2.log |
| Smoke R1 | 33b72599 | 7443 terminal | exit1; no startup port due missing shared logging resource, bounded90s cleanup | runtime-command.log, runtime-startup.log, runtime-result.json |
| Build R3 scoped escalation | 3db2cc02 | 8305 terminal | exit0, 49s; new shared-logback dependency | build-r3.log |
| Smoke R2 | 3db2cc02 | 46426 terminal | exit0, 15s; actual200/metrics | runtime-r2-command.log, r2/runtime-result.json, r2/runtime-startup.log, r2/runtime-metrics.txt |
| Exact R2 cleanup | 3db2cc02 | short command | exit0; own PID/listener absent | r2/cleanup.json |

Smoke commands: `python .agent/evidence/mobile-monitoring-20261002/runtime-smoke.py` (R1), then same command with `r2` evidence label (R2). One-off evidence driver saved in runtime-smoke.py, no product test suite. Child Java `-Xms64m -Xmx256m`, bind127.0.0.1/server.port=0, no prod profile, minimal child env, synthetic GRPC_SECRET, all auth/gRPC downstream addresses loopback, HTTP proxy disabled. Exact Java arguments saved in each result JSON. No auth server/helper/mock/containers/other builds.

Повторы только по новому evidence: R2 build меняет sandbox execution после воспроизведённого AccessDeniedException (ACL/code не менялись); R3 build и R2 smoke — после root-authorized product correction. Прежние source/config lightPASS не повторялись.

## Подтверждённый packaging defect и correction

R1 runtime WARN: logback-spring.xml включает `shared/logback-base.xml`, JAR `f2e48c9c42be66a0b4b28acb00c3750b02a5244cb103219b6a5262026995fad8` не содержит shared-logback library или base resource (read-only zip inspection exit0). Нет appenders и startup порт не наблюдаем. Связь с запросом: невозможно подтвердить доступность реального BFF metrics endpoint выбранным bounded smoke, BFF runtime лишён штатного логирования.

Root открыл main source и расширил тот же buildfile ownership ровно на зависимость shared-logback по соседнему pattern. Product commit **3db2cc02**, modified единственный файл `services/mobile-bff/mobile-bff-app/build.gradle.kts`, exact diff **+1/-0**:
`implementation(project(":services:shared:shared-logback"))`.
Scoped product diff-check exit0. Logging override не применялся. R2 startup содержит штатные BFF/Tomcat сообщения; metrics проверены реально. Product Java/security/compose/frontend не менялись.

## Cleanup и ограничения

R1 own PID40788: subprocess reaped/poll1 и независимый Get-Process подтвердил отсутствие (exit0). R2 own PID35356: finally terminate/wait завершил процесс, poll1/reaped=true; затем Get-Process PID35356 отсутствует и Get-NetTCPConnection Listen port49771 отсутствует (r2/cleanup.json). Exit1 остановленного Java процесса — ожидаемый terminate result, smoke exit0. Reserved loopback auth/gRPC socket закрыт контекстом Python. Больше своих runtime ресурсов нет.

Известный startup ERROR PublicKeyProvider: auth-key request к собственному bound/not-listening loopback port отказан, как предусмотрено безопасным local plan. Этот smoke проверяет metrics, не auth API; ошибки внешней сети/секретов нет, код из-за этого не изменён.

Prometheus service/scrape target in Docker, promtool schema validation, alert evaluation/ServiceDown firing, Alertmanager/provider delivery и deploy этим smoke **не проверены**. Ранее YAML/source consistency PASS сохраняется. Достаточный локальный критерий BFF endpoint закрыт; actual deploy остаётся отдельным разрешением.

## Дополнительный evidence inventory

Created в текущем evidence directory: RUNTIME.md, runtime-checks.json, runtime-smoke.py, build-r1.log, build-r2.log, build-r3.log, runtime-command.log, runtime-startup.log, runtime-result.json, runtime-r2-command.log; подкаталог r2/: runtime-result.json, runtime-startup.log, runtime-metrics.txt, cleanup.json. Modified SUMMARY.md: current runtime outcome и correction. Deleted none. Исходные evidence/логи сохранены, чужие dirty RULES/LEAF-PACKET не тронуты.
