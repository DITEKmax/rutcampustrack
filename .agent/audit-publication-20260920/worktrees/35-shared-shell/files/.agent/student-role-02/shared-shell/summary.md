# Summary

Implemented the compact shared mobile shell contract in the assigned
`frontends/mobile-core` scope. The Today screen now delegates dock rendering and
route/keyboard/Back policy to reusable shared components while keeping all
existing Today content and events. Unimplemented routes stay disabled and host
integration is callback-only. The shell also keeps the host keyboard signal
distinct from an omitted Boolean feature prop and explicitly owns the project
font/number settings so extraction does not regress dock typography.

Verification is recorded in `checks.json` and `evidence.md`: workspace
typecheck, production builds, six contract tests, and scoped lint pass. Full
lint has one pre-existing fixture transport finding at line 94; it was not
changed because it is unrelated to this request. Runtime evidence from the
existing port 5175 server confirms the root shell, visible dock, and five
expected nav states; the component probe confirms host keyboard hides the dock,
and computed styles confirm Onest/tabular typography.

No commit was created. The parent/root should review the scoped diff and decide
integration with the frozen contract; no product or scope decision is pending
from this leaf.
