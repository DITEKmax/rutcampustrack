#!/usr/bin/env python3
"""Bounded, read-only GitHub quality gate for one exact release revision."""
import argparse
import json
import os
from pathlib import Path
import re
import time
import urllib.request

SERVICES = ('api-gateway', 'auth-service', 'academic-service', 'schedule-service',
            'attendance-service', 'notification-service', 'mobile-bff')
REQUIRED = {
    'ci.yml': {*(f'Java unit — {s}' for s in SERVICES),
               *(f'Java IT — {s}' for s in SERVICES),
               'Java verification (verifyNoDebugInProd + verifyLogbackJsonInAllServices)',
               'Python Lint & Test', 'Vue workspace — WEB, PWA, TMA', 'ShellCheck scripts',
               'Grafana dashboards structure', 'Synthetic database and file recovery',
               'Playwright E2E auth flow (client)', 'Playwright E2E auth flow (lifecycle)',
               'Playwright E2E auth flow',
               'Monitoring — formulas, delivery, stores and Grafana pipeline',
               'Isolated GlitchTip, Java CPU profiles and protected operator links'},
    'coverage.yml': {*(f'Java coverage — {s}' for s in SERVICES),
                     'Vue coverage — WEB, PWA, TMA, mobile-core, shared',
                     'Python coverage — notification-bot'},
    'security.yml': {'Trivy (filesystem)', 'Trivy (config — Dockerfile + compose)',
                     'Gitleaks (secrets)'},
    'openapi-drift.yml': {'OpenAPI ↔ generated types drift'},
}
OPTIONAL_SKIPS = {'coverage.yml': {'Diff coverage report'},
                  'security.yml': {'Trivy (built images)'}}


class GateFailure(Exception):
    pass


class Pending(Exception):
    pass


def assess(workflow, runs, jobs_for_run, sha, repository):
    """Latest matching run wins; no fallback to an older green result."""
    if not runs:
        raise Pending(f'{workflow}: no run for source SHA')
    run = max(runs, key=lambda value: (value['run_number'], value['id']))
    if (run.get('head_sha') != sha or
            (run.get('head_repository') or {}).get('full_name', '').lower() != repository.lower() or
            not re.fullmatch(re.escape('.github/workflows/' + workflow) + r'(?:@[A-Za-z0-9_./-]+)?', run.get('path', '')) or
            run.get('event') not in ('push', 'pull_request', 'workflow_dispatch')):
        raise GateFailure(f'{workflow}: wrong source/repository/workflow/event')
    if run.get('status') != 'completed':
        raise Pending(f'{workflow}: run {run["id"]} is {run.get("status")}')
    if run.get('conclusion') != 'success':
        raise GateFailure(f'{workflow}: latest run {run["id"]} is {run.get("conclusion")}')
    jobs = jobs_for_run(run['id'])
    names = [job['name'] for job in jobs]
    required = set(REQUIRED[workflow])
    optional = set(OPTIONAL_SKIPS.get(workflow, set()))
    if workflow == 'coverage.yml' and run['event'] == 'pull_request':
        required.add('Diff coverage report')
        optional.remove('Diff coverage report')
    if len(names) != len(set(names)) or not required.issubset(names) or set(names) - required - optional:
        raise GateFailure(f'{workflow}: missing, duplicate or unknown jobs in run {run["id"]}')
    for job in jobs:
        allowed = ('success', 'skipped') if job['name'] in optional else ('success',)
        if job.get('head_sha') != sha or job.get('status') != 'completed' or job.get('conclusion') not in allowed:
            raise GateFailure(f'{workflow}: job {job["name"]} is not an accepted same-SHA success')
    return {'workflow': workflow, 'run_id': run['id'], 'run_attempt': run['run_attempt'],
            'head_sha': sha, 'required_jobs': len(required), 'conclusion': 'success'}


class GitHub:
    def __init__(self, repository, token):
        self.base = 'https://api.github.com/repos/' + repository
        self.token = token

    def get(self, path):
        request = urllib.request.Request(self.base + path, headers={
            'Accept': 'application/vnd.github+json', 'Authorization': 'Bearer ' + self.token,
            'X-GitHub-Api-Version': '2022-11-28'})
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)

    def pages(self, path, key):
        values = []
        for page in range(1, 101):
            batch = self.get(path + ('&' if '?' in path else '?') + f'per_page=100&page={page}')[key]
            values.extend(batch)
            if len(batch) < 100:
                return values
        raise GateFailure('GitHub pagination limit reached')


def require_apply(sha, current_main, enabled):
    if enabled != 'true' or sha != current_main:
        raise GateFailure('Apply requires production gate=true and exact current main SHA')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sha', required=True)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--wait-seconds', type=int, default=1800)
    parser.add_argument('--output', default='release-quality.json')
    args = parser.parse_args()
    if not re.fullmatch('[0-9a-f]{40}', args.sha) or not re.fullmatch('[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', args.repository):
        parser.error('Exact SHA and owner/repository are required')
    if not 0 <= args.wait_seconds <= 3600:
        parser.error('wait-seconds must be between 0 and 3600')
    api = GitHub(args.repository, os.environ['GH_TOKEN'])
    deadline = time.monotonic() + args.wait_seconds
    while True:
        receipts, pending = [], []
        if args.apply:
            require_apply(args.sha, api.get('/git/ref/heads/main')['object']['sha'],
                          os.environ.get('PRODUCTION_DEPLOY_ENABLED', 'false'))
        for workflow in REQUIRED:
            runs = api.pages('/actions/workflows/' + workflow + '/runs?head_sha=' + args.sha, 'workflow_runs')
            try:
                receipts.append(assess(workflow, runs,
                    lambda run_id: api.pages(f'/actions/runs/{run_id}/jobs?filter=latest', 'jobs'),
                    args.sha, args.repository))
            except Pending as error:
                pending.append(str(error))
        if not pending:
            Path(args.output).write_text(json.dumps({'source_sha': args.sha, 'checks': receipts}, indent=2) + '\n')
            print(json.dumps(receipts))
            return
        print('; '.join(pending), flush=True)
        if time.monotonic() >= deadline:
            raise GateFailure('Bounded wait expired; release quality is NOT confirmed')
        time.sleep(min(20, max(0, deadline - time.monotonic())))


if __name__ == '__main__':
    try:
        main()
    except (GateFailure, OSError, KeyError, ValueError) as error:
        raise SystemExit('Release gate rejected: ' + str(error))
