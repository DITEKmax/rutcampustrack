# Correction-02 criteria

1. The positive HTTP test uses a try-with-resources
   `AnnotationConfigApplicationContext` and closes it after the existing HTTP
   assertions complete.
2. The context registers the existing preinitialized `signingService`, the
   existing JDBC `authorityHolder.authority()` as `SessionStatePort`, and the
   actual `InternalIssuerProperties` instance as singleton beans. Production
   `SessionAdmissionService.class` and
   `InternalSessionAdmissionController.class` are registered as classes and
   the context is refreshed.
3. The test obtains both production beans from the context, asserts the
   registered instances are the expected ones, and routes the existing
   access-to-internal-token, no-store, and old-route-404 flow through the
   context-created controller.
4. The positive path does not call the four-argument Clock constructor; the
   separate service-secret denial test and its existing helper remain intact.
5. The exact source manifest reports this IT as the third expected drift after
   correction-01; the two correction-01 hashes remain expected, twelve other
   existing source rows remain unchanged, and two deleted rows remain absent.
6. Only the owned IT and correction-02 evidence are changed; no product
   behavior or shared contract is changed.
