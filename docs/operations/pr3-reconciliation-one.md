# Один свежий запуск D9

Scope: локальный operational D9 `semester-reconciliation`, риск S3. Команда запускает свежий изолированный обычный стек17, canonical lifecycle prerequisites, прежний `advance_to_next_teaching_day` и оригинальную D9 историю. Данные D9 создаёт собственный `MobileDataset`. Все календарные, permission, envelope, replay, duplicate и restoration assertions остаются.

Новый `--reconciliation-only` допустим только вместе с `special13 --operational --lifecycle-only`. Он несовместим с `--operational-until` и четырьмя старыми closure flags. Discovery выполняет только D9; в логическом результате семь строк, шесть `NOT_RUN`, selected1 и `fullLifecycleAcceptance=false`. Успех этой команды не означает прохождение всех lifecycle7.

Только при этом opt-in старые контейнеры с `rct.local.stage17` допускаются по выбранным metadata, если каждый строго `exited`, без running/paused/restarting. Исторические файлы, credentials, env, базы, очереди и volumes не читаются. Snapshot снимается под fixed-port lease; перед BOOT членство и состояния сверяются повторно. Смена ID/состояния запрещает BOOT. Старые ресурсы не изменяются; cleanup сохраняет прежнюю проверку точного нового nonce/project/ID, unsafe restoration удерживает собственные ресурсы.

Final DLQ observation делает только GET очереди `attendance-service.events.dlq` и требует прежние ready1/unacked0. Повторяются только валидные несовпадающие счётчики. HTTP/transport/body/JSON/shape/bound ошибки не повторяются. Общий observation deadline не превышает10s, ограничен прежним `end−5s`; один request deadline≤4s действует на заголовки и всё тело. Последние5s остаются disposal. Статистика RabbitMQ обновляется периодически; это источник хрупкости single snapshot, а не доказанная причина исторического FAIL.

Новый `reconciliation-dlq.safe.json` содержит только первые/последние классы счётчиков, finite reason, HTTP status, elapsed/bound и cleanup stage. Native sidecar различает `RABBITMQ_INSPECT` и `ACADEMIC_INSPECT`. v11 schema/key set и4096bound сохранены; исходная MAIN ошибка по-прежнему выше cleanup ошибки.

После независимого review владелец выполняет один foreground entry в native WSL Ubuntu, из существующего toolchain/activated local stand. Public package: `pr3-remaining13/reconciliation-one-v1`; clean owner branch `codex/pr3-ui-batch`, exact base `bccf261c9953fd5612d8295d86eced1bcc4559ea`. Entry сначала проверяет manifest, native Bash syntax, base/source hashes и отсутствие новых путей, применяет exact patch, выполняет ограниченные source controls и normal commit без bypass hooks. Затем выполняет ровно одну D9 команду с существующим bundled frontend flag.

```bash
bash --noprofile --norc /mnt/c/Users/maksd/.codex/visualizations/2026/10/11/01a12bea-f8eb-7c43-afc1-242d08755c13/pr3-remaining13/reconciliation-one-v1/owner-commands.sh
```

Private per-command logs остаются в новом каталоге `$HOME/.local/share/rct-pr3-local/checks/reconciliation-one-*`. EXIT автоматически собирает bounded public `pr3-reconciliation-one-owner-v1/fresh-d9.safe.json` из одного typed `PREPARED` и точно связанного нового owner manifest. Collection выполняется при PASS/FAIL/cancel; native runner и tee exits сохраняются раздельно. При ошибке до PREPARED public result остаётся partial и не сканирует историю. Нет автоматического rerun или следующих тестов после Ctrl+C.

Не повторяются уже принятые unit2/IT14, browser36, nonclock6 и первые четыре lifecycle stories. Полный и prefix режимы и CI selection сохранены. Actual D9, normal hooks и owner Bash остаются `NOT_RUN` до выполнения владельцем. Production readiness не меняется.
