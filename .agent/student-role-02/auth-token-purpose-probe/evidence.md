# Auth refresh-token purpose — confirmed filter regression

07.09.2026. S3 security prerequisite to profile/session implementation. Root verification only; no product edits.

Severity: MEDIUM for the confirmed Auth filter token-purpose boundary failure; stronger endpoint/revocation impact remains unproven. Confirmed: the actual compiled Auth JwtAuthenticationFilter establishes an authenticated SecurityContext from a token produced by the actual JwtService.generateRefreshToken, with authority ROLE_null. It does not require an access purpose or consult refresh validity. Source locations: JwtAuthenticationFilter.java:37,46,48; JwtService.java:144,160. SecurityConfig.java:47 accepts any authenticated principal for non-public paths.

The frozen probe loads six real classes extracted from auth-app-0.1.0.jar SHA256 FF3B78750E3E81DCDA966899E9C35F3C7B62650B84F86B8076AEF55C4027974D and its 141 runtime libraries. Source manifest records the matching unchanged checkout files; git diff --quiet d3c31 for those sources exited0. Reflection supplies only a synthetic in-memory RSA key pair and fake User424242/STUDENT/group987; JwtService.init is never called. No key files, real tokens, Redis, DB or network are used. Neither synthetic key nor token is logged.

Environment: Windows PowerShell, Microsoft Java21.0.10. Command: java --class-path (exact classpath.txt contents) AuthTokenPurposeProbe.java. Initial sandbox run failed compilation access to existing local classes; sandbox-result.log is environment evidence only. Same scoped approved escalated command executed the real behavior, exit1 on the security assertion:

```
MISSING AUTHENTICATED=false AUTHORITIES=[]
ACCESS AUTHENTICATED=true AUTHORITIES=[ROLE_STUDENT]
REFRESH AUTHENTICATED=true AUTHORITIES=[ROLE_null]
AssertionError: Refresh token must not authenticate an access-token request
```

The missing-header and access controls pass. The refresh negative case fails. This is a real servlet-filter/SecurityContext result, not yet a complete Gateway→Auth HTTP or WebSocket exploit. No successful password change, ticket redemption, revoked-token HTTP access, or peer-data disclosure is claimed. Those effects need their exact endpoint/dependency checks.

Required bounded correction in forthcoming auth/session contract: signed and validated token purpose separation, fail-closed required claims, and fresh negative tests at Auth and Gateway plus full HTTP ingress. Ensure refresh validation/rotation remains separate and old revoked access/session checks follow the accepted session design. Do not patch this in the dependency-only writer's scope.
