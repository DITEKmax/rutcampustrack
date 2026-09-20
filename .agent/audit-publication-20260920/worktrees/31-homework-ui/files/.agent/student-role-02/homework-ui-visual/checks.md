# Homework visual evidence checks

Revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`  
Environment: Windows PowerShell, Node `v24.14.0`, npm `11.9.0`, Microsoft Edge headless, `ru-RU`, `Europe/Moscow`.

| Command / check | Exit | Evidence |
| --- | ---: | --- |
| `node --check .agent\\student-role-02\\homework-ui-visual\\visual-check.mjs` | 0 | Runner syntax valid. |
| `node .agent\\student-role-02\\homework-ui-visual\\visual-check.mjs` | 0 | `visual-evidence.json`, generated `2026-09-07T16:15:07.173Z`; 7 primary PNGs and 12 matrix captures. |
| Frozen source manifest SHA-256 check, 15 files | 0 | `SOURCE_SHA_MATCH=TRUE`; baseline `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`. |
| Primary PNG dimension check | 0 | 7/7 are exactly `390x844`; responsive captures are `320x844`, `390x844`, `430x844`. |
| `git -C .agent/worktrees/student-role-02/homework-ui diff --check` | 0 | No whitespace errors; only existing LF/CRLF normalization warnings. |
| Final port ownership check for `127.0.0.1:5181` | 0 | `PORT_5181=FREE` after stopping the owned fixture server. |

The fixture runner used the real shared `HomeworkScreen`/`useHomework` with deterministic in-process transport. Axe violations were `0` for all seven primary scenes; horizontal overflow was `false` for all 12 matrix captures; Onest font faces reported loaded. No product, API, adapter, token, package or lockfile file was changed by this visual leaf.
