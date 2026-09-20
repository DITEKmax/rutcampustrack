# R5 instruction pointer

This evidence and the R5 source diff follow the canonical project instructions:

- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/AGENTS.md`
- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/AGENTS.md`
- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/services/AGENTS.md`
- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/tests/AGENTS.md`
- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/docs/agent-workflow.md`
- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`
- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/CURRENT.md`
- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/REQUESTS-R4-R5.md`

The orchestration rules file was verified as SHA-256
`B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
The applicable skills were read from
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agents/skills/rct-source-resolution/SKILL.md`
and `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agents/skills/rct-verification/SKILL.md`.

R5 is the sole writer for the bounded env/template/CI-E2E input and
StudentApi contract test scope in this isolated worktree. R4 owns Requests UI
changes; generated OpenAPI and TypeScript artifacts remain generator-owned.
