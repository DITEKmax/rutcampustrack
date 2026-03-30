-- V5__add_enum_casts.sql
-- Add implicit casts from varchar/text to PostgreSQL enum types so that
-- JPA AttributeConverter (which binds enums as character varying) works correctly.

CREATE CAST (varchar AS user_role)    WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS account_status) WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS subject_type)  WITH INOUT AS IMPLICIT;
