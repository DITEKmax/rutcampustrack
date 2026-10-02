Goal: close the single confirmed P2: same-key CREATE replay completes after a lesson transfer instead of retaining an uncertain form forever.

Context/evidence: source1562c8d5/evidence7ced5ac3; independent review otherwise passed critical paths. HomeworkService.replayExistingHomework returns current entity after checking the immutable accepted intent (373–392); HomeworkAssembler supplies bindingId/requestKey from that entity. Current lessonDate/lessonNumber/bindingMode cannot identify the original creation payload.

Relevant scope / exact changed inventory: frontends/mobile-core/src/features/homework/AssistantHomeworkScreen.vue; assistant-homework-create-intent.ts; headman-homework-client.ts; headman-homework-client.test.ts, all under the same features/homework directory. Stable correction commit a3960240. No generated/schema/backend change.

Required behavior: successful replay validates requestKey, positive homeworkId/bindingId, immutable group/subject/semester. The first validated202 PENDING receipt is frozen into the existing create intent; later202 or ACTIVE responses must retain its exact IDs and key. ACTIVE supplies current placement and follows the existing draft-clear/list-reload path. Lost responses preserve the original payload/key. Wrong key/known IDs/scope fail closed.

Constraints: nullable receipt fields on legacy list responses preserve list compatibility; creation requires identity evidence. Session generation and mutation revision fences remain. The intent remains session memory, with no durable/offline queue. No claim that first-response IDs can be compared with unknown IDs after a completely lost initial response: same requestKey and immutable scope are the available proof; after202 both IDs are known and compared.

Existing patterns: existing frozen create intent, generation-bound HeadmanHomeworkApi, same POST payload/key, clearDraft/load success path. Explicit ACTIVE/PENDING result replaces null-as-pending so identity is retained without fabricating success.

Acceptance criteria / result: actual client/intent tests cover lost POST response→transfer→same-key current response,202→transfer→same-key same-ID completion, frozen payload/receipt, tampered key/homeworkId/bindingId in both pending/active responses, and immutable scope mismatches. Existing DATE/edit/history checks in the affected file remain passing.

Verification: targeted Vitest two existing files,8/8 PASS, exit0,877ms (terminal07df68); strict scoped Vue/TS on AssistantHomeworkScreen/client/create-intent and their existing tests exit0,2.13s (15346c), stdout empty so only exit record exists; scoped diff check exit0 (12ca8b). Exact index4files and product commit exit0. No full suite, generation repeat, browser/backend/runtime or resources. Independent affected recheck pending; source-ready is not runtime acceptance.

Do not: edit App.vue/schedule/report/backend/generated/lockfiles/config, alter foreign WIP, launch heavy checks/runtime, children, reset/clean/rebase/stash/push/deploy. Root owns integration and affected independent recheck.
