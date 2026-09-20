# Runtime evidence

Fixture: `.agent/student-role-02/homework-ui/runtime`, served with Vite on `127.0.0.1:5181` from the frozen worktree. The server was started with:

```text
node .\frontends\node_modules\vite\bin\vite.js --config .agent\student-role-02\homework-ui\runtime\vite.config.mjs --configLoader native --host 127.0.0.1 --port 5181
```

The browser fixture loaded the real shared `HomeworkScreen` and `useHomework` against a deterministic `StudentApi` fetcher. Server was stopped after evidence with `Stop-Process -Id 13548 -Force` (exit 0); no runtime process remained.

Observed through the accessibility tree and dark screenshot:

- Current feed rendered «Выполнено сегодня», «Завтра, 8 сентября» and «9 сентября» groups. A completed past lesson with `completedAt=2026-09-07T06:30:00Z` appeared once in the Moscow server-today group.
- Disclosure button changed from collapsed to expanded and exposed the inline title text.
- Completion changed the control to disabled «Сохраняем состояние…», then ACK moved the item into «Выполнено сегодня»; the reverse action returned it to its lesson-date group.
- A failing 503 fixture kept the item unchanged, exposed «Сохранение временно недоступно» and «Повторить». Changing scope removed that old retry/error state and loaded the new scope feed.
- Unsafe `javascript:` material displayed «Материалы недоступны: ссылка не поддерживается.» and exposed no material action. `link=null` exposed no material button or empty placeholder.
- Previous-range navigation loaded representative 28/29 August data and exposed «Вернуться к сегодняшним заданиям»; return restored the current feed.
- Offline toggle exposed read-only status and disabled completion controls with the accessible online-only explanation while leaving materials/disclosure available.
- Keyboard focus reached the Math completion control; `Space` invoked the same completion command and the ACK state was observed.
- `fixtureRootFont=24` rendered the same mobile composition without horizontal overflow in the available browser viewport; CSS constrains the shared surface to the existing mobile max width.

The available subagent browser did not expose a 390×844 viewport override, so the screenshot was captured in its default viewport (the component remained at its mobile max width). Light/system theme and real PWA/TMA/API service scenarios remain downstream integration checks.
