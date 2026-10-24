# Narrow auth-adapter correction recheck

Вердикт: **PASS — оба P2 из REVIEW-0d30f35ce.md закрыты по source; новых подтверждённых P1/P2 нет**. Это не browser/runtime acceptance.

Дата: 2026-10-24. Frozen correction: `6ff8874ad1417691c63e9c303c100ce87abc4721`, parent `0d30f35ce7be782f0dee4ab3783938e9b6e824ca`. Ровно один изменённый файл: `tests/e2e/fixtures/auth.ts`, 19 добавлений/5 удалений. Предыдущий immutable report не переписывался; unchanged route/axe/control files и полный auth scope не пересматривались. Назначение — Sol6.1/high independent recheck; runtime model telemetry недоступна.

## P2-1 CLOSED — associated password label

Строки6/24/67 используют один anchored regex `^Пароль\s+\*(?:\s+(?:Показать|Скрыть))?$` и сохраняют `getByLabel`, fill и empty-password postcondition. Он соответствует normalized associated label текущего Vue LoginPage в обоих состояниях toggle, сохраняя привязку к password field. Начало/конец и разрешённые suffixes исключают совпадение с новым/чужим password field или произвольным дополнительным текстом.

Независимая pure проверка regex, извлечённого из frozen source: принимает `Пароль * Показать`, `Пароль * Скрыть`, `Пароль *`; отклоняет `Новый пароль * Показать`, `Пароль * Показать другую кнопку`, `Логин *` — PASS. Выполнено через `git show ... | node` в памяти, без импорта fixture/Playwright/browser и без записи test files. Повтор прежнего source-function harness не потребовался: associated-label поведение уже установлено в первом review, изменился только matcher.

## P2-2 CLOSED — responsive visible role carrier

Строки33–44 теперь требуют видимый header, используют desktop carrier только при его видимости, иначе открывают существующую кнопку `Разделы` при collapsed state и требуют видимый `.web-nav-role` внутри current named navigation. Это совпадает с AppShell/RoleSwitcher и compact PCSS; helper не ищет hidden header role controls как единственный источник grants.

После выбора роли строки50–54 заново вызывают `roleCarrier()` и получают current requested button для pressed assertion. Адресно проверено `webSession.selectRole`: он синхронно очищает snapshot до сетевого await; App.vue на этом состоянии удаляет старый RouterView/shell и показывает session-loading main. Следовательно ожидание видимого header в повторном carrier lookup ожидает восстановленный shell, после чего при необходимости раскрывает его новую mobile navigation. Locator сохраняет публичный selector и не удерживает ElementHandle старого shell.

Enabled/selectable constraint перед сменой, exact requested-role/optional read-only label, `aria-pressed=true`, current single-role label, same-origin login200 и exact canonical query/hash-free landing сохранены. Ни force-click, ни `isHeadman` inference, ни синтетический grant не добавлены. Уже активную read-only/nonselectable роль helper по-прежнему не пытается переключать.

## Проверки и пределы

Прочитаны frozen one-file diff, relevant current AppShell/RoleSwitcher/App/session source и `/workspace/rct-pr3-runtime/VUE-AUTH-ADAPTER-RECHECK-6ff8874ad.md`. `git diff --check 6ff8874ad^ 6ff8874ad` — exit0; HEAD/parent совпали с packet, worktree clean во время проверки. Авторский scoped strict TypeScript exit0 принят как переданная evidence и не повторялся reviewer.

Unchanged finite control и его positive10/rejected22 не повторялись. Browser/login/role PUT/logout/backend/DB/build/CI не запускались; actual compact/desktop UI proof не заявляется. Users/spec migration и unsupported legacy route controls остаются pending прежнего отдельного packet. Продуктовые исходники и процессы не менялись; единственная запись — этот внешний immutable recheck report.
