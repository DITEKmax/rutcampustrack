# Criteria

1. `requireJoseHeader(raw.header())` остаётся до `.verifyWith(...)`.
2. Raw JOSE gate сохраняет exact plain `RS256`, required nonblank `kid`, optional
   plain `typ=JWT`, duplicate/unknown/missing/wrong-type rejection.
3. JJWT продолжает вызывать `.verifyWith(publicKeyProvider.getPublicKey())`.
4. Только target source изменён этим correction; test/fixture hashes совпадают с
   pre-correction manifest.
5. Runtime acceptance остаётся root-owned.
