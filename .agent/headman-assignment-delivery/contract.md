# JS-ADMIN-06/07: назначение старосты

Baseline: `e21e9d331fe07d7b740129ecd7c6a6de3b0dd972` on
`codex/headman-assignment-20260922`; assigned WT was clean before branch
creation. MAIN and foreign worktrees are outside this scope.

## Goal

ADMIN выбирает старосту из фактического состава группы. После успешной
операции реестр показывает `ACTIVE` и нового старосту после повторной загрузки;
прежнее durable headman authority отзывается, а сохранённые assistant rows
становятся inactive.

## Context / evidence

- `GroupApi` сейчас не имеет ADMIN roster/preview/assignment contract.
- Реестр JS-ADMIN-05 вычисляет статус и FIO из active `user_role_grants` с
  `role='headman'`.
- Legacy `UserService.patchUser(PatchUserRequest.isHeadman)` блокирует только
  target user, меняет legacy flag и пишет grants, поэтому оставляет обход
  group-wide CAS/unique правила.
- `GroupRepository.findByIdForUpdate`, `UserRepository.findByIdForUpdate`,
  `UserRoleGrantWriter.synchronizeDerivedHeadman` и
  `HeadmanAssistantRepository.revokeAllByGroupId` уже являются каноническими
  building blocks.

## Relevant scope

Academic group API/DTO/controller, новый canonical assignment service,
узкие repository/role-writer/UserService changes needed to close bypasses,
additive migration, focused PostgreSQL IT, и feature `admin-groups` client/
screen в `mobile-core`. AssistantService, permission authority, App/global
configuration, proto/generated files и чужая assistant-ветка исключены.

## Required behavior

1. `GET /academic/groups/{groupId}/headman/roster` возвращает только active
   STUDENT grant этой группы, независимо от base `users.role` и base account
   status; archived users и missing/non-active STUDENT grants не допускаются.
   Derived HEADMAN scope всегда берётся из этого grant.
2. `GET .../headman/preview?studentId=...` валидирует candidate и возвращает
   серверный план последствий без записи.
3. `PUT .../headman` принимает `studentId` и `expectedHeadmanId`; ADMIN-only,
   архивная/несуществующая группа и non-member отклоняются.
4. В mutation все user rows (старый и новый headman) блокируются в ascending
   id, затем блокируется group row; после lock повторно проверяются membership,
   active headman и CAS. User lock после group lock запрещён.
5. Новый grant и legacy `users.is_headman` меняются одной транзакцией;
   active headman unique index защищает остаточный race. Same-headman повтор
   идемпотентен и не отзывает assistants.
6. При смене старый grant становится suspended, новый active, assistants
   группы bulk-revoked с сохранением строк/истории, `GroupUpdatedEvent`
   публикуется, caches очищаются after commit.
7. Constraint race возвращает понятный 409; migration останавливается с
   диагностикой при pre-existing duplicate active headmen и не выбирает
   победителя.

## Constraints

No production migration, data deletion, push/deploy, full suite, new framework,
or broad user/assistant refactor. Existing assistant schema has no headman_id;
accepted behavior is group-wide revoke. Legacy admin-users compatibility must
be resolved without lock inversion or silent bypass.

## Existing patterns

Use `@RequireRole({ADMIN})`, `ConflictException`/`GlobalExceptionHandler`,
`GroupUpdatedEvent`, `@Transactional`, durable grants, after-commit cache
invalidation, mobile-core generation/abort guards, and current AdminGroups
registry styling. Keep `UserService.transferStudent` lock ordering unchanged.

## Acceptance criteria

First assignment, replacement, same-headman retry, archived/non-member
rejection, persisted registry reload, assistant retention with inactive state,
concurrent candidates with one winner/one 409, a multi-role account with a
suspended base grant keeping its headman grant through ordinary profile sync,
and STUDENT grant suspension/revocation withdrawing it; no illegal state in a
transfer/assistant race. No legacy writer can create a second active headman.
A stale roster/preview/PUT response cannot close or announce into a newer group
panel; a sent mutation is reconciled even when its panel context is stale or
the network outcome is ambiguous.

## Verification

One focused PostgreSQL/Testcontainers IT covers first/change/idempotency,
constraint/legacy bypass, concurrent candidates, multi-role suspended-base
profile sync and STUDENT revoke; affected Java selectors and one mobile-core
typecheck after implementation. Record exit codes, runtime evidence, exact
diff and remaining limitations in `summary.md`; Sol review follows frozen
source.

## Do not

Do not modify `AssistantService`, `UserRoleGrantWriter` consumers outside the
bounded canonical path, `HeadmanAssistantRepository` without the existing
group-wide method, App/global config, generated/proto/lock files, MAIN, or
foreign WIP. Do not claim full product readiness before runtime/live review.
