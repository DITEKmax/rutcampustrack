# ADMIN users API extension proposal

Scope: JS-ADMIN-01/02/03, baseline `368e99de779a72c33c831243dcf310fffb4422d1`.
The proposal is additive where existing consumers rely on the legacy scalar
fields. `user_role_grants` remains the only durable role authority; no second
permission or account store is introduced.

## Read model

`UserResponse` keeps the current scalar `role`, `status`, `groupId` and legacy
fields for compatibility and adds:

```text
roles: RoleGrantView[]
```

`RoleGrantView` is server-owned and contains:

```text
role: "STUDENT" | "TEACHER" | "ADMIN" | "HEADMAN"
status: "ACTIVE" | "EXPELLED" | "GRADUATED" | "SUSPENDED" | "DISMISSED" | "ARCHIVED"
groupId: number | null
groupName: string | null
selectable: boolean
readOnly: boolean
applicableStatuses: string[]
canUpdate: boolean
blockedReason: string | null
```

`HEADMAN` is returned when the durable derived grant exists, but is not an
ADMIN mutation target. `groupName` is a projection convenience; `groupId`
remains the mutation key. Detail and mutation responses return the same
projection after the transaction commits. List responses do not include a
plaintext initial password; create keeps the existing one-time credential
response, and detail retains the existing admin-only legacy behavior until the
credential-delivery contract is separately replaced.

The status applicability descriptor is server supplied: `STUDENT` supports
`ACTIVE`, `EXPELLED`, `GRADUATED`, `SUSPENDED`; `TEACHER` supports `ACTIVE`,
`DISMISSED`, `SUSPENDED`; `ADMIN` supports `ACTIVE` for this package.
Existing inactive ADMIN grants are returned read-only and are not a new
destructive UI action. `ARCHIVED` is retained for history/read-only projection.
Only `ACTIVE` grants are selectable; other role grants remain visible and
durable so an active grant for another role continues to work.

## Endpoints and request fields

Existing `GET /academic/users` remains server-paginated. `search` keeps its
case-insensitive login/name/Telegram search and adds employee number. The
existing `role` parameter filters against a grant role, and a new
`roleStatus` parameter filters that same role's grant status. A status without
a role is rejected with `400`; pagination metadata remains authoritative.

Existing `GET /academic/users/{id}` returns the additive `roles[]` projection.

Existing `POST /academic/users` remains one-person creation. Its current
role-specific fields stay compatible: `lastName`, `firstName`, optional
`middleName`, `role`, `groupId` for STUDENT, `employeeNumber` for TEACHER and
optional `telegramId` where the existing role validation permits it. The
server generates one role-independent login from transliterated family/given
names plus the last four Telegram/employee digits; a deterministic suffix is
used on collision and the database unique constraint remains the race
backstop. Adding a role never regenerates an existing login.

New role-scoped mutation:

```http
PUT /academic/users/{id}/roles/{role}
```

Request `RoleGrantUpdateRequest`:

```text
status: required enum value from the role's applicable status list
groupId: required only when adding STUDENT, omitted for TEACHER/ADMIN
employeeNumber: required when adding TEACHER
telegramId: required when adding STUDENT
```

The operation locks the user and target grant, validates role/status/group
compatibility, and upserts exactly that grant in one transaction. Existing
grants for other roles, their statuses, groups and history are preserved. It
rejects `HEADMAN`, `ARCHIVED`, invalid role/status pairs, foreign IDs and
non-ADMIN callers. This is also the add-role operation; a missing grant is
created only with the required role data so an ACTIVE grant is usable. An
existing STUDENT grant cannot change `groupId` through this endpoint: group
changes go only through transfer with its reason/history transaction. An
existing role status may change without rewriting another grant.

Existing `POST /academic/users/{id}/transfer` remains the only student group
transfer operation. It keeps `newGroupId` and required `reason`, closes the
open membership interval, creates the dated destination interval, and updates
the STUDENT grant group atomically. Generic PATCH group mutation remains
rejected when it would change membership.

## Authority and session behavior

Academic writes the same `user_role_grants` rows read by Auth and relies on the
existing roles-version trigger. Auth role admissibility is changed so only
`ACTIVE` is selectable; `EXPELLED`, `GRADUATED`, `SUSPENDED`, `DISMISSED` and
`ARCHIVED` remain read-only/non-selectable. Clearing a selected revoked grant
must not revoke or rewrite another active grant. A subsequent role selection
uses the existing session authority and can select the remaining active grant.

## Known source deltas carried into implementation

1. `User` and its current DTOs expose one scalar role/status; the additive
   projection must become authoritative for ADMIN users while preserving
   legacy fields for other consumers.
2. `UserRoleGrantWriter` currently synchronizes only the base/headman rows; it
   needs a locked role-scoped upsert that does not overwrite unrelated grants.
3. The grant check constraint and Auth `RoleStatus` do not contain
   `DISMISSED`; one additive migration and enum/read-path update are required.
4. `RoleStatus` currently marks terminal statuses selectable; the accepted
   active-only decision requires changing this admission rule and verifying
   stale selected-role isolation.
5. Existing create login generation is role-prefixed; only newly created
   users use the new name/digits generator. Existing logins remain unchanged.

No delete/archive/export/bulk import, permissions redesign, Telegram transport
change, or full admin CRUD is part of this package.
