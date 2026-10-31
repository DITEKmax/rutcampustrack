# Gitleaks finding — synthetic request UUID

Verdict: **false positive; an exact fingerprint exception is appropriate**.

Reviewed the redacted finding in `/tmp/pr3-root-gitleaks-source-20261024.json` and frozen commit `f45b754ac6769525e503692e49ddaaff15a3bd6e`, file `frontends/web-vue/src/features/headman/request-decision-state.test.ts:9`.

The matched `const key` is a fixed synthetic UUID fixture. It is assigned to `FrozenRequestDecision.selection.requestKey` and echoed by the test's simulated command receipt. The test uses null access tokens and mocked responses to verify request reconciliation. This value is neither a credential nor a bearer/capability token; it grants no service access. No actual secret was found in the reported location.

Allow only this exact reported fingerprint:

```text
f45b754ac6769525e503692e49ddaaff15a3bd6e:frontends/web-vue/src/features/headman/request-decision-state.test.ts:generic-api-key:9
```

No blanket path/value allowlist, rule disabling or product edit is justified or performed. This verdict covers this finding only, not an independent full repository secret scan. Requested review model remains `gpt-6.1-sol/high`; runtime model telemetry is unavailable.
