# M01 Notes

## 2026-04-19

- Старт milestone'а. PLAN + CHECKLIST написаны. Коммит initial.
- Группа 1 (Gradle scaffolding) завершена:
  - Создан `gradle/libs.versions.toml` (catalog с 7 версиями) — см. DECISIONS.md.
  - 4 build.gradle.kts под решения NEW-34 (compileOnly для spring/jackson/slf4j).
  - `shared-test-containers` — плагин `java-test-fixtures` (см. DECISIONS.md).
  - CHECKLIST п.1 «services/shared/.gitkeep» — **skipped N/A** (директория
    наполнилась сразу 4 модулями с файлами, .gitkeep не нужен).
  - Билд `./gradlew build` отложен до наполнения модулей кодом (Группа 2+),
    чтобы не прогонять Gradle на пустых compileJava задачах.
