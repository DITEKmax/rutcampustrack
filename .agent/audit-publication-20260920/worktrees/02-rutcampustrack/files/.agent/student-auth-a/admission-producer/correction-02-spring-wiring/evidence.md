# Correction-02 evidence

Root finding and reproduction: at the accepted pre-correction source hash
`A8799DE7C76BD2324621C89DDE56D57658440CFB0DEFDD1730257588DED06E6A` (20270
bytes), `httpAdmissionUsesRealSecretGuardAndReturnsSignedInternalToken` called
`mockMvc(admissionService(now), properties)`. That helper constructed
`SessionAdmissionService` with the four-argument fixed-Clock constructor and
constructed `InternalSessionAdmissionController` with `new`, so the positive
HTTP flow could not prove Spring constructor injection.

The correction registers the existing initialized instances with
`context.getBeanFactory().registerSingleton(...)`. This preserves the seeded
RSA keys and JDBC authority while allowing only the production service and
controller classes to be instantiated by Spring. The production service's
three-argument `@Autowired` constructor is therefore the context path under
test; the four-argument helper remains only for the separate denial/authority
tests.

The exact before/after hash and byte manifest is `manifest.json`:

- before: 20270 bytes,
  `A8799DE7C76BD2324621C89DDE56D57658440CFB0DEFDD1730257588DED06E6A`;
- after: 22027 bytes,
  `6C06F2AD5E4D5EBBAC99574802C112B41234942A091FA2A2AEE37F481D9C5E4A`.

The source-17 post-correction guard and scoped static assertions are recorded
with exit codes in `checks.md`.
