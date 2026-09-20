# Source evidence

## Frozen imports

`manifest.json` records all 13 accepted Auth13 paths and their expected SHA256
digests. The exact hash guard reported `import_hash_count=13` and
`static_scope_exit=0`.

## Source corrections

- `JwtService` now calls the existing `parseCanonicalUuid` helper from the raw
  session-access wire check; the previous missing helper reference was removed.
- `SessionAdmissionService` distinguishes an absent requested grant
  (`ROLE_NOT_GRANTED`) from a matching authoritative non-selectable grant
  (`ROLE_NOT_SELECTABLE`). The focused unit and PostgreSQL IT source assert the
  latter boundary.
- Expiration is read through `Claims.getExpiration()` after strict raw-wire
  validation, and the primary three-argument service constructor is explicitly
  `@Autowired`.

## Static checks

- `git diff --check` exit `0`.
- Frozen-import/hash/structure guard exit `0`.
- Token-disclosure scan exit `0` (`token-disclosure-scan=none`).
- Brace-balance guard for changed parser/service files exit `0`.
- Tracked source diff and owned-source trailing-whitespace checks exit `0`.

The shell emitted pre-existing PowerShell profile and inaccessible global Git
ignore warnings; neither changed files or the check exit codes.
