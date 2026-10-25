# Адресная очистка PR3 — 25 октября2026, время Москвы

Разрешение владельца: удалить только собственные неиспользуемые производные
сборки/кэши и доказанные лишние копии. До удаления выполнены broad `du`, Docker
system/buildcache/image/container/mount inventory, проверка процессов и независимый
review точного списка. Новая инфраструктура диагностики не создавалась.

| Кандидаты | Точный scope | Учтённые allocated bytes | Почему восстановимы |
|---|---|---:|---|
| Производные Gradle | `/workspace/.gradle/caches/8.12/{generated-gradle-jars,javaCompile,transforms}` |314097664| Gradle8.12/JDK/wrapper/modules-2 сохранены; генерируются снова |
| Промежуточные app сборки | 95 существующих childpaths `classes/generated/extracted-include-protos/extracted-protos/tmp` в19 buildroots, все exactpaths в candidates.json |754868224| source/proto/Gradle tasks сохранены; tracked0, symlink0; libs/resources/results/reports не затронуты |
| Лишние JAR-копии |4 exactpaths в candidates.json|319545344| SHA256 совпал с сохранёнными canonical artifacts; различные inode, mounts/protected overlap0 |
| Собственный npm cache |`/workspace/rct-pr3-tooling/npm-cache/_cacache`|73043968|285content objects SHA-equal основному сохранённому npm cache; штатный `npm cache clean --force --cache <ownpath>` |
| Дубли передачи ZIP |3 exact partpaths +3 exact wrapperpaths в candidates.json|175153152| исходный87568617B ZIP/SHA0f851394/422CRC, extracted и transfer metadata сохранены; части точно равны срезам оригинала |

[Единый полный список](candidates.json) содержит все109 точных путей/размеров/
назначений/способов восстановления. План SHA256:
`56a6c98c4cabad9fa2810d55d53e5d99befa4d268eb31c7d3db75c79385612d5`.
Dockercache402628551B logical имел только828B unshared (ID
`yzq3awjy84qxpggvcpzketn6u`): не очищался. Образы/30контейнеров/RW/volumes/data
сохранены. Системные/platform files неизвестного назначения не кандидаты.

Три пакета удалили98/5/6 exactpaths. [Фактические замеры](summary.json):
available5244747776→6531416064B, net increase1286668288B (~1.20GiB).
Сумма учтённых candidates1636708352B НЕ объявляется освобождённым местом:
overlay и сопутствующие записи дают меньший фактический прирост.

**Порог не достигнут.** Последний замер6530658304B (~6.08GiB), минимум7GiB
(7516192768B), остаточный дефицит985534464B (~0.92GiB), без дополнительного запаса.
Нужно примерно1.5–2GiB дополнительного объёма, сохранив workspace/Dockerdata.
Очистка остановлена: основной npm cache, все node_modules/sharedsymlinks,
Gradle modules/wrapper,25protected JARs,3legacysetup JARs, source/recovery/unique
check results/keys/configs/data сохранены. Ни guards, ни runtime forecast не снижены.
Group runtime/fixturewrites/UIdelete0; это не PASS удаления группы.

[Независимый postproof](post.json): все12WT HEAD/branch/porcelain-z совпали с
immutable source recovery metadata,25protected и4canonical JAR SHA сохранены,
originalhandoff и recoveryZIP00fb SHA/CRC сохранены; initial30IDprefix/name/image/
state совпали,20running/10stopped. Полная machine equality134mounts не заявлена:
immutable mount baseline отсутствует. Safety-review использовал134actualmounts,
22runtimepins, no overlap;11liveJava принадлежали контейнерам, hostGradle отсутствовал.
FD-проверка была частичной из-за ограничений /proc, абсолютный negativeFDproof
не заявлялся. Docker/service/DB/Auth операций в cleanup не выполняли.

CI/Coverage/Security/OpenAPI PAUSED; без main/reset/clean/forcepush/merge/deploy.
