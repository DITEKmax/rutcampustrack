# Correction-02 compile failure

## Scope

Auth Stage2 correction tests after the recorded independent review FAIL.

## Reproduction

Command:

`\.\\gradlew.bat :services:auth-service:auth-app:compileJava :services:auth-service:auth-app:compileTestJava --no-daemon --no-parallel --max-workers=1 --console=plain`

Environment: Windows PowerShell, shared checkout, revision after the bounded
auth correction edits. Exit code: `1`.

## Raw evidence

`compileTestJava` reported:

```text
AuthSessionControllerTest.java:239: error: cannot find symbol
        verifyNoInteractions(wsTicketService);
        ^
  symbol:   method verifyNoInteractions(WsTicketService)
  location: class AuthSessionControllerTest
1 error
> Task :services:auth-service:auth-app:compileTestJava FAILED
BUILD FAILED
```

The same Gradle process also reported a secondary
`FileAlreadyExistsException` while moving its problems report; the actionable
source failure is the missing Mockito static import above.

## Correction

Restore the existing `verifyNoInteractions` static import. The authority
failure test still needs to assert that optional WS cleanup is not called.

## Verification

Rerun the exact compile command. No product test had started before this
compile failure.

