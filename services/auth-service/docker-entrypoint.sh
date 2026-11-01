#!/bin/sh
set -eu
# Alpine ash supports pipefail: a failed crypto operation must never hash as empty.
set -o pipefail
umask 077

fail() {
    echo "JWT key initialization refused: $1" >&2
    exit 1
}

key_dir=${JWT_KEY_DIR:-/keys}
export JWT_KEY_DIR="$key_dir"
[ -d "$key_dir" ] && [ ! -L "$key_dir" ] || fail "key directory must be an existing regular directory"
marker="$key_dir/.jwt-initializing"
[ ! -e "$marker" ] && [ ! -L "$marker" ] || fail "initialization marker exists; explicit operator recovery required"

check_files() {
    count=0
    for name in private.key public.key kid.txt; do
        path="$key_dir/$name"
        [ ! -L "$path" ] || fail "symlink key file"
        if [ -e "$path" ]; then
            [ -f "$path" ] && [ -r "$path" ] && [ -s "$path" ] || fail "unreadable or invalid key file"
            count=$((count + 1))
        fi
    done
    [ "$count" = 0 ] || [ "$count" = 3 ] || fail "partial keyset; refusing implicit rotation"
}

validate() {
    # Match JwtService's PKCS8EncodedKeySpec / X509EncodedKeySpec RSA parsers.
    openssl pkcs8 -in "$key_dir/private.key" -nocrypt -out /dev/null 2>/dev/null || fail "invalid PKCS8 private key"
    openssl rsa -in "$key_dir/private.key" -check -noout >/dev/null 2>&1 || fail "invalid RSA private key"
    openssl rsa -pubin -in "$key_dir/public.key" -noout >/dev/null 2>&1 || fail "invalid RSA public key"
    private_public=$(openssl pkey -in "$key_dir/private.key" -pubout -outform DER 2>/dev/null | sha256sum) || fail "cannot derive public key fingerprint"
    supplied_public=$(openssl pkey -pubin -in "$key_dir/public.key" -outform DER 2>/dev/null | sha256sum) || fail "cannot read public key fingerprint"
    [ "$private_public" = "$supplied_public" ] || fail "inconsistent RSA key pair"
    kid=$(cat "$key_dir/kid.txt")
    [ -n "$kid" ] && [ "${#kid}" -le 128 ] || fail "invalid key identifier"
    case "$kid" in *[!A-Za-z0-9._-]*) fail "invalid key identifier" ;; esac
}

check_files
if [ "$count" = 0 ]; then
    # mkdir is the cross-container lock. Never remove an interrupted marker:
    # a partial publication must require explicit recovery, never key rotation.
    mkdir -m 700 "$marker" 2>/dev/null || fail "another initializer or interrupted initialization exists"
    trap 'exit 143' TERM
    trap 'exit 130' INT
    check_files
    [ "$count" = 0 ] || fail "keyset appeared during initialization"
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$marker/private.key" 2>/dev/null || fail "RSA generation failed"
    openssl pkey -in "$marker/private.key" -pubout -out "$marker/public.key" 2>/dev/null || fail "public key generation failed"
    openssl rand -hex 16 > "$marker/kid.txt" || fail "key identifier generation failed"
    chmod 600 "$marker/private.key" "$marker/kid.txt"
    chmod 644 "$marker/public.key"
    # Hard links cannot replace a file adopted by a concurrent initializer.
    for name in private.key public.key kid.txt; do
        ln "$marker/$name" "$key_dir/$name" || fail "key publication conflict"
    done
    validate
    rm "$marker/private.key" "$marker/public.key" "$marker/kid.txt"
    rmdir "$marker"
    trap - TERM INT
else
    validate
fi
# A concurrent/interrupted writer may not turn a complete-looking set into readiness.
[ ! -e "$marker" ] && [ ! -L "$marker" ] || fail "initialization marker exists"
exec "$@"
