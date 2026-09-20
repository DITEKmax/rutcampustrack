# Pre-fix reproduction: native fetch receiver

Root runtime evidence: PWA refresh succeeds, but `StudentApi.getSession()` calls a detached native `fetch` and browser throws `TypeError: Illegal invocation`. Fixture mode passes an injected transport and therefore does not cover the default branch.

Targeted regression is added before code correction: a stubbed default fetch throws unless `this === globalThis`; the injected fetch must continue to receive its own invocation semantics.

Command: `npx vitest run mobile-core/src/test-adapter/fixture-transport.test.ts`.
Pre-fix result: exit 1; the new test rejects with `TypeError: Illegal invocation` at `StudentApi.requestResponse` while all four fixture tests pass. After the wrapper correction it passes 5/5.
