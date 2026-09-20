# Repair summary

The recorded review defect was reproduced: the old whole-body retry turned a
successfully committed EXCUSE or LATE_CHECKIN decision with a lost commit ACK
into a false `409` and risked duplicate work. The narrow repair now resolves
an unknown commit result through a read-only exact-match recovery for the
authorized actor and requested decision (plus normalized EXCUSE comment), and
keeps ordinary retries/conflicts for all other cases.

Validation is green: main/test compilation, focused authorization (`7/7`) and
real Mongo domain integration (`19/19`) all exited `0`; `git diff --check`
exited `0`. The runtime used the production Mongo transaction/outbox path and
asserted one persisted terminal event for each ambiguity scenario.

Scope is limited to the service and focused IT in this worktree. The dirty
baseline is preserved, no commit was made, and fresh independent Sol recheck
is the remaining root gate.
