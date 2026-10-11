# Git hygiene

Коммить исходники, тесты, документацию, конфигурацию и lockfiles. `.gitignore`
исключает локальные `.agent/`, `.codex/`, `.pnpm-store/`, окружения, caches,
логи, private keys и database dumps. Уже отслеживаемые файлы под этими путями
остаются отслеживаемыми: ignore не удаляет их из Git и не заменяет решение об
архивировании. Для нового намеренно versioned process-файла под `.agent/`
нужен адресный выбор интегратора.

Примеры `.env.example`/`.env.*.example`, `tests/e2e/.env.ci`, Gradle wrapper,
lockfiles и SQL migrations остаются доступными для Git. Private `.env.*`
исключены; не добавляй их принудительно. Backup-правила не скрывают все ZIP:
публичные исходные font archives могут быть частью проекта.

`frontends/landing/dist/` — канонический source без bundler и вход Docker COPY,
поэтому исключение сохранено. Новые `frontends/web-panel/dist/` — generated
Angular output и игнорируются; existing tracked output этим изменением не удалён.

Перед commit просмотри `git status --short` и адресный `git diff --cached`.
Добавляй согласованные пути явно. Не используй массовый `git add -A` для локальных
runtime/evidence и не снимай tracking с существующих файлов автоматически.
Установленные commit hooks описаны в [commit-hooks.md](commit-hooks.md).
