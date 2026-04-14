-- V9__groups_archived_at.sql
-- BUG-006 п.6 / план 58-06: добавить временную метку архивации группы.
--
-- Сервис архивации (GroupArchivalService) ставит is_active=false и
-- archived_at=now() одновременно, чтобы отличать естественный выпуск
-- от любого другого деактивирования.
--
-- Индекс на is_active (partial, только активные) ускоряет listGroups
-- при фильтре status=active и подсчёт active-групп в dashboard.

ALTER TABLE groups
    ADD COLUMN archived_at TIMESTAMPTZ NULL;

CREATE INDEX idx_groups_is_active ON groups(is_active) WHERE is_active = true;
