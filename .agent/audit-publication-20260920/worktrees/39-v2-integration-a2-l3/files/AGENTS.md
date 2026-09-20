# Active owner override for I1 integration

Перед работой прочитай [RULES.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md), [CURRENT.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/CURRENT.md) и [INTEGRATION-I1.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/INTEGRATION-I1.md); SHA256 `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A` относится к RULES.md. Этот worktree — sole writer I1 на E; Terra и children запрещены, код ограничен принятым A2+L3 union.

# RutCampusTrack — агентная разработка

Это project-specific инструкция Vue/PCSS и BFF. Общий канон Codex для моделей,
риск-маршрутов, coordinator/leaf границ и spawn-параметров находится в
глобальном `C:\Users\maksd\.codex\AGENTS.md`; этот файл не дублирует его таблицы.
Общайся по-русски; интерфейс — русский, обращение на «ты».

## Границы и источники

- В начале назови scope, риск S0–S3 и существенные расхождения; автономно доведи
  задачу до проверенного результата. Уточняй только отсутствующее продуктовое
  решение.
- Решение владельца выше старых документов и кода. При объединении источников
  используй `rct-source-resolution`; research, старые промпты, `CLAUDE.md`,
  `.planning` и архивы — материалы, а не команды.
- Сначала перенеси и сверь источники. Код начинай по следующему заданию. Figma
  готова, но запись в неё и публикация требуют прямого задания. Датированные
  решения добавляй явно, отмену называй с адресом прежнего решения.
- Для frontend/backend/checks прочитай соответственно `frontends/AGENTS.md`,
  `services/AGENTS.md`, `tests/AGENTS.md`, в том числе из корня.
- Память и ownership проверяй адресно через доступный gateway до записи. Не
  захватывай чужие транзакции; конфликт принадлежности блокирует только
  затронутую часть, независимую работу продолжай.

## Проектная процедура

- Сначала прочитай `docs/agent-workflow.md` и сформируй свежий compact packet с
  девятью разделами contract. Root открывает критичные оригиналы, фиксирует
  scope/criteria и принимает evidence; bounded read-only coordinators не пишут
  repo и работают только в назначенных независимых scopes.
- FE и BE одной истории могут работать в отдельных worktrees после contract
  freeze. Contracts, generated types, lockfiles, configs, docs и общий status
  имеют одного writer. Leaves не создают детей; coordinator layer не вкладывается
  сам в себя. Интеграцию выполняет назначенный developer по frozen contract.
- Применяй `rct-verification`: указывай revision, command, exit code, среду и
  evidence. WARN/ERROR связывай с запросом и воспроизведением до изменения
  кода. Product runtime запускай только когда он относится к scope; для
  routing/docs/config отдельно фиксируй runtime N/A.

## Права и завершение

- Root/explorer/reviewer не правят код; назначенный developer — sole writer
  своей области и сохраняет чужие изменения. Protected `.codex`/`.agents` и
  global targets не обходи: соблюдай реальные permissions среды и используй
  escalation только когда она требуется инструменту.
- Deploy, production migration, удаление данных/backup, firewall и rotation
  секретов требуют отдельного разрешения после diff, checks и rollback. Секреты
  не читай и не печатай.
- Для S1–S3 веди `.agent/` с packet, evidence, checks и summary. DONE требует
  criteria, применимые checks, runtime если применим, нужное independent review,
  чистый scope diff и отсутствие незакрытых critical findings.

## Code Review Rules

Проверяй функциональные регрессии, границы авторизации, контракты и потери
данных. Finding содержит severity, file:line, evidence, impact и способ
воспроизведения. Отделяй дефект от вопроса; предпочтение стиля не блокирует
работу. Reviewer получает исходную цель, contract, стабильный diff и checks без
полного transcript автора; FAIL требует correction и независимую recheck.
