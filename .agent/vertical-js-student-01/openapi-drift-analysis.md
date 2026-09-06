# OpenAPI non-update drift analysis

Date: 2026-09-06. The first production-controller `OpenApiSnapshotIT` run failed
1/3 while its two runtime request-shape tests passed.

The new `StudentHttpGrpcAuthIT` requires `attendance-app` only in the Mobile BFF
test classpath. That transitive test dependency introduced two configurations that
do not exist in the production Mobile BFF runtime:

- shared-web auto-configuration appended generic 400/404/409/429/500 responses
  using `ErrorResponse` and `FieldError`, conflicting with the frozen
  `MobileProblemDetails` operation contract;
- Spring HATEOAS replaced the nested contract schema named `Link` with its own
  model, removing required URI `href` and adding unrelated fields.

Updating the canonical snapshot would therefore have recorded a false public
contract change. `OpenApiSnapshotIT` now excludes test-only
`SharedWebAutoConfiguration` and disables HATEOAS support for that test context.
The next non-update run passed 3/3 with exit code 0 in 51 seconds. The committed
`docs/openapi/mobile-bff.json` was not changed.
