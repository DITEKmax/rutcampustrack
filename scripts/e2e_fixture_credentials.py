"""Fresh private credentials for the disposable CI/local e2e Compose stack."""

import base64
import json
import secrets
import subprocess


def fresh_credentials():
    """Return ephemeral Mongo/VAPID values; never print or persist key material."""
    program = """
const crypto = require('node:crypto');
const curve = crypto.createECDH('prime256v1');
curve.generateKeys();
const privateKey = Buffer.from(curve.getPrivateKey().toString('hex').padStart(64, '0'), 'hex');
process.stdout.write(JSON.stringify({
  VAPID_PUBLIC_KEY: curve.getPublicKey().toString('base64url'),
  VAPID_PRIVATE_KEY: privateKey.toString('base64url')
}));
"""
    try:
        result = subprocess.run(
            ['node', '--eval', program], capture_output=True, timeout=10, check=False)
        if result.returncode or len(result.stdout) > 1024:
            raise ValueError
        vapid = json.loads(result.stdout)
        if type(vapid) is not dict or set(vapid) != {'VAPID_PUBLIC_KEY', 'VAPID_PRIVATE_KEY'}:
            raise ValueError
        for name, size in [('VAPID_PUBLIC_KEY', 65), ('VAPID_PRIVATE_KEY', 32)]:
            value = vapid[name]
            if type(value) is not str or not value.isascii():
                raise ValueError
            raw = base64.b64decode(value + '=' * (-len(value) % 4), altchars=b'-_', validate=True)
            if (len(raw) != size
                    or base64.urlsafe_b64encode(raw).decode().rstrip('=') != value
                    or name == 'VAPID_PUBLIC_KEY' and raw[0] != 4):
                raise ValueError
        return {'MONGODB_REPLICA_SET_KEY': base64.b64encode(secrets.token_bytes(48)).decode(),
                **vapid}
    except Exception:
        raise RuntimeError('Disposable e2e credential generation failed') from None
