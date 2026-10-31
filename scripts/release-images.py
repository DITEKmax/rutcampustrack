#!/usr/bin/env python3
"""Validate the fixed release inventory and bind pulled tags to build digests."""
import argparse
import json
from pathlib import Path
import re
import subprocess

PREFIX = 'ghcr.io/ditekmax/rutcampustrack/'
IMAGES = ('api-gateway', 'mobile-bff', 'auth-service', 'academic-service', 'schedule-service',
          'attendance-service', 'document-renderer-service', 'notification-web', 'notification-bot',
          'pwa-nginx', 'mini-app-nginx', 'web-panel-nginx', 'landing-nginx')


def validate(manifest, sha):
    if not re.fullmatch('[0-9a-f]{40}', sha) or set(manifest) != {'source_sha', 'images'} or manifest['source_sha'] != sha:
        raise ValueError('Manifest source/schema mismatch')
    if set(manifest['images']) != set(IMAGES):
        raise ValueError('Manifest must contain exactly 13 release images')
    for name, reference in manifest['images'].items():
        if not re.fullmatch(re.escape(PREFIX + name) + '@sha256:[0-9a-f]{64}', reference):
            raise ValueError('Invalid fixed-registry image digest: ' + name)
    return manifest


def verify_local(manifest, sha, inspect):
    for name, reference in manifest['images'].items():
        data = inspect(PREFIX + name + ':' + sha)
        if reference not in data.get('RepoDigests', []):
            raise ValueError('Pulled tag content differs from scanned build digest: ' + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest')
    parser.add_argument('sha')
    parser.add_argument('--verify-local', action='store_true')
    args = parser.parse_args()
    manifest = validate(json.loads(Path(args.manifest).read_text()), args.sha)
    if args.verify_local:
        def inspect(reference):
            return json.loads(subprocess.check_output(['docker', 'image', 'inspect', reference], text=True))[0]
        verify_local(manifest, args.sha, inspect)
    print('Validated 13 exact build digests for ' + args.sha)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, subprocess.SubprocessError) as error:
        raise SystemExit('Release images rejected: ' + str(error))
