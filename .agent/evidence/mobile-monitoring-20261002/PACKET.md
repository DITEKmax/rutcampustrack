# Пакет Ж — mobile-bff monitoring

- Goal: эксплуатация получает существующий ServiceDown для недоступного mobile-bff.
- Context/evidence: RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA; main 50f6eabb; assigned WT HEAD 83f3472f1b3e17672fe352f880683d00203af61d. Target отсутствовал. Frozen BFF artifact 2948e60e не содержит Prometheus registry (см. artifact.log).
- Relevant scope: sole writer `/root/zh_mobile_monitoring_1002`, assigned worktree `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/pwa-install-delivery-20260923`; infra/prometheus/prometheus.yml, docs/operations/monitoring/alerts.md; root расширил scope ровно на services/mobile-bff/mobile-bff-app/build.gradle.kts для registry. Evidence только в этом каталоге. Risk S1. Main интегратор root.
- Required behavior: job mobile-bff, внутренний mobile-bff:9080/actuator/prometheus, registry в runtimeClasspath, существующее up == 0 / for:1m. Metrics не публикуются наружу.
- Constraints: repository preparation, no deploy/push/containers/build без lease; no secrets; auth/compose/backend code не менять; сохранить чужие dirty RULES/LEAF-PACKET и несвязанные файлы.
- Existing patterns: соседние Spring scrape jobs; runtimeOnly("io.micrometer:micrometer-registry-prometheus") в document-renderer/auth/academic/attendance/gateway/schedule/notification; private_net и expose:9080 в compose; MobileIdentityFilter обрабатывает только student/map/teacher API.
- Acceptance criteria: отсутствующий job устранён; YAML разбирается, уникальные jobs, host/port/path согласованы с compose/application; generic ServiceDown включает BFF; runtime registry dependency добавлена; docs точны; scope diff чистый.
- Verification: лёгкий Python/PyYAML config/source check, git diff --check, read-only осмотр frozen JAR. promtool отсутствует; Docker/Gradle lease не выдан. HTTP scrape/alert runtime — NOT RUN, обязательный остаток перед deploy.
- Do not: никаких детей/Terra/redesign/секретов/.env.prod/чужих правок/public metrics/security changes/full merge; root принимает evidence и интегрирует inventory.

## Baseline

Назначенные файлы чистые и byte-identical main50f6eabb, перенос не нужен:

| File | Git blob WT/main |
|---|---|
| infra/prometheus/prometheus.yml | acdd52b08c982ebea9ad8c886f27ef58d8f6758c |
| docs/operations/monitoring/alerts.md | 8deb6487cd6ca152c0b716c5194b814635d43ef8 |
| services/mobile-bff/mobile-bff-app/build.gradle.kts | e66a657b90ac413cbe2a38014a2cfc07abf2487f |

Входящий tracked dirty: .agent/orchestration-v2/LEAF-PACKET.md и RULES.md. Incoming untracked: recovery-zh1002-static/, teacher-replacement-ui-20260923/source.diff, zh-no-files-static/. Git/ripgrep предупредили о недоступных ignore/recovery dirs; это не продуктовая ошибка, код из-за них не менялся.

Root scope delta 2026-10-02: «Расширяю sole-writer scope ровно на services/mobile-bff/mobile-bff-app/build.gradle.kts для runtimeOnly micrometer-registry-prometheus … Frozenruntime2948 не менять, heavy Gradle после стенда». Никакого собственного contract redesign.
