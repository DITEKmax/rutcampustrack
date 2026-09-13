# Проверки

- Команды — из scripts, Gradle wrapper и CI текущего checkout; подготовительная партия
  фиксирует проверенные команды. Используй `rct-verification`: результат связывай с
  criterion, revision, command, exit code и evidence; skipped/не запущенное не PASS.
- Для code change базовые категории: build/lint/typecheck/unit/integration. Обоснуй
  неприменимость; обязательная непройденная проверка блокирует DONE. Документация/TOML:
  синтаксис, ссылки, конфигурация без прогона продукта.
- Не создавай тесты, которые повторяют реализацию или проверяют только wording.
  Проверяй observable behavior и регрессии по требованиям.
- UI: запуск, согласованный flow, screenshot, keyboard, состояния, темы, responsive;
  mocks не доказывают backend, MSW — PWA SW. Backend: сервис, endpoints, интеграции,
  связанные логи. Security: scanners + независимое review + runtime по риску.
- Изолируй тестовые users/data/ports/queues/volumes. Не используй production как
  test fixture. Перед reset проверь принадлежность окружения текущей задаче.
- Reviewer читает стабильный diff; изменение после review требует повторной
  проверки затронутого участка. Не повторяй весь набор без причины.
