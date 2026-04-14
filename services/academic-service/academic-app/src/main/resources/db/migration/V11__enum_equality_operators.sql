-- V11__enum_equality_operators.sql
-- BUG-006 gap-closure (plan 58-10).
--
-- Проблема: Hibernate 6 при criteria `cb.equal(root.get("role"), UserRole.STUDENT)`
-- рендерит SQL `u.role = ?` с bind-параметром как varchar (работает JPA-конвертер).
-- PostgreSQL не имеет оператора `user_role = varchar` — V5 IMPLICIT cast работает
-- только для assignment, не для operators. SQLGrammarException: operator does not exist.
--
-- Решение: добавить полноценные `=` и `<>` операторы между enum и text для всех
-- кастомных enum-типов проекта. Использует встроенные casts из V5.

-- ---------- user_role ----------
CREATE OR REPLACE FUNCTION user_role_eq_text(user_role, text) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT $1::text = $2 $$;

CREATE OPERATOR = (
    LEFTARG = user_role, RIGHTARG = text,
    PROCEDURE = user_role_eq_text,
    COMMUTATOR = =, NEGATOR = <>,
    RESTRICT = eqsel, JOIN = eqjoinsel
);

CREATE OR REPLACE FUNCTION user_role_ne_text(user_role, text) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT $1::text <> $2 $$;

CREATE OPERATOR <> (
    LEFTARG = user_role, RIGHTARG = text,
    PROCEDURE = user_role_ne_text,
    COMMUTATOR = <>, NEGATOR = =,
    RESTRICT = neqsel, JOIN = neqjoinsel
);

-- ---------- account_status ----------
CREATE OR REPLACE FUNCTION account_status_eq_text(account_status, text) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT $1::text = $2 $$;

CREATE OPERATOR = (
    LEFTARG = account_status, RIGHTARG = text,
    PROCEDURE = account_status_eq_text,
    COMMUTATOR = =, NEGATOR = <>,
    RESTRICT = eqsel, JOIN = eqjoinsel
);

CREATE OR REPLACE FUNCTION account_status_ne_text(account_status, text) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT $1::text <> $2 $$;

CREATE OPERATOR <> (
    LEFTARG = account_status, RIGHTARG = text,
    PROCEDURE = account_status_ne_text,
    COMMUTATOR = <>, NEGATOR = =,
    RESTRICT = neqsel, JOIN = neqjoinsel
);

-- ---------- subject_type ----------
CREATE OR REPLACE FUNCTION subject_type_eq_text(subject_type, text) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT $1::text = $2 $$;

CREATE OPERATOR = (
    LEFTARG = subject_type, RIGHTARG = text,
    PROCEDURE = subject_type_eq_text,
    COMMUTATOR = =, NEGATOR = <>,
    RESTRICT = eqsel, JOIN = eqjoinsel
);

CREATE OR REPLACE FUNCTION subject_type_ne_text(subject_type, text) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT $1::text <> $2 $$;

CREATE OPERATOR <> (
    LEFTARG = subject_type, RIGHTARG = text,
    PROCEDURE = subject_type_ne_text,
    COMMUTATOR = <>, NEGATOR = =,
    RESTRICT = neqsel, JOIN = neqjoinsel
);
