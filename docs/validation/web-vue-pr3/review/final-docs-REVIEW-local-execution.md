# Final Root docs/handoff consistency review — one P2 instruction gap

Reviewed the uncommitted Root docs in `/workspace/rutcampustrack` on25October2026: LOCAL-EXECUTION, VISUAL-REMAINDER, CURRENT-PRIORITIES, PROGRESS changes and the assembled-nginx public final evidence/projections. Read-only; no source edits, tests, builds, Auth/DB/Docker/browser/owner-local execution.

## P2 — Optional owner-local Nginx instructions inherit the cloud check/down order

`docs/plans/web-vue-pr3/LOCAL-EXECUTION.md:92` delegates the full optional sequence to `tests/e2e/pr3-nginx/README.md`. That primary README's copyable up/check/down block runs direct API `http://127.0.0.1:18080` at line96 and shuts the Nginx down at line99; the owner-local replacement check against18443+CA appears only at line110. Following the linked blocks sequentially can query the old/local18080 stand and then attempt the correct local check after the listener was stopped. The wrapper names the correct local API/CA but does not explicitly replace the earlier cloud command/down before the owner follows it. Its fresh build outputs also need `SOURCE_SHA=$PR3_SHA` and `PWA_DIST=$GATE_STATE/pwa-dist`, rather than the primary README's historical accepted-PWA placeholders.

Minimal Root-doc correction: explicitly give the local variable bindings and say to substitute the18443+CA check for the cloud18080 check, performing it while the own Nginx is up; defer its scoped down until after the optional local/browser criteria. Preserve the exact order: known-terminal group → stop3Vites → optional sequential fresh prefix builds → prepare local network/gateway/TLS → up/nginx-t → local18443+CA check/browser criteria → own Nginx down → local.py stop for12backend. Do not touch18080 or re-run group. No source launcher change or new runtime run is needed.

## Other reviewed boundaries

No additional substantial blockers found. Toolchain is exact Node24.14.0/npm11.9.0/JDK21/Gradle8.12, clean new checkout/full published SHA and no old activate/secret exports are explicit. Correct local network/gateway/API CA and unchanged trust to TLSedge.10 are stated; no extra IP/subnet trust is requested. Unknown/NOT_RUN/provider limits and deferred owner-local group gate remain explicit.

Public JSON projections parse and all use source240a1b2a86a2b2ba0cbb89647a9ed8b151007007. Actual static/anonymous API PASS is separated from PWA bounded HTTP Auth/catalog/STOMP PASS; WEB rawEXIT1/completedfalse/apiProofnull/knownIDsfalse is retained with bounded source/schema/connection/cleanup qualification. HTTPS/WSS/forwardedidentity/SW/nativeTelegram/WebPush are not promoted to PASS. Original lifecycle resource counts/closure and30preserved containers agree with the narrative. Gallery289/newPNG0 and116/117 accepted actions are not inflated. Relative file links in the4main docs and6assembled checkpoint Markdown files resolve.

Review result: correct the one local command-order gap before final handoff publication; no new execution requested. Existing source reviews/PASS evidence are reused. Full owner-local gate remains NOT_RUN.
