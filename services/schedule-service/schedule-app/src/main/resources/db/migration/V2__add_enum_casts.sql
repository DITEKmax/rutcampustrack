-- V2__add_enum_casts.sql
-- Add implicit casts from varchar/text to PostgreSQL enum types so that
-- JPA AttributeConverter (which binds enums as character varying) works correctly.
-- Covers both INSERT assignment and WHERE ... IN (...) comparisons.

CREATE CAST (varchar AS week_type)     WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS lesson_status) WITH INOUT AS IMPLICIT;
CREATE CAST (text    AS week_type)     WITH INOUT AS IMPLICIT;
CREATE CAST (text    AS lesson_status) WITH INOUT AS IMPLICIT;
