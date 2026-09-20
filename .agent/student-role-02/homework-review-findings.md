# Homework review — findings in progress

07.09.2026, fresh independent Sol high `homework_api_review`, stable22file manifest. Full final review pending; no product writes while review runs.

## MEDIUM — oversized numeric ID produces500
Reviewer confirmed `StudentQueryService.java:144` and contract `StudentApi.java:168`: regex permits arbitrary digit length, unguarded Long.parseLong throws for `9223372036854775808`, unmapped exception gives500 rather than400 INVALID_REQUEST. Reproduction: authenticated PUT `/api/v1/student/homework/9223372036854775808/completion` with `{completed:true}`. No Academic gRPC call should occur. Root accepts bounded correction: parse helper/catch to typed400, HTTP max+1 negative regression and no-RPC assertion; no unrelated ID/API cleanup. Fresh Luna repair after final review, independent Sol recheck required.

## MEDIUM — scalar coercion accepts malformed completed
Reviewer confirmed `StudentApiModels.java:238`: @NotNull Boolean does not prohibit Jackson scalar coercion (local2.18.2 TryConvert/ALLOW_COERCION_OF_SCALARS=true). `completed:1` or string `"true"` can reach mutation instead of400. Root accepts field/type-scoped BFF strict deserialization, no global ObjectMapper behavior change. HTTP negative1/string/null/missing must return400 with no RPC; literaltrue/false200. Contract module currently has annotations only, so prefer app-side scoped mixin/deserializer rather than adding dependencies solely to carry the implementation.

## MEDIUM — errors lack no-store
Reviewer confirmed `MobileProblemHandler.java:34–42,76–79` and `MobileIdentityFilter.java:53–59`: Homework controller sets no-store only after successful query, typed400/403/404/503 and authentication401 omit it. Root opened originals. Correct all Homework response paths including pre-controller auth with bounded handling; preserve unrelated Today/checkin behavior. Java-first error-response headers/OpenAPI must reflect contract. Add HTTP header assertions on400/401/403/404/503 and successful200; do not call tests200-only coverage sufficient.

## MEDIUM — missing active semester is misclassified as forbidden
Reviewer confirmed `MobileAcademicClient.java:34–36,69–70`: generic activeSemester call maps Academic NOT_FOUND to403 OUT_OF_SCOPE, so both Homework GET/PUT fail preflight with403 when the active semester is absent. Academic mutation already deliberately maps missing Semester toUNAVAILABLE (`AcademicGrpcServiceImpl.java:397–400`), but BFF preflight prevents reaching it. Root opened both originals and accepts Homework-specific preflight mapping to503 DEPENDENCY_UNAVAILABLE. Do not change generic Today/schedule mapping. HTTP fake GetActiveSemester→NOT_FOUND verifies both routes503, no homework read/mutation RPC, no-store.
