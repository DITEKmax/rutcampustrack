# Student homework API diff map

- `proto/academic.proto`: `SetHomeworkCompletion` request/response RPC.
- `services/academic-service/academic-app`: atomic completion repository
  operations, active student domain service, signed mutation identity boundary,
  gRPC status mapping and focused tests.
- `services/mobile-bff/mobile-bff-api-contract`: homework response/command
  schemas and GET/PUT Student API annotations.
- `services/mobile-bff/mobile-bff-app`: gRPC mapping, date-range/order/null-link
  query projection, no-store controller responses and focused query tests.
- `frontends/mobile-core`: generated-schema aliases and typed homework methods.
- `frontends/mobile-core/src/test-adapter/fixture-transport.test.ts`: bounded
  lint-only correction requested by root.
- `.agent/student-role-02/homework-api`: compact contract and evidence records.

No lockfiles, configs, UI, migrations, Figma files, old toggle endpoints or
unrelated OpenAPI services were changed.
