-- V10__semesters_no_overlap.sql
-- BUG-006 п.7 (Plan 58-05): атомарная защита от пересечения семестров.
--
-- EXCLUDE USING gist (daterange(date_from, date_to, '[]') WITH &&) запрещает
-- вставку строки, у которой диапазон [date_from, date_to] пересекается с уже
-- существующим. Требует extension btree_gist для индексной поддержки.
--
-- Service-level check в SemesterService.create/update выдаёт human-readable 409
-- (ConflictException), а DB-constraint — защищает от race condition (T-58-05-01).

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE semesters
    ADD CONSTRAINT semesters_no_overlap
    EXCLUDE USING gist (daterange(date_from, date_to, '[]') WITH &&);
