# Runtime evidence and limits

No live product runtime was started. The contract explicitly forbids contacting
Rabbit, Telegram, Academic, Attendance, or other external services, and the
available Python toolchain lacks pytest and bot dependencies. The applicable
local evidence is therefore the successful bundled-Python `py_compile` and AST
contract checks recorded in `checks.md`; pytest commands were attempted and
recorded with exit code 1 due to the missing executable.

No claim is made about live message delivery, acknowledgement, retry, or DLQ
behavior. The code path preserves the existing dispatcher/consumer boundary for
those behaviors.
