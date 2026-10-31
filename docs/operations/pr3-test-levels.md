# PR3: обычная регрессия и операционная приёмка

Обычная обратная связь запускается из корня репозитория на Linux:

```bash
python3 scripts/lifecycle_regression.py unit --out /absolute/new-private-unit-output
python3 scripts/lifecycle_regression.py service-it --out /absolute/new-private-it-output
```

Каталоги вывода должны отсутствовать. Unit выбирает ровно два существующих метода
`SemesterServiceTest`: явный учебный год при создании/изменении семестра и московские
даты без замены security clock. Docker для этого входа не требуется.

Service IT выбирает ровно 14 существующих методов в академическом, расписании и
посещаемости: запрет изменения используемых дат; stale/replayed promotion;
удаление предмета с durable receipts и rollback outbox; pending/resume и
конфликтующий assignment; archive transitions/recovery receipts; точный replay
operation/manifest; московская граница домашних заданий; mixed/sole EXCUSE и
атомарность outbox; drain задержанного transfer перед archive seal.

IT использует изолированные Testcontainers: PostgreSQL 16, Redis 7 и MongoDB 7.
RPC/Rabbit и некоторые удалённые участники заменены fixture mocks. Старые БД,
очереди, порты и retained cohorts не являются входом этой команды. Один процесс
Gradle и один worker удерживают локальную lease; перед IT проверяются Docker,
доступные 4 GiB памяти, 8 GiB диска и два CPU. Это консервативный входной порог,
а не гарантия отсутствия OOM; отказ содержит наблюдавшиеся числа. Ryuk обязателен;
fixture код задаёт собственные DB bindings и не включает reuse. Во время команды не запускай
другую тяжёлую сборку/стенд.

Каждый Test task получает `--rerun`, чтобы XML был новым; зависимости не получают
`--rerun-tasks`. Нужен Gradle >=7.6; wrapper проверяется перед запуском.
Семантика флага описана в [Gradle 7.6](https://docs.gradle.org/7.6/release-notes.html)
и [command line reference](https://docs.gradle.org/current/userguide/command_line_interface.html).
`--continue` позволяет независимым задачам сохранить диагностику после отказа
одного сервиса. Код 0 требует native exit 0, точные выбранные class/method,
свежий XML, отсутствие skips, дубликатов и missing tests. Пропущенный Docker IT
не считается PASS. Логи Gradle остаются в private 0600; публичный результат
содержит конечные категории ошибок и только известные Java source frames.
Первая команда может компилировать выбранные Java tests и их зависимости;
она не собирает образы или полный стенд. В начале печатается private log path,
а во время Gradle каждые примерно 30 секунд — конечная phase/elapsed/bound.

Эти 16 методов не доказывают реальный D9 admin reconciliation, outage/restart,
межсервисный archive, браузерные права/историю или application backup/restore.
Результат явно оставляет их NOT_RUN. Успешный unit или IT возвращает 0 для
выбранного уровня независимо от этих незапущенных требований.

Операционная цепочка сохраняется в `scripts/local_e2e_async.py special13` и
требует явный `--operational`, включая nonclock renderer outage. Для локального
ограниченного прогона разрешён dependency prefix:

```text
special13 --operational --lifecycle-only --operational-until <story>
```

Порядок: semester-metadata → subject-deletion → assignment → today →
semester-reconciliation → application-recovery → semester-archive. Выполняется
весь prefix с его предпосылками. Незатронутые stories остаются NOT_RUN; успех
prefix не объявляется успехом всех семи. Существующие admission/retained receipts,
disposable/ownership/network guards, бюджеты и обязательное восстановление после
каждой фактической операции сохраняются. Это отдельная тяжёлая операция, которую
следует запускать только после проверки текущего retained состояния; данный
пакет не запускает её автоматически и не выдаёт команду старта старого стенда.

Реальный CI имеет отдельную lifecycle matrix с D9, recovery и archive. Обычный
Playwright config исключает fixtures, но это не исключает их из всего CI.
Этот change не удаляет CI jobs и mandatory gates. Перенос нового быстрого входа
в CI возможен отдельно; зелёный unit/IT не позволяет скрыть отказ lifecycle.

Последний `rct-local-stage17-247d995b7f41` был новым изолированным namespace;
build/BOOT прошли, первые четыре stories прошли, D9 дал OTHER + TIMEOUT,
restorationSucceeded не подтверждено, recovery/archive не выполнены. Это не
доказывает дефект образа или точную ошибку последовательности. Plain attendance
start имеет cap 5000 ms и раньше не распознавался matcher; это кандидат для
проверки, а не установленная историческая команда. Новый diagnostic сохраняет
конечную категорию команды, timeout/exit, фактический timeout, elapsed,
phase/cleanup без argv/env/output. Бюджеты не увеличены. Явный DOCKER_HOST
передаётся в admitted local children; прежняя потеря этого binding была дефектом
согласованности окружения, но не доказанной причиной позднего timeout.

Read-only snapshot из delivery package читает только известные saved receipts и
выбранные inspect metadata retained247d. Он не стартует/останавливает/exec ресурсы,
не читает Config.Env, health output или ключи. Старые образы остаются связанными
с исходным d689, даже после нового commit. Неизвестный snapshot не блокирует unit
и не означает безопасное восстановление или разрешение удалить retained данные.
