# Foundation summary — JS-STUDENT-01-r1

Date: 2026-09-06. Risk: S3. Writer: `/root/geo_contract_owner`. Requested route:
Sol high; runtime model/effort metadata was not exposed.

The freeze-ready foundation adds the Java-first mobile BFF REST contract, a
reproducible OpenAPI 3.0.1 snapshot, generated TypeScript and nine conforming
fixtures. Required-nullable values remain required keys with `null` in both schema
and generated TS. Geo input is a flat discriminated union with non-null coordinates.

The internal attendance proto now carries domain-owned eligibility and server time,
strict enums, coordinate presence and a stable gRPC error detail. The schedule proto
adds explicit room-change provenance. BFF composition therefore has no reason to
reimplement window, cooldown or manual-absence rules.

The Vue workspace pins Node/npm-compatible build, vue-tsc and ESLint tooling for
mobile-core/PWA/TMA. It commits one lock, generated types and contract fixtures.
Onest bytes are present through the pinned fontsource package. The unused deprecated
Telegram SDK was removed after its valibot chain reproduced four high audit findings;
the packet fixes a narrow typed injected-WebApp adapter instead.

All canonical export/drift, generated type drift, fixtures, contract tests, lint,
Vue/core foundation typechecks, protobuf generation and the targeted service compile
pass. The BFF foundation also starts on port 19080 and returns health UP plus the
expected OpenAPI metadata. Product handlers, FE screens, Mongo concurrency,
gateway/auth and the integrated HTTP-to-gRPC story are the next FE/BE lanes and
remain required before DONE.
