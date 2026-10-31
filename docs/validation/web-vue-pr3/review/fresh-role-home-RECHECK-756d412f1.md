# Independent canonical-home authority continuation recheck

Вердикт: **PASS — единственный P2 REVIEW-22972df63.md закрыт; новых подтверждённых P1/P2 нет**. Actual fresh UI/ACT-ROLE-SELECT acceptance остаётся pending после integration.

Дата: 2026-10-24. Frozen correction `756d412f1f73d56f274962afe3016fc92a2507b0`, parent `22972df6373a9ff25d9cae03ccb03c6d24c177f8`. Единственное изменение — App.vue:10, третий primitive watch source `webSession.identity`. Author HEAD/parent и clean status совпали. Initial report/probe artifacts сохранены без изменения. Назначение Sol6.1/high; runtime model telemetry недоступна.

## P2 CLOSED

Смена authority identity при тех же phase/activeRole теперь вызывает existing callback. Если current route role-home, положительная current role запускает новую **guarded** canonical admission с force. Старый awaited ADMIN read по-прежнему отвергается existing owner fence; свежая admission использует новый identity. Router/chooser/security/grants/redirect/query/hash/force-only-current-role-home не меняются.

`webSession.identity` — computed primitive authority tuple, включающий user/session/version/rolesVersion/activeRole/readOnly. Display/profile snapshot update с равным identity/phase/role не изменяет ни один native watch source и остаётся no-op. При actual identity turnover на уже admitted explicit page existing callback conditions не требуют новой canonical navigation; stale-response protection не ослабляется. DOM/focus runtime результат из этого source вывода не заявляется.

## Независимая узкая проверка

- `git diff --check 756d412f1^ 756d412f1` — exit0; frozen diff ровно одна watch-source line.
- Повторно использован ранее сохранённый `/workspace/rct-pr3-review/fresh-role-home/PROBE-22972df63.cjs` из frozen756 WT: actual App watcher + real Vue refs/MemoryRouter + reduced existing owner fence. Native exit0; после same-role authority turnover current route **admin-dashboard** вместо прежнего role-home.
- Прочитан `/tmp/rct-role-home-authority-probe.cjs`, author report `/workspace/rct-pr3-runtime/FRESH-ROLE-HOME-756d412f1.md` и `/workspace/rct-pr3-runtime/fresh-role-home/authority-probe.json`. Независимо выполнена только **current-source** branch этого memory recipe; в памяти исключена уже проверенная old229 branch и отключена запись author JSON. Ни один assertion текущей branch не удалялся/не менялся. Native exit0:

```json
{"revision":"corrected-source","afterAuthorityTurnover":"admin-dashboard","queryHashPreserved":true,"privateRouteRetained":true,"sameIdentityRefreshRetained":true,"oldResponseRejected":true}
```

Эта branch удерживает старый/current guards раздельно, обновляет same-identity display snapshot пока current read pending, сначала завершает старый response и проверяет отсутствие stale admission, затем current response/canonical path+query/hash. Далее actual identity change и same-identity snapshot update сохраняют тот же admitted route object. Это причинная проверка continuation/no-op, не property-text mirror и не browser/SUT proof. Обе effect scopes завершаются; нового framework или repository test files нет.

## Пределы

Авторские strict types/App ESLint/diff0 прочитаны как переданная evidence; four unchanged chooser tests/build/suites не повторялись. Reviewer не запускал browser, HTTP/API/network/backend/server/CI и не менял permissions/users/stand/product. Initial before observer-path qualification остаётся: его meReads0 не authoritative. Actual after fresh login/native role selection на integrated14250 с корректным real me-path observer остаётся обязательным и пока не принят этим report.

Source PASS позволяет root интегрировать229+756; он не восстанавливает locallyverified action до actual after evidence. Единственная новая запись reviewer — этот внешний immutable recheck report.
