# Correction-01 evidence

The pre-correction source guard passed 17/17 before the edit. The two target
rows were therefore byte-identified by the accepted source manifest as:

- `JwtTokenPurposeTest.java`: 15469 bytes,
  `19AFD43685CBB5C50A2E807E651308540BE0FCA18A560E286758F9B0C41C3644`.
- `SessionAdmissionServiceTest.java`: 9807 bytes,
  `95ED8A2215CE17EF64B9E87D839F38CFA4A61940F75FFB4C847DBFB7933AB6F6`.

The runtime-failure-01 evidence recorded two deterministic expired positive JWT
fixtures (`iat=2026-09-10T09:00:00Z`, `exp=09:01:00Z`) and two deterministic
ACTIVE admission fixture mismatches (claims omitted `group_id` while the
snapshot used group 10). It did not identify a product defect.

After the correction, the exact two-row before/after hash and byte manifest is
`manifest.json`. Its rows are:

- `JwtTokenPurposeTest.java`: 15485 bytes,
  `097B05FF70E8B3A80357D3C5E61F1A9AEE12ACE1079A2AF4345EC959F2DA49A3`.
- `SessionAdmissionServiceTest.java`: 9917 bytes,
  `2C68897A1550FCC30B4D295E3E596F67416A22AFBE7D00F342A921AB8BB121BE`.

The byte deltas are +16 and +110 respectively, matching the two time literal
replacements and two explicit `group_id` stubbings. Scoped pattern and
whitespace checks passed with exit code 0; see `checks.md`.
