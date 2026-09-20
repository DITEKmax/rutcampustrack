# Limitations and open gates

- Combined Java/Gradle compilation and tests are pending root H76/H77 heavy leases.
- Product runtime and PostgreSQL migration execution are pending root; no schema claim is made here.
- Independent full Sol review is pending and may identify a bounded correction. No post-freeze product edits are authorized without root release.
- Git reports LF→CRLF checkout warnings for exact source blobs under the Windows working-tree policy; `git hash-object --no-filters` proves the accepted source bytes are exact. The warnings were not treated as a product defect and caused no code change beyond exact blob transfer.
- No Terra escalation, child agent, source/runtime/main modification, push, merge, deploy, or production operation occurred.
