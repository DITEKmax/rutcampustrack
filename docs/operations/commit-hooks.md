# Commit hooks

В Windows checkout с установленными Git, Python и `uv` выполни из проекта:

```powershell
pwsh -NoProfile -File scripts/Install-CommitHooks.ps1
```

Installer создаёт локальное Python-окружение с `pre-commit==4.6.2` и устанавливает
стандартный `.git/hooks/pre-commit`. Он заранее загружает зависимости шести hooks
из `.pre-commit-config.yaml`: Gitleaks `v8.22.1`, YAML, JSON, файлы больше 1024 KB,
финальный перевод строки и пробелы в конце строки (`pre-commit-hooks v5.0.0`).
Gitleaks собирается из pinned Git revision; pre-commit сам загружает Go, если
системный Go отсутствует. Глобальные Go, Python-пакеты и Git config не меняются.
Python автоматически не скачивается: при его отсутствии установка останавливается.

Окружение и caches находятся в `.agent/tooling/` родительского MAIN checkout,
определённого через common Git directory. Generated hook сохраняет абсолютные
`PRE_COMMIT_HOME`, `GOCACHE`, `GOMODCACHE`, поэтому linked worktrees используют
один подготовленный cache. Добавлены только эти exports; штатные аргументы
pre-commit и выбор Python сохранены. PATH глобально дополнять не нужно.

Установка убирает только одиночный локальный `core.hooksPath`, совпадающий с
штатным common Git `hooks`. Другие custom/inherited значения приводят к остановке.
Перед переустановкой проверь имеющийся hook: pre-commit сохраняет существующий
чужой launcher согласно своему migration mode.

При `git commit` проверяются staged изменения. YAML/JSON/Gitleaks/large-files
блокируют неподходящие файлы; EOF/whitespace могут исправить рабочий файл и
остановить commit — проверь diff и повторно добавь именно исправленный файл.
Только пять byte-exact архивных снимков исключены из EOF/whitespace:
корневой и три вложенных `AGENTS.md.inactive` в
`docs/archive/agent-instructions/2026-09-05/`, а также
`docs/archive/database-overview/2026-10-11/DATABASES_OVERVIEW.md`.
Для них сохранены остальные применимые проверки, включая поиск секретов;
`.gitattributes` отключает преобразование переводов строк этих снимков.

Локальная проверка 2026-10-11: фактический общий Git hook пропустил корректные
синтетические YAML/JSON/текст, отверг шесть отдельных дефектов за один запуск,
затем пропустил исправленные fixtures без загрузки зависимостей. Gitleaks показал
редактированную находку `github-pat`. Реальный index проекта и приватные файлы
не сканировались. Evidence: `.agent/root-cleanup-20261011/approved2/hook-checks.json`.

Сохраняй `.agent/tooling/` локально и не добавляй в Git. При переносе MAIN checkout
запусти installer снова: абсолютные пути Python/cache должны соответствовать
новому расположению. При удалённом окружении hook останавливает commit; заново
установи инструменты той же командой.
