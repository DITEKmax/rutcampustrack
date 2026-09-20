# Codex agent routing

Root orchestration uses Astra medium. It classifies risk, creates a compact task
contract, actively delegates useful bounded work, and accepts evidence. It does
not create a fixed roster of agents or a second orchestration tree.

| Work | Model and effort |
|---|---|
| Root | Astra medium |
| Obvious S0/S1 plan | Astra low |
| Architecture, product, ambiguity, or risk | Astra medium/high |
| Narrow scout and fallback | Luna high |
| Bounded implementation | Luna max |
| Implementation/debug escalation | Terra medium/high, preferring high |
| Hard debug, security, concurrency, important review | Sol high |
| Exceptional S4 after explicit justification | Sol xhigh |

| Risk | Minimum route |
|---|---|
| S0 | Luna high lookup or Luna max tiny bounded edit → applicable check → root result. No mandatory scout, planner, or review. |
| S1 | Narrow scout when useful → compact contract → Luna max → checks/runtime. |
| S2 | Luna high scout → Astra low/medium contract → Luna max; Terra high on complexity → checks/runtime → fresh Sol high important review. |
| S3 | Scout → Astra medium/high contract → Terra high or Sol high → applicable checks/runtime → fresh Sol high review; fresh Astra medium/high architecture review for critical architecture, risk, or uncertainty. |
| S4 | Exceptional justified Sol xhigh or Astra high; retain all S3 safeguards. |

Explorer and reviewer are read-only. A developer is the sole writer for its
assigned task and preserves work outside scope. One shared checkout has one
writer; parallel writers require separate worktrees, resources, frozen baseline,
and contract revision. Each spawn receives explicit model/effort, a fresh compact
packet, and `fork_turns="none"`; role files do not pin model or effort.

The handoff is evidence → planner → bounded implementation → independent review.
The scout returns compact evidence. The planner and senior reviewer open critical
original sources instead of relying only on a scout retelling. The planner's
contract states Goal, Context/evidence, Relevant scope, Required behavior,
Constraints, Existing patterns, Acceptance criteria, Verification, and Do not.
The developer receives that contract and required files. The reviewer receives
the original goal, contract, stable diff, and checks.

On FAIL record defect, evidence, correction, scope, and verification; select the
cheapest capable repair (Luna max, Terra high, or Sol high) and obtain an
independent recheck. Do not repeat an identical attempt without new evidence. A
warning or error needs a request link and reproduction before it changes code.
S3 safeguards mean applicable checks and fresh review; production deployment,
migration, data destruction/backup, firewall, or secret rotation needs approval
for that specific operation after checks and rollback are ready. Important review
is fresh Sol high. No configuration file claims to change an already running
session; confirm selected parameters at runtime.
