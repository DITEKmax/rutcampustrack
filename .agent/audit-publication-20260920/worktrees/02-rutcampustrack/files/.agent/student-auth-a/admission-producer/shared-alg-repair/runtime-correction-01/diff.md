# Diff

Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

Observed correction delta from the pre-correction working source:

```diff
             Jwts.parser()
                     .verifyWith(publicKeyProvider.getPublicKey())
-                    .sig().clear().add(Jwts.SIG.RS256).and()
                     .requireIssuer(properties.expectedIssuer())
```

Pre/post target manifest: 23291 bytes / `4696BD3886F4BFC145B92009D3DA5EC4E5BB1C7EAF5E6BA9C90CE6E405C483FF`
to 23230 bytes / `E8633DE82330BCCB8A9805E37351F5E9F439A27FD1C95524B760592B614E3FF5`.
The existing target overlay remains otherwise intact. No tests, fixture, producer,
record, configuration, or foreign dirty path was edited by this correction.
