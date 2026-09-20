# Correction-02 diff and limitations

The owned IT delta is bounded to:

- importing `AnnotationConfigApplicationContext`;
- replacing only the positive HTTP test's manually constructed service and
  controller with a try-with-resources context;
- registering the three preinitialized dependency instances as singletons;
- registering and refreshing the production service/controller classes;
- retrieving/asserting those production beans and passing the controller bean
  into a focused MockMvc overload;
- retaining the existing manual helper for the separate service-secret denial
  test.

The exact file hash/byte transition and source-17 classification are in
`manifest.json` and `checks.md`. The file is an existing untracked producer
addition in the shared dirty checkout, so ordinary `git diff` does not render
its content; hashes and scoped assertions are the bounded diff evidence.

Foreign work remains present and untouched. This leaf did not stage, commit,
reset, revert, delete, or alter product/shared/Auth13/other-test files. No
runtime or full producer acceptance is claimed.
