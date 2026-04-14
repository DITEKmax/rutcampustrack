-- V8__group_unify_name.sql
-- BUG-006 п.5: слить поля name+code в одно поле name.
--
-- Активный формат имени группы: ^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}$ (кириллица, 3 цифры)
-- Архивный формат (устанавливается сервисом архивации, см. план 06):
--   <active> (выпуск YYYY) — например 'УИТ-411 (выпуск 2026)'.
-- VARCHAR(32) покрывает и активный (макс 8), и архивный (макс ~22) форматы.
--
-- Миграция идемпотентна в пределах своей версии: column code дропается, данные переносятся.
-- В проде только тестовая группа — потери данных невозможны.

-- 1) Перенос значений code → name там, где code заполнен.
UPDATE groups
   SET name = code
 WHERE code IS NOT NULL
   AND code <> '';

-- 2) Схема name: VARCHAR(32) UNIQUE (ранее VARCHAR(128) NOT NULL без UNIQUE).
ALTER TABLE groups
    ALTER COLUMN name TYPE VARCHAR(32);

ALTER TABLE groups
    ADD CONSTRAINT groups_name_key UNIQUE (name);

-- 3) Удалить колонку code (вместе с её UNIQUE constraint — drops каскадно).
ALTER TABLE groups
    DROP COLUMN code;
