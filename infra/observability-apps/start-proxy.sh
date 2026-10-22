#!/bin/sh
set -eu
# Same operator Basic authentication contract as the existing ingress.
# Check the mounted file without logging usernames, hashes or secret values.
[ -s /run/secrets/observer-htpasswd ] || { echo 'Operator password file is required' >&2; exit 1; }
if ! grep -Eq '^[A-Za-z0-9_.-]+:\$(2[aby]\$|apr1\$)' /run/secrets/observer-htpasswd; then
  echo 'Operator password file has an unsupported hash format' >&2
  exit 1
fi
cp /run/secrets/observer-htpasswd /tmp/observer.htpasswd
chmod 444 /tmp/observer.htpasswd
exec nginx -g 'daemon off;'
