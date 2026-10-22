# Q04 archived registry independent whole source review

Verdict: **APPROVE_SOURCE**, no confirmed blocking findings in the complete five-file change. **Real PostgreSQL and browser after-fix acceptance remain pending / NOT_RUN.** The stopped local PostgreSQL attempt is not RED evidence; compiling the counterexample is not an assertion result.

Reviewed frozen base `75cb5ed5ae8d731991a5efd447c611b0eb9f65da` → clean author commit `6e59b8a6c66b92e8fa32243b37022c65cf13259d`, `/workspace/rct-worktrees/first3-spec-frame-20261013`, branch codex/q04-archived-registry-20261014. Whole diff includes prior test-only b95b34e1, already published separately on Root e4e9; product changes are not claimed executed by that earlier CI.

Reviewer requested/selected Sol6.1/high, actual metadata UNKNOWN. Read-only product/data/security/test review; only this scratch note written. No Source fixes, runtime/native checks, Node/Java/DB/Docker/browser/network, logs/private credentials, new packets/framework/children, main/push/deploy or frozen B013/B014 work.

Exact reviewed Source hashes, matched independently to author outcome q04current.product_fix.final_sources:

| File | SHA256 |
| --- | --- |
| services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRepository.java | 00431bf7abb7c0a04b0ed135d4a756bbcb208277d1ba81d2f2c2dc324c87eadf |
| services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java | 1822f15ace09e70b46ae0008284710060b21266eb96e2615fd57de2a8079db39 |
| services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java | 3e06b4d68e61adfbb43805e1a31802ea271b141a2edeba100117e00588a05672 |
| services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserSearchIT.java | 2efdde78e2786428cfcf5276bde6a816a661b513ed8447319076c5f293860e70 |
| tests/e2e/specs/mobile-real-server.spec.ts | b02d9e3889b68f43e3ff7e4319167c60a9ee949921ea971742c9e13dc88969a9 |

Independently opened current governing state/resources, contract/decisions/questions, canonical JS-ADMIN-04 and dated2026-10-02 restore-without-rights decision; whole diff plus surrounding old/current UserService/UserSpecifications/UserRepository/User entity/role-grant entity, controller admission and assembler mapping, relevant existing service/MockMvc tests, real Vue filter labels/client query, complete archive/restore fixture tail, author outcome qualifications.

Query/data behavior:

- Existing User @SQLRestriction(status<>'archived') remains unchanged. Ordinary list, identity/login/teacher lookup and other JPA repository methods retain that restriction. New native method is invoked only for an explicit archived role filter, or explicit archived account filter when roleStatus is absent. It does not implement an unconditional include-archive mode.
- Existing validateRoleFilter runs first: roleStatus requires a role and only its applicable accepted status is permitted. Therefore the native predicate cannot accidentally ignore a standalone archived-role request with no role. Role/status filters remain durable-grant EXISTS predicates; this avoids row duplication and preserves same-role/status binding.
- The archived-role branch intentionally does not force account ARCHIVED, allowing a restored ACTIVE account whose grants remain ARCHIVED, as required by Oct02. Role-status precedence over account status matches the existing Specification path. Account ARCHIVED without roleStatus retains exact account-status filtering.
- Page and count use the exact same immutable predicate string. All search/filter values are bound parameters; search retains login/FIO/employee/Telegram fields and literal percent/underscore/backslash escaping, null handling and case-insensitive normalization. No user input is concatenated as SQL. The new sort mapping covers the current registry/export accepted fields and actual Vue name/id sequence, retains direction/options, maps physical columns, rejects unknown fields safely and appends unique id when absent for deterministic equal-name paging. No offset/size/count predicate mismatch or join multiplicity found.
- Native SELECT u.* hydrates the existing entity only internally. UserController.listUsers remains ADMIN/USERS_VIEW and explicitly maps safe UserResponse with includeInitialPassword=false and existing batched role grants. The query does not serialize entity/password/private columns. Single-user initial-password display remains governed by existing credential authority, unchanged; FULL/LIMITED USERS_VIEW is not broadened. No API/entity/schema/grant/auth configuration change.

Tests/client oracle:

- New UserSearchIT is a meaningful signed-ADMIN MockMvc/real-PostgreSQL counterexample: archived account plus restored ACTIVE account with archived student grants, archived teacher and unrelated suspended student; it requires exact two-page count/order/id tie-break, separate account-archive filtering, default omission of archived accounts, and literal-percent search. Old global-restriction query cannot meet these expected results. It is transactional and uses synthetic records. Source is valid evidence of intended assertion sensitivity; execution/RED/GREEN remains unproved.
- Existing Mockito adjustment selects the new guarded repository route; it is a mechanical delegation check, not substituted for the real query proof. Author Java formatter/check/compile and7Mockito tests PASS are appropriately bounded evidence.
- Fixture adds only actual Student/Archived filter navigation before original search/open/restore. Current Vue details/buttons and dynamic available statuses match those selectors; client sends role=STUDENT/roleStatus=ARCHIVED. Original archive HTTP/result, denied old session, attendance/history retention, restored identity/no ACTIVE/selectable grant/no group/headman/helper assertions remain intact. This makes the archived account discoverable through the accepted explicit filter instead of weakening ordinary-list exclusion.

Author outcome records Java local phases74.529s and Node4checks5.846s PASS/reaped/cleaned; reviewer did not rerun them. Local real-PG attempt stopped for disk before XML/assertion, owned resources later cleaned and lease released. Real database page/count/native mapping and actual browser archive→find→restore must still pass on the final product revision in normal CI. This approval is Source-ready integration review, not product DONE or runtime acceptance.
