"""Explicit identities shared by CI and the foreground local release checks."""
import json
import os
from pathlib import Path
import re
import stat
import subprocess


def source_context(environ=None, head=None):
    env = os.environ if environ is None else environ
    if env.get('RCT_LOCAL_RELEASE_REVISION'):
        if any(env.get(k) for k in ('GITHUB_ACTIONS', 'GITHUB_SHA', 'GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT')):
            raise ValueError('Local checks require an honest LOCAL_CONTROL context')
        revision = env['RCT_LOCAL_RELEASE_REVISION']
        nonce = env.get('RCT_LOCAL_RELEASE_NONCE', '')
        if re.fullmatch('[a-f0-9]{40}', revision) is None or re.fullmatch('[a-f0-9]{24}', nonce) is None:
            raise ValueError('Exact local source and fresh nonce required')
        audit = Path(env['RCT_LOCAL_RELEASE_AUDIT'])
        info = audit.lstat()
        if not audit.is_absolute() or not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
            raise ValueError('Private local audit directory required')
        if head is None:
            head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True, timeout=10).strip()
        if head != revision:
            raise ValueError('Local source revision changed')
        return {'event': 'LOCAL_CONTROL', 'checkoutSha': revision, 'producerHeadSha': revision,
                'runId': None, 'runAttempt': None, 'runIdentity': 'local-' + nonce}
    if env.get('GITHUB_ACTIONS') != 'true':
        raise ValueError('Choose the explicit local release entry or isolated CI')
    revision = env['GITHUB_SHA']
    run, attempt = env['GITHUB_RUN_ID'], env['GITHUB_RUN_ATTEMPT']
    if re.fullmatch('[a-f0-9]{40}', revision) is None or not run.isdecimal() or not attempt.isdecimal():
        raise ValueError('Exact CI identity required')
    return {'event': env['GITHUB_EVENT_NAME'], 'checkoutSha': revision,
            'producerHeadSha': env.get('RCT_CI_PR_HEAD_SHA', revision),
            'runId': run, 'runAttempt': attempt, 'runIdentity': run + '-' + attempt}


def runtime_context(marker, environ=None):
    env = os.environ if environ is None else environ
    context = source_context(env)
    expected = 'isolated-local' if context['event'] == 'LOCAL_CONTROL' else 'isolated-ci'
    if env.get(marker) != expected:
        raise ValueError('Isolated synthetic runtime lease required')
    return context


if __name__ == '__main__':
    import sys
    if sys.argv[1:] != ['revision']:
        raise SystemExit('Only the shared revision context is supported')
    print(source_context()['producerHeadSha'])
