# Независимое review authority reconciliation — PASS

Revision: `2d3398326dd3ae8892a7a8d8921f32e88eb57f13`, worktree `/workspace/rct-worktrees/admin-authority-reconcile`, branch `codex/pr3-admin-authority-reconcile`. Проверены все три изменённых файла: `frontends/web-vue/src/auth/session.ts`, `frontends/web-vue/src/app/live.ts`, новый `frontends/web-vue/src/app/admin-authority-reconcile.test.ts`; связанные существующие transport, Auth client, App/AppShell, ADMIN permission gate и серверный session/version contract прочитаны адресно.

**Подтверждённых P1/P2 findings нет.** Source-пакет пригоден для интеграции в согласованную PR3-базу. Это source review, не новая actual/browser/STOMP приёмка.

## Проверенные границы

- `live.ts:72–102`: один последовательный таймер; новый poll назначается после завершения предыдущего. Запуск допускается только для ready/visible ADMIN. Hidden, guest/error и смена роли снимают ожидающий таймер. Уже начатая проверка имеет собственный deadline; её завершение заново оценивает текущую роль/phase/visibility. `busy` не допускает authority read во время пользовательской session mutation.
- `session.ts:121–149`: существующий GET `/api/auth/session` использует `cache: no-store` и общий пятисекундный deadline на probe и требуемый refresh. Ошибка чтения/timeout удаляет snapshot и повышает generation: App удаляет защищённый DOM, realtime теряет прежнего owner. Восстановление идёт обычной кнопкой session retry/bootstrap, без повтора business mutation.
- `session.ts:43–60`: одинаковые session/roles versions, role и readOnly сохраняют generation/identity. Новая snapshot-ссылка сама не меняет ключ RouterView и не вызывает domain invalidation. Изменённая authority повышает generation и использует существующее retirement/remount и повторное получение ADMIN permissions. Серверный identity operation повышает rolesVersion и при изменении LIMITED profile; Auth session проверяет её соответствие bearer, поэтому обычный stale bearer требует штатного refresh.
- `session.ts:63–118,138–139`: HTTP401 проходит через общий refresh flight. Deadline подписывается на конкретный captured AbortController; ожидающий probe ограничивает и уже начатый shared refresh. Refresh принимает token/snapshot/error только при прежней operation. После obsolete flight текущий caller может начать собственный refresh; сменившийся caller этого не делает. Поздний abort старого poll не обращается к новому глобальному controller.
- `session.ts:123–137`: первоначальный probe дополнительно ограждён operation и generation. Logout, invalidate, selectRole и новый login меняют operation; поздние 200/401/network callbacks старого probe не принимают snapshot, не запускают refresh и не очищают successor session. После передачи в refresh действуют его operation fences. Изменение generation другим подтверждённым snapshot также отвергает старый первоначальный read.

## Значимость существующих проверок

Новый suite монтирует настоящие App, AppShell и GroupsPage через Vue renderer и memory router. Он проверяет исчезновение cached table/toolbar и старого input, retained Teacher после ADMIN revoke, сохранение того же input/focus/черновика и отсутствие дополнительных Groups reads при неизменной authority, включая штатный401 refresh. Отдельные сценарии проверяют hidden/visible, timeout/error/retry, bounded shared refresh и поздние 200/401/network outcomes после successor login. Transport/realtime заменены doubles; genuine Notification/STOMP этим suite не квалифицируется.

Переиспользованы результаты автора на неизменённом пакете; reviewer проверки не перезапускал:

- Cwd `frontends`: `node node_modules/vitest/vitest.mjs run web-vue/src/app/admin-authority-reconcile.test.ts web-vue/src/auth/session-races.test.ts web-vue/src/app/api-ownership.test.ts web-vue/src/app/live.test.ts --maxWorkers=1` — 20/20, четыре файла, 2.45s. Лог: `/workspace/rct-worktrees/admin-authority-reconcile/.agent/evidence/admin-authority-reconcile/unit-r2.log`.
- Cwd `frontends`: `node node_modules/vue-tsc/bin/vue-tsc.js -p web-vue/tsconfig.json --noEmit` — автор сообщил exit0; сохранён пустой `typecheck-r1.log` в той же evidence-директории.
- Cwd `frontends/web-vue`: `node ../node_modules/eslint/bin/eslint.js src/auth/session.ts src/app/live.ts src/app/admin-authority-reconcile.test.ts --max-warnings=0` — автор сообщил exit0; сохранён пустой `lint-r1.log`.
- `git diff --check` — автор сообщил exit0, отдельный лог не сохранён. Нового запуска нет.

## Ограничения и выполненные действия reviewer

Ранее actual LIMITED v2 Users200/Groups403 → Groups200/rendered → revoke204/old ADMIN denied остаются принятыми фактами. **Actual open-idle DOM withdrawal FAIL остаётся FAIL**: новый source ещё не проверен этим runtime сценарием. ENV gap isolated Notification и genuine STOMP/reconnect qualification остаются отдельными по `QUALIFY-actual-v2-withdrawal.md`; данный fallback их не закрывает и не требует подменять канал fake STOMP.

Прочитаны AGENTS, применимые frontends/tests инструкции, CURRENT/SLOTS и RULES с SHA256 `2b9a859cc86e056ee4298170d90a73550ca8a60573dab8289579a9075ede6b38`. Spawn-request модель/effort — `gpt-6.1-sol/high`; served-model telemetry отсутствует и не заявляется подтверждённой. Reviewer выполнил только source/public-evidence/Git reads и создал этот отчёт. Tests/build/lint/runtime/API/browser/DB/private reads, product changes, CI/main/merge/deploy и дочерние агенты: 0.
