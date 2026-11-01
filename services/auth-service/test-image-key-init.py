#!/usr/bin/env python3
"""Focused, offline image verification. Only creates/removes nonce-owned resources.

Run on a Docker host with Python 3 and javac 21:
  python3 services/auth-service/test-image-key-init.py IMAGE EVIDENCE_DIRECTORY
No key bytes or JWT tokens are emitted; all key material is synthetic in owned volumes.
"""

import json
import os
import subprocess
import sys
import time
import uuid
from pathlib import Path


def main():
    image, output = sys.argv[1:]
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    nonce = "rct-auth-key-" + uuid.uuid4().hex[:12]
    volumes, containers, checks = [], [], []

    def command(args, success=True, timeout=30):
        result = subprocess.run(args, check=False, text=True, capture_output=True, timeout=timeout)
        if success and result.returncode:
            raise RuntimeError(f"Command failed ({result.returncode}): {args[:3]}: {result.stderr}")
        return result

    def volume(suffix):
        name = nonce + "-" + suffix
        command(["docker", "volume", "create", name])
        volumes.append(name)
        return name

    def run(vol, args, *, entrypoint="/usr/local/bin/auth-entrypoint", readonly=False, extra=(), success=True):
        name = nonce + "-run" + str(len(containers))
        containers.append(name)
        cmd = [
            "docker",
            "run",
            "--rm",
            "--name",
            name,
            "--network",
            "none",
            "--memory",
            "256m",
            "--cpus",
            "1",
            "--mount",
            f"type=volume,src={vol},dst=/keys" + (",readonly" if readonly else ""),
        ]
        if readonly:
            cmd += ["--read-only"]
        if entrypoint:
            cmd += ["--entrypoint", entrypoint]
        cmd += list(extra) + [image] + list(args)
        return command(cmd, success=success)

    def identity(vol):
        return run(vol, ["-ec", "sha256sum /keys/private.key /keys/public.key /keys/kid.txt"], entrypoint="sh").stdout

    def clone(suffix):
        vol = volume(suffix)
        run(
            vol,
            ["-ec", "cp /seed/private.key /seed/public.key /seed/kid.txt /keys/"],
            entrypoint="sh",
            extra=["--mount", f"type=volume,src={first},dst=/seed,readonly"],
        )
        return vol

    def rejected(vol, reason, extra=()):
        before = run(vol, ["-ec", "find /keys -type f -exec sha256sum '{}' ';' | sort"], entrypoint="sh").stdout
        result = run(vol, ["true"], extra=extra, success=False)
        assert result.returncode != 0 and "JWT key initialization refused:" in result.stderr, reason
        after = run(vol, ["-ec", "find /keys -type f -exec sha256sum '{}' ';' | sort"], entrypoint="sh").stdout
        assert before == after, reason + " changed key files"
        checks.append(reason)

    try:
        # The unmounted image, including its copy-up directory, has no signing files.
        inspect_name = nonce + "-image"
        containers.append(inspect_name)
        command(
            [
                "docker",
                "run",
                "--rm",
                "--name",
                inspect_name,
                "--network",
                "none",
                "--entrypoint",
                "sh",
                image,
                "-ec",
                'test -z "$(ls -A /keys)"; test "$(stat -c %u:%g /keys)" = 100:101; test "$(id -u)" = 100; id -G | tr \' \' \'\\n\' | grep -qx 10004',
            ]
        )
        checks.append("image_empty_nonroot_copyup_tls_group")
        first, second = volume("fresh1"), volume("fresh2")
        started = time.monotonic()
        run(
            first,
            [
                "sh",
                "-ec",
                'test "$JWT_KEY_DIR" = /keys; test "$(stat -c %a /keys/private.key)" = 600; test "$(stat -c %a /keys/kid.txt)" = 600; test "$(stat -c %a /keys/public.key)" = 644',
            ],
        )
        fresh_seconds = round(time.monotonic() - started, 3)
        assert fresh_seconds < 20, "fresh initialization exceeded bounded startup"
        original = identity(first)
        run(second, ["true"])
        second_identity = identity(second)
        assert original.splitlines()[0] != second_identity.splitlines()[0], "fresh private keys must differ"
        assert original.splitlines()[1] != second_identity.splitlines()[1], "fresh public keys must differ"
        checks.append("fresh_independent_keys_rsa3072_bounded_startup")
        run(first, ["true"])
        assert identity(first) == original
        run(first, ["true"], readonly=True)
        assert identity(first) == original
        checks += ["restart_preserves_identity", "readonly_rootfs_and_complete_mount_preserve_identity"]

        # Default image argv must retain the prior Java/class entrypoint semantics.
        shim = output / "argv-shim"
        shim.mkdir(exist_ok=True)
        (shim / "java").write_text(
            """#!/bin/sh
test "$1" = org.springframework.boot.loader.launch.JarLauncher || exit 1
shift
test "$#" = "$EXPECTED_ARGS" || exit 1
if [ "$#" = 1 ]; then test "$1" = --spring.profiles.active=synthetic || exit 1; fi
printf 'ARGV_PASS\\n'
""",
            encoding="utf-8",
        )
        (shim / "java").chmod(0o755)
        for args in [[], ["--spring.profiles.active=synthetic"]]:
            forwarded = run(
                first,
                args,
                entrypoint=None,
                readonly=True,
                extra=[
                    "--env",
                    "PATH=/argv-shim:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                    "--env",
                    f"EXPECTED_ARGS={len(args)}",
                    "--mount",
                    f"type=bind,src={shim},dst=/argv-shim,readonly",
                ],
            )
            assert forwarded.stdout.strip() == "ARGV_PASS"
        checks.append("default_Java_class_and_Spring_argument_forwarding")

        # Run the real production JwtService parser, then sign with its loaded key.
        probe = output / "probe"
        probe.mkdir(exist_ok=True)
        (probe / "JwtKeyProbe.java").write_text(
            """
import java.lang.reflect.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
public class JwtKeyProbe {
  public static void main(String[] args) throws Exception {
    if ("signal".equals(args[0])) { System.out.println("SIGNAL_READY"); Thread.sleep(300000); return; }
    if (!"option-ok".equals(System.getProperty("auth.probe")) || !"argument-ok".equals(args[0])) throw new AssertionError();
    Class<?> props = Class.forName("ru.rutcampustrack.auth.config.JwtProperties");
    Object config = props.getConstructor(String.class, long.class, long.class).newInstance(System.getenv("JWT_KEY_DIR"), 60000L, 60000L);
    Class<?> service = Class.forName("ru.rutcampustrack.auth.service.JwtService");
    Object jwt = service.getConstructor(props, Class.forName("org.springframework.data.redis.core.StringRedisTemplate")).newInstance(config, null);
    service.getMethod("init").invoke(jwt);
    Field priv = service.getDeclaredField("privateKey"), pub = service.getDeclaredField("publicKey");
    priv.setAccessible(true); pub.setAccessible(true);
    PrivateKey privateKey = (PrivateKey) priv.get(jwt);
    PublicKey publicKey = (PublicKey) pub.get(jwt);
    if (((RSAPublicKey) publicKey).getModulus().bitLength() != 3072) throw new AssertionError();
    byte[] message = "synthetic-runtime-key-check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(privateKey); signature.update(message); byte[] signed = signature.sign();
    signature.initVerify(publicKey); signature.update(message);
    if (!signature.verify(signed)) throw new AssertionError();
    Class<?> userType = Class.forName("ru.rutcampustrack.auth.entity.User");
    Object user = userType.getConstructor().newInstance();
    Field userId = userType.getDeclaredField("id"); userId.setAccessible(true); userId.set(user, 424242L);
    String token = (String) service.getMethod("generateRefreshToken", userType).invoke(jwt, user);
    Object parsed = service.getMethod("parseRefreshToken", String.class).invoke(jwt, token);
    Class<?> jwtType = Class.forName("io.jsonwebtoken.Jwt");
    java.util.Map<?, ?> claims = (java.util.Map<?, ?>) jwtType.getMethod("getPayload").invoke(parsed);
    java.util.Map<?, ?> header = (java.util.Map<?, ?>) jwtType.getMethod("getHeader").invoke(parsed);
    if (!"424242".equals(claims.get("sub")) || !"refresh".equals(claims.get("token_use")) ||
        !service.getMethod("getSigningKeyId").invoke(jwt).equals(header.get("kid"))) throw new AssertionError();
    System.out.println("JWT_KEY_PROBE_PASS");
  }
}
""",
            encoding="utf-8",
        )
        command(["javac", "--release", "21", str(probe / "JwtKeyProbe.java")])
        parsed = run(
            first,
            ["java", "-cp", "/probe:/app/BOOT-INF/classes:/app/BOOT-INF/lib/*", "JwtKeyProbe", "argument-ok"],
            readonly=True,
            extra=[
                "--env",
                "JAVA_TOOL_OPTIONS=-Dauth.probe=option-ok",
                "--mount",
                f"type=bind,src={probe},dst=/probe,readonly",
            ],
        )
        assert "JWT_KEY_PROBE_PASS" in parsed.stdout
        assert identity(first) == original
        checks.append("actual_JwtService_PKCS8_X509_RSA3072_signature_refresh_token_roundtrip_JVM_options_args")

        uuidkid = clone("uuid")
        run(uuidkid, ["-ec", "printf '%s\\n' 01234567-89ab-cdef-0123-456789abcdef > /keys/kid.txt"], entrypoint="sh")
        uuid_identity = identity(uuidkid)
        run(uuidkid, ["true"], readonly=True)
        assert identity(uuidkid) == uuid_identity
        checks.append("production_UUID_kid_readonly")
        mutations = {
            "missing_kid": "rm /keys/kid.txt",
            "partial_private_only": "rm /keys/public.key /keys/kid.txt",
            "symlink_private": "mv /keys/private.key /keys/original; ln -s original /keys/private.key",
            "symlink_marker": "ln -s /nonexistent /keys/.jwt-initializing",
            "stale_marker": "mkdir /keys/.jwt-initializing",
            "bad_PEM": "printf invalid > /keys/private.key",
            "unreadable_private": "chmod 000 /keys/private.key",
            "directory_instead_of_key": "rm /keys/kid.txt; mkdir /keys/kid.txt",
            "invalid_kid": "printf 'invalid id' > /keys/kid.txt",
        }
        for reason, mutation in mutations.items():
            vol = clone(reason)
            run(vol, ["-ec", mutation], entrypoint="sh")
            rejected(vol, reason)
        mismatched = clone("mismatch")
        run(
            mismatched,
            ["-ec", "cp /other/public.key /keys/public.key"],
            entrypoint="sh",
            extra=["--mount", f"type=volume,src={second},dst=/other,readonly"],
        )
        rejected(mismatched, "inconsistent_keypair")
        failing_crypto = output / "failing-crypto"
        failing_crypto.mkdir(exist_ok=True)
        (failing_crypto / "openssl").write_text(
            """#!/bin/sh
if [ "$1" = pkey ]; then exit 1; fi
exec /usr/bin/openssl "$@"
""",
            encoding="utf-8",
        )
        (failing_crypto / "openssl").chmod(0o755)
        rejected(
            mismatched,
            "crypto_pipeline_failure_rejected",
            extra=[
                "--env",
                "PATH=/failing-crypto:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "--mount",
                f"type=bind,src={failing_crypto},dst=/failing-crypto,readonly",
            ],
        )
        keydirlink = clone("keydirlink")
        rejected(keydirlink, "symlink_keydir", extra=["--env", "JWT_KEY_DIR=/var/run"])

        # Deterministic concurrency and interruption: pause only an owned container.
        control = output / "control"
        control.mkdir(exist_ok=True)
        wrappers = output / "wrappers"
        wrappers.mkdir(exist_ok=True)
        (wrappers / "openssl").write_text(
            """#!/bin/sh
if [ "$1" = genpkey ]; then
  touch /control/generating
  while [ ! -e /control/release ]; do sleep 0.1; done
fi
exec /usr/bin/openssl "$@"
""",
            encoding="utf-8",
        )
        (wrappers / "ln").write_text(
            """#!/bin/sh
/bin/ln "$@" || exit $?
case "$2" in */private.key)
  touch /control/published
  while :; do sleep 1; done ;;
esac
""",
            encoding="utf-8",
        )
        for wrapper in wrappers.iterdir():
            wrapper.chmod(0o755)
        os.chmod(control, 0o777)

        def background(vol, suffix, use_wrappers=True):
            name = nonce + "-" + suffix
            containers.append(name)
            cmd = [
                "docker",
                "run",
                "-d",
                "--name",
                name,
                "--network",
                "none",
                "--memory",
                "256m",
                "--cpus",
                "1",
                "--entrypoint",
                "/usr/local/bin/auth-entrypoint",
                "--mount",
                f"type=volume,src={vol},dst=/keys",
            ]
            if use_wrappers:
                cmd += [
                    "--env",
                    "PATH=/wrappers:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                    "--mount",
                    f"type=bind,src={control},dst=/control",
                    "--mount",
                    f"type=bind,src={wrappers},dst=/wrappers,readonly",
                ]
                args = ["sleep", "300"]
            else:
                cmd += ["--mount", f"type=bind,src={probe},dst=/probe,readonly"]
                args = ["java", "-cp", "/probe", "JwtKeyProbe", "signal"]
            command(cmd + [image] + args)
            return name

        def await_file(path):
            until = time.monotonic() + 15
            while not path.exists():
                assert time.monotonic() < until, "controlled initializer did not reach checkpoint"
                time.sleep(0.1)

        race = volume("race")
        active = background(race, "race")
        await_file(control / "generating")
        rejected(race, "concurrent_initializer_rejected")
        (control / "release").touch()
        await_file(control / "published")
        command(["docker", "kill", "--signal", "KILL", active])
        command(["docker", "wait", active])
        rejected(race, "interrupted_partial_publication_rejected")
        assert (
            run(
                race,
                ["-ec", "test -f /keys/private.key; test ! -e /keys/public.key; test -d /keys/.jwt-initializing"],
                entrypoint="sh",
            ).returncode
            == 0
        )
        signal = background(first, "signal", use_wrappers=False)
        until = time.monotonic() + 15
        while True:
            process = command(["docker", "exec", signal, "cat", "/proc/1/comm"]).stdout.strip()
            if process == "java" and "SIGNAL_READY" in command(["docker", "logs", signal]).stdout:
                break
            assert time.monotonic() < until, "entrypoint did not exec command"
            time.sleep(0.1)
        command(["docker", "kill", "--signal", "TERM", signal])
        assert command(["docker", "wait", signal]).stdout.strip() == "143"
        checks.append("exec_PID1_SIGTERM")
        result = {"status": "PASS", "image": image, "nonce": nonce, "fresh_seconds": fresh_seconds, "checks": checks}
        (output / "runtime.safe.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result))
    finally:
        for name in reversed(containers):
            command(["docker", "rm", "-f", name], success=False)
        for name in reversed(volumes):
            command(["docker", "volume", "rm", name], success=False)
        remaining_containers = [
            name
            for name in containers
            if command(["docker", "container", "inspect", name], success=False).returncode == 0
        ]
        remaining_volumes = [
            name for name in volumes if command(["docker", "volume", "inspect", name], success=False).returncode == 0
        ]
        (output / "resources.safe.json").write_text(
            json.dumps(
                {
                    "nonce": nonce,
                    "containers": containers,
                    "volumes": volumes,
                    "remaining_containers": remaining_containers,
                    "remaining_volumes": remaining_volumes,
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        assert not remaining_containers and not remaining_volumes, "Owned resource cleanup incomplete"


if __name__ == "__main__":
    main()
