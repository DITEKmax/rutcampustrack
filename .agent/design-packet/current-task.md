# Design packet — JS-STUDENT-01

- **Scope:** local Figma evidence packet for `JS-STUDENT-01` Today and the related
  session projection `JS-SYSTEM-14`: six captured mobile states, retained raw
  contexts/screenshots/assets, state mapping and implementation boundaries.
- **Risk:** S2. The packet informs a later PWA/TMA Vue/PCSS implementation. It does
  not change product code, Figma, API contracts, backend/authz policy or publish.
- **Writer:** `/root/design_packet_writer`, `gpt-5.6-terra`, high effort. Root owns
  Figma reads; the reader supplied all evidence through the local receiver.
- **Base revision:** `87784165874e2da6fc261abc1c01584e24624289`.

## Acceptance criteria

1. Exact node links and six captured screen states are local, hash-addressed evidence.
2. Raw response completeness, timing proxy, Figma revision limitation and call status
   are explicit rather than inferred.
3. Actual generated-code asset URLs map to local canonical bytes and retain source
   provenance; boilerplate URLs do not become required assets.
4. Product story links distinguish visual evidence, canonical copy and unresolved
   request origin.
5. Owner-authorized planned states are separated from captured Figma states. Contract,
   authz and runtime remain later gates.
6. Local integrity checks and evidence name their command, revision, exit status and
   limitations. Product runtime is `SKIPPED` because product code is untouched.

## Owner decisions, 06.09.2026

- Missing Figma nodes for loading/error/offline/responsive/PWA-host/TMA-host do not
  block this packet; future implementation designs them from existing tokens,
  components, interface and domain logic.
- R008 wording is preserved as evidence; future implementation uses canonical
  JS-STUDENT-06 copy «Был, но забыл отметиться».
