# Phase 5: Entity and Repository Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-30
**Phase:** 05-entity-and-repository-foundation
**Areas discussed:** Soft delete filtering, Login generation, Permission array mapping

---

## Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| Entity packaging | Flat package vs per-domain packages | |
| Soft delete filtering | How archived users are excluded | ✓ |
| Login generation | PostgreSQL SEQUENCE vs MAX()+1 | ✓ |
| Permission array mapping | VARCHAR(64)[] to Java mapping | ✓ |

---

## Soft Delete Filtering

| Option | Description | Selected |
|--------|-------------|----------|
| @Where on entity | Hibernate @Where(clause = "status != 'archived'") — automatic filtering | ✓ |
| Custom repo methods | findAllByStatusNot(ARCHIVED) — explicit but verbose | |
| Default + explicit | @Where default plus separate findIncludingArchived methods | |

**User's choice:** @Where on entity
**Notes:** Automatic filtering everywhere. Admin operations needing archived users will use native queries.

---

## Login Generation

| Option | Description | Selected |
|--------|-------------|----------|
| PostgreSQL SEQUENCE (Recommended) | CREATE SEQUENCE in V3 migration, nextval() is atomic | ✓ |
| MAX(login) + 1 with lock | SELECT MAX approach with advisory lock, race-prone | |

**User's choice:** PostgreSQL SEQUENCE
**Notes:** Research had flagged MAX approach as race-prone. SEQUENCE is the standard PostgreSQL pattern.

---

## Permission Array Mapping

| Option | Description | Selected |
|--------|-------------|----------|
| String column + converter | Comma-separated String, convert via AttributeConverter | |
| Hibernate @Type array | @JdbcTypeCode(SqlTypes.ARRAY) with String[] field | ✓ |
| You decide | Claude picks based on codebase patterns | |

**User's choice:** Hibernate @JdbcTypeCode(SqlTypes.ARRAY) with String[]
**Notes:** Native PostgreSQL array support. Conversion to List<AssistantPermission> happens in service layer.

---

## Claude's Discretion

- Entity packaging: flat `entity/` package (consistent with Auth Service pattern)
- Entity design: Long FK fields, Lombok, GenerationType.IDENTITY, OffsetDateTime (all from Auth Service pattern)

## Deferred Ideas

None
