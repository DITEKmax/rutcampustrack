# Scope and criteria

## Scope

The bounded repair covers the shared internal JWT validator, its test fixture, its
focused validator tests, and the dual-mode filter propagation test. The four owned
paths are listed in packet.md. All other modified/untracked repository paths are
foreign or reserved and were preserved.

## Criteria

1. The cryptographic algorithm boundary accepts only RS256.
2. The raw JOSE header follows the root decision: alg and nonblank kid are required;
   typ=JWT is optional; no other header names or duplicate keys are accepted.
3. Strict raw JSON parsing rejects duplicate header/payload keys and preserves the
   frozen claim types and canonical forms.
4. The filter test asserts equality of the complete InternalJwtClaims tuple.
5. The source diff stays within the four assigned files and the evidence contains no
   bearer or internal token values.
6. Runtime and Gradle checks remain root-owned under the separate runtime lease.

The record accessor and clock-skew findings from the immutable review are explicitly
outside this repair because root rejected those deltas.
