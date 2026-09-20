# Scoped diff

Baseline: 8002b9ea4356b10779c5bb9a6d99746d32d78ae2

The scoped diff contains exactly four paths:

- InternalJwtValidator.java
- InternalJwtValidatorTest.java
- DualModeUserContextFilterTest.java
- InternalJwtTestFactory.java

Scoped diff stat from Git: 906 insertions, 132 deletions, 4 files changed.

The validator change adds bounded strict raw header/payload parsing, exact frozen
identity extraction, RS256 parser allowlisting, JOSE field checks, semantic checks,
and whole-second time checks. The fixture emits the required kid header. Tests add
alternate-algorithm, header, duplicate-key, canonical/type/range, semantic, and
complete-filter assertions.

The scoped name-only guard returned only the four paths above. Foreign dirty
Auth/Profile/Contracts work and reserved InternalJwtClaims were not reverted or
staged.
