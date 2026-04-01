-- V6__add_semester_first_week_type.sql
-- Adds first_week_type to semesters for week parity anchoring (LSSN-02)
-- Uses ('odd', 'even') only — 'all' is a template concept, not a semester property

CREATE TYPE week_type AS ENUM ('odd', 'even');

-- Implicit cast so JPA (binding varchar) can write to the week_type column (same pattern as V5)
CREATE CAST (varchar AS week_type) WITH INOUT AS IMPLICIT;

ALTER TABLE semesters
    ADD COLUMN first_week_type week_type NOT NULL DEFAULT 'odd';
