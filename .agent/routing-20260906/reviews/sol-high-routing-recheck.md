PASS — независимая recheck закрыла все пять findings.

- Полная S0–S4 routing-матрица восстановлена в global contract и task contract.
- Staged handoff требует чтения критичных оригиналов planner/reviewer.
- Astra architecture review выбирается по критичности, риску или неопределённости.
- Apply-script проверяет stage hashes и TOML до записи, использует atomic replace, rollback и readback.
- Target, backup и staged paths проверяются на symlink/reparse и выход за разрешённый root.

Стабильные SHA-256 до global apply:

- Task contract: `1B83B5B8B7BEB71DC492956517B93C504BD35E88650C10250319DB1232DCE6CC`
- Checks: `7F4A6DE74BD49B4CCD3CF30A6F79D02EFF407FB779B8285C1F509004623025DB`
- Apply-script: `13A15E6CBA219C49C1F10DFE58256878D3CF3E30555A45F6F95141F51976BF16`
- Global AGENTS: `F1099BC84FDB03831C85A5BA30A0C3B80B4ACF585CF2DD4FED9684F7B7E1FD17`
- Workflow: `3D83E17E9FA9E932B2DD42CB2161EA06830B4B1CA2D520C541A3839FDD36B131`
- Local reviewer: `17DF508550AEE4F5802A9AC6D5E88E388B56061F6F019CCC60DF7752A6528E5E`
- Parallel routing: `B7E1574A1BF80426E80152DDF01C4CEB2BA7C3A24CC408129FD9F33434017385`

Checks подтверждают syntax/preflight, policy assertions, CRLF preservation,
stage/live/backup guards, rollback, readback и explicit-apply guard.

Ограничение: реальный Windows symlink создать не удалось из-за `WinError 1314`;
reparse refusal проверен инъекцией на соответствующей boundary. Reviewer файлов
не менял.
