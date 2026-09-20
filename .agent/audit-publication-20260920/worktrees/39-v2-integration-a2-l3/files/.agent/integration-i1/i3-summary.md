# I3 integration summary

Status: `RELEASED_TO_ROOT` for the bounded source correction and authorized
synthetic config-only gate.

I3 fixes the I2 HIGH finding that Docker's complete private subnet could
allocate the static trusted Nginx edge. Both production and E2E Compose IPAM
blocks now use the required `gateway` and `ip_range` inputs. The documented
nonsecret inputs are parent `172.30.0.0/24`, gateway `172.30.0.1`, edge
`172.30.0.10`, and dynamic range `172.30.0.128/25`. The tracked CI input and
production template agree.

`scripts/validate-env-prod.sh` requires both new inputs and rejects malformed,
trailing-dot, nonaligned, outside, overlapping, fixed-host-equal, and
shell-injection network values. It also checks usable parent membership,
RFC1918 containment, distinct fixed hosts, complete dynamic-range containment,
and exclusion of both fixed hosts from the dynamic interval. Configurable
private parents remain supported; no `/24` assumption was added.

The expanded product freeze contains 35 rows in
`i3-union-manifest.sha256`, including the previously omitted changed
`docker-compose.prod.yml`. Its canonical SHA256 is
`604CD4D36A70710B7C1790805DA70721B97741AB7CA0924E46BAF3850F614C6A`, with
zero file-hash mismatches. The prior I2 34-row manifest and all accepted
OpenAPI/JWT/generated artifacts remain unchanged.

Checks passed with exit 0: Git Bash syntax, 13/13 pure validator cases,
static caller/IPAM/input inventory, manifest recomputation, diff check, and
authorized synthetic Compose rendering. Rendered prod and E2E IPAM values
matched the four validated inputs. Evidence hashes and commands are in
`i3-checks.json`; bounded logs are `i3-validator.log`, `i3-static.log`, and
`i3-compose-render.log`.

The exact follow-up config selectors used were:

```text
docker compose --env-file <TEMP R5 isolated synthetic env> -f docker-compose.prod.yml config --format json
docker compose --env-file <TEMP R5 isolated synthetic env> -f docker-compose.e2e.yml config --format json
```

Only `private_net.ipam.config` and the Nginx static address were inspected.
No service started, network was created, real production env or secret was
read, and no clean-start allocation claim is made by I3. R3 remains the owner
of the separate H32 allocation evidence. A fresh independent Sol review of the
expanded union is still a parent-controlled gate.
