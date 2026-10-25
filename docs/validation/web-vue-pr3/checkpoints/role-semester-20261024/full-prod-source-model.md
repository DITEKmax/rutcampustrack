# Full PROD Compose source-model check — STOP

ROOT source0421 remained frozen. One actual `docker compose --env-file /workspace/rct-pr3-setup/private/synthetic.env -p rct-pr3-release-inputs -f docker-compose.prod.yml config --no-env-resolution --quiet` invocation used the existing synthetic env bindings plus CAMPUS_MAP_USAGE_HMAC_KEY obtained only from the exact own nonproduction Academic materialized JSON property and added to the child environment in memory. Inherited environment/DOCKER_CONFIG/proxy/TLS settings were preserved; no values or rendered configuration were printed.

Native Compose exit1: the next required binding is ACADEMIC_GRPC_TLS_AUTHORITY (TLS authority configuration). STOP as assigned: no other binding lookup/search, fake value, repeat, production env access or environment mutation. Source model is NOT PASS. Production environment, actual deployment/runtime and service env-file resolution remain unvalidated.

The first Python preflight assumed a nested JSON shape and stopped at KeyError before invoking Compose. A corrected exact dotted-key lookup then performed the sole Compose invocation. This was a local input-shape correction, not a repeated validation or workaround for missing configuration.

receipt.json contains scope, command, native exit, safe missing-variable classification and input hashes. Raw stderr was captured only in stderr.private.txt with mode0600 and not printed. Root source/author WT unchanged; no browser, API/domain/DB, container/build/up/image, CI/deploy/push or main stand action occurred.

Compose source SHA256: 96da38adf3b78fbcf3d321aaa84a8bae52a9e17797c12009623cb0a2e0b205f0
Owned materialized input SHA256: [private binding digest omitted]
Receipt SHA256: 84256456136e31dcb539ff71e815673d10e85754cd18ad6d1c2d198a250f40be
