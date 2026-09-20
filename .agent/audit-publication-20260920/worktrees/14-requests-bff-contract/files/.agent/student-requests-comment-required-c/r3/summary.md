# r3 summary
Status: RELEASE to root.
Goal met: StudentRequestDomainIT raw Mongo inspection now queries BSON ObjectId representation while retaining the no-attachment-payload assertion and retention checks.
Scope delta: test file only; no service, entity, proto, BFF, config, generated, or lockfile changes.
Evidence: frozen baseline SHA 4AB8154262472792F051C0E15B742379A4D652C02430B2E1D3C9B54B93FE8BE2; final SHA BDA19E54D3B69BC6A7E87D7ABB7F3807335A469375CCE40707255658D5EF9C68; final import/query shape at lines 8 and 529.
Verification: targeted static checks exit 0; no Gradle/Docker/runtime per contract.
Diff: exactly one canonical ObjectId import and one raw _id query conversion; existing assertions preserved.
Limitations: target file is part of the pre-existing untracked producer area, so git diff -- target is empty; root owns integration rerun and final review.
