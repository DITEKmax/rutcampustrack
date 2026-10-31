# Independent optional build-CA review — 947ee6dd6

PASS source review of original947ee6dd6, integrated9d99cc5ee. Reviewer root did
not author product source. Exact three-Dockerfile parent diff inspected; the
previous4445 workspace/static-source review remains separate.

Each dependency RUN mounts optional BuildKit secret proxy_ca and uses the file
only through command-local NODE_EXTRA_CA_CERTS for npm ci --strict-ssl=true.
The absent-secret branch keeps TLS verification. No ENV or COPY persists the
session CA; build/runtime inputs, lockfile, scripts and application code do not
change. Managed Docker28.4/Buildx0.31 support secret inputs. Existing builds with
no supplied secret continue through the normal branch; actual branch builds
are a separate verification gate, not proved by reading syntax.

No scanner/build/browser/Docker mutation was executed for this source review.
Actual serialized shipping-image verification is owned by root separately.
