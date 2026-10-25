# Independent fresh canonical-home source review

Вердикт: **CHANGES REQUIRED — один P2 в pending role-home continuation**. Основная duplicate-navigation correction и supported force API корректны; before/after actual UI gate ещё не закрыт.

Дата: 2026-10-24. Frozen original `22972df6373a9ff25d9cae03ccb03c6d24c177f8`, parent `b84cc19e61848cdeba7157699f045f2027d21f7f`, clean author WT `codex/pr3-web-fresh-role-home`. Ровно App.vue, 7 добавлений/2 удаления. Назначение Sol6.1/high; runtime model telemetry недоступна.

## P2 — same-role authority refresh отменяет home guard без новой continuation

**Путь:** `frontends/web-vue/src/App.vue:10`.

Новый watcher зависит только от phase и activeRole. Пока forced role-home ADMIN guard ждёт `/api/academic/admin-access/me`, обычный session refresh может заменить snapshot с изменённым authority identity (например rolesVersion/sessionVersion/readOnly) при тех же `ready` и `ADMIN`. `acceptSession`/`refresh` в session.ts:51–56/70–90 поддерживают такое обновление без промежуточного очищения snapshot или смены phase. Новый watcher не вызывается, поскольку оба наблюдаемых primitive значения прежние.

Existing router.ts намеренно отвергает старый результат: captured transport assertion и `owner !== webSession.identity.value` возвращают false при смене authority. Для pending navigation из current role-home это оставляет current route `/`; новой canonical admission для свежего owner не возникает. RoleLanding при активной роли показывает `Страница недоступна`; его ordinary same-route link тоже не имеет force. Таким образом race воспроизводит stuck role-home после корректного начального force.

**Causal case:** current `/` без выбранной роли → выбрать ADMIN → held current-access GET → принять refreshed ADMIN snapshot с другим identity → завершить старый GET. Старый result безопасно отбрасывается, но route остаётся role-home при `ready/ADMIN`. Это не утечка старой authority; это потеря требуемой current canonical-home continuation.

**Исправление:** учитывать primitive authority identity при текущем role-home и запускать новый guarded canonical admission после её изменения. Сохранить existing stale-response fence и no-op для snapshot refresh с **тем же identity/phase/role**; не force-redirect валидную explicit page из-за произвольного display/profile обновления. Никакой смены grants/chooser/policy для correction не требуется.

## Независимое source-only доказательство

Выполнен один ephemeral in-memory case: frozen App script setup (imports удалены только для injection) с реальными Vue refs/watch и installed Vue Router `createMemoryHistory`; minimal guard моделирует ровно существующий pending ADMIN read + owner-mismatch rejection. После A→B identity turnover при неизменном activeRole результат:

```json
{"frozenAppWatcher":true,"realVueMemoryRouter":true,"modeledExistingOwnerFence":true,"afterSameRoleAuthorityTurnover":"role-home","phase":"ready","role":"ADMIN","browserOrApi":false}
```

Команда: inline `node` из frozen author WT; fs читает только App.vue, TypeScript `transpileModule` выполняется в памяти, `effectScope` остановлен. Нового framework/test file, browser, HTTP/API, app server или backend операции не было. Это causal source/harness evidence pending-navigation race, не actual SUT воспроизведение или DOM/focus runtime proof.

## Что проходит source review

- Installed Vue Router4 public `RouteLocationOptions.force?: boolean` существует. В `replace` поле сохраняется; `pushWithRedirect` читает to.force и пропускает duplicate suppression только при true. Изменённый source применяет force строго когда current name role-home. Без этой опции действительно можно пропустить guards для повторного `/`.
- Native multi-source watch сравнивает primitive phase/role и не вызывает callback при их равенстве; это устраняет прежние callbacks на каждом новом snapshot array. Однако authority-turnover case выше требует отдельной primitive continuation.
- Watcher не зависит от route, поэтому сам redirect не запускает watcher-loop. Query/hash, guest/password-recovery exception, lastRole reset, router/chooser/permissions/grants/internal-return validation остаются unchanged. Current ADM getMe owner fence и fallback policy не ослаблены.
- `git diff --check` exit0, frozen inventory/HEAD/parent/clean status совпали. Авторские existing4 role-home behavior tests, scoped types/App lint PASS приняты как переданная evidence, reviewer их не повторял. Они не проверяют pending same-role authority turnover.

## Runtime limits

Прочитан `/workspace/rct-pr3-runtime/FRESH-ROLE-HOME-22972df63.md`. Actual before URL/DOM failure после login200/ADMIN PUT200 — квалифицированная evidence. Неверный before observer path `/api/v1/admin-access/me` не доказывает отсутствие настоящего `/api/academic/admin-access/me`; meReads0 из него не принимается как authoritative. Отдельные root observations не выдаются за собственные запуски reviewer.

Actual after fresh UI14250/root7, browser focus preservation, current-policy fallback и action acceptance остаются pending после correction/integration. Reviewer не повторял build/browser/API/backend/CI и не менял продуктовые файлы, permissions, users или stand. Единственная запись — этот внешний immutable report.
