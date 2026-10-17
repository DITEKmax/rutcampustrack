#!/usr/bin/env python3
"""Bounded source checks only; never starts Node, Docker, a browser or an API client."""
import hashlib
import json
import re
import subprocess
from pathlib import Path
import yaml

BASE = 'db177beeb9660f5a17d7fadf660e024e28f9f4b9'
WT = Path('/workspace/rct-worktrees/client-ci-first3')
OLD = Path('/workspace/scratch/rct-validation/client_real_acceptance/focused3-preparation-5ced-r1')
OUT = Path(__file__).parent
checks = {}
def check(name, condition):
    checks[name] = bool(condition)
    if not condition:
        raise RuntimeError('Closed source check failed: ' + name)
def base(path):
    return subprocess.check_output(['git', '-C', str(WT), 'show', f'{BASE}:{path}'])
def data(path):
    return (WT / path).read_bytes()
def digest(value):
    return hashlib.sha256(value).hexdigest()

workflow_path = '.github/workflows/ci.yml'
before = base(workflow_path)
after = data(workflow_path)
a = yaml.safe_load(before)
b = yaml.safe_load(after)
old_job = a['jobs'].pop('e2e-auth')
new_job = b['jobs'].pop('e2e-auth')
check('triggers_other_jobs_unchanged', a == b)
check('prefix_before_e2e_byte_equal', before.split(b'  e2e-auth:')[0] == after.split(b'  e2e-auth:')[0])
check('runner_job_timeout_preserved', all(old_job[k] == new_job[k] for k in ['name', 'runs-on', 'timeout-minutes']))
def named(job, name):
    return next(step for step in job['steps'] if step.get('name') == name)
for name in ['Generate self-signed TLS cert', 'Link synthetic Telegram teacher in the ephemeral database', 'Install Playwright + browsers', 'Run auth-flow @smoke specs (chromium only)', 'Stop isolated e2e containers']:
    check('unchanged_step_' + str(len(checks)), named(old_job, name) == named(new_job, name))
check('all_old_action_steps_preserved', all(step in new_job['steps'] for step in old_job['steps'] if 'uses' in step and 'upload-artifact' not in step['uses']))
boot = named(new_job, 'Boot isolated Vue auth stack')['run']
check('one_full_stack_boot_same_timeout', boot.count('up -d --build --wait --wait-timeout 480') == 1 and '-f tests/e2e/infra/docker-compose.clock-fixture.yml' in boot)
overlay = yaml.safe_load(data('tests/e2e/infra/docker-compose.clock-fixture.yml'))
check('educational_only_clock_overlay', set(overlay['services']) == {'academic-service', 'schedule-service', 'attendance-service', 'mobile-bff'})
compose = yaml.safe_load(data('docker-compose.e2e.yml'))
check('real_full_16_service_graph', len(compose['services']) == 16)
prepare = named(new_job, 'Prepare disposable FIRST3 clock and private artifacts')['run']
check('private_paths_before_boot_and_seed', 'umask 077' in prepare and prepare.count('mkdir -m 700') == 2 and new_job['steps'].index(named(new_job, 'Prepare disposable FIRST3 clock and private artifacts')) < new_job['steps'].index(named(new_job, 'Boot isolated Vue auth stack')))
check('existing_jar_and_control_no_new_clock', 'e2eClockFixtureJar e2eClockFixtureControl --no-daemon --max-workers=1' in prepare and 'backend-clock/prepare-clock.mjs' in prepare)
check('finite_first3_browser_fixed_time_only', data('tests/e2e/first3-specs/mobile-first3.spec.ts').decode().count('await page.clock.setFixedTime(teachingInstant);') == 1 and 'clock.install' not in data('tests/e2e/playwright.first3.config.ts').decode())
check('mandatory_first3_no_failure_suppression', 'continue-on-error' not in named(new_job, 'Run accepted FIRST3 with closed diagnostics (chromium only)') and not new_job.get('continue-on-error'))

expected_uploads = ['${{ env.RCT_FOCUS3_RUN }}/artifacts/safe-results.json', *['${{ env.RCT_FOCUS3_RUN }}/supervision/observations-' + role + '.safe.json' for role in ['student', 'headman', 'helper']]]
def private_uploads(job):
    uploads = [s for s in job['steps'] if s.get('uses', '').startswith('actions/upload-artifact@')]
    return len(uploads) == 1 and uploads[0]['with']['path'].splitlines() == expected_uploads
check('only_canonical_four_json_upload', private_uploads(new_job))
check('old_raw_upload_counterexample_rejected', not private_uploads(old_job))
negative_uploads = ['tests/e2e/playwright-report/', 'docker-logs/', '${{ env.RCT_FOCUS3_RUN }}/**', '${{ env.RCT_FOCUS3_RUN }}/artifacts/error-context.md']
for i, path in enumerate(negative_uploads):
    mutated = json.loads(json.dumps(new_job))
    next(s for s in mutated['steps'] if s.get('uses', '').startswith('actions/upload-artifact@'))['with']['path'] = path
    check('unsafe_upload_negative_' + str(i), not private_uploads(mutated))
check('no_raw_docker_dump', not any(' logs ' in s.get('run', '') for s in new_job['steps']))

observer = 'tests/e2e/fixtures/first3-observer.ts'
original_observer = (OLD / 'observer.ts').read_bytes()
portable = original_observer.replace(b"'/workspace/rutcampustrack/tests/e2e/node_modules/@playwright/test'", b"'@playwright/test'").replace(b"'/workspace/rutcampustrack/tests/e2e/fixtures/mobile-dataset'", b"'./mobile-dataset'")
check('observer_only_two_import_substitutions', data(observer) == portable)
check('observer_old_exact_reviewed_hash', digest(original_observer) == '4650d61052bbc6f5bb7171e529389da11331898e8aecdd4da807c69a2e26df40')
check('observer_cancellation_redirect_controls_retained', all(token in portable.decode() for token in ['maxRedirects: 0', 'maxRetries: 0', 'this.token = undefined', 'await this.closeDiagnosticContext()', 'if (this.stopped || this.ended) return;']))
def wrapper_body(text):
    text = text[text.index('let observer:'):]
    text = text.replace('async ({ page, dataset, teachingInstant }, info)', 'async ({ page, dataset }, info)')
    text = text.replace("  if (!teachingInstant) throw new Error('FIRST3 teaching instant is required');\n", '')
    text = text.replace('  // Same educational day as the dataset; real timers and backend security clocks stay enabled.\n', '')
    text = text.replace('  await page.clock.setFixedTime(teachingInstant);\n', '')
    text = re.sub(r'^//[^\n]*\n', '', text, flags=re.M)
    return text.replace("'/workspace/rutcampustrack/tests/e2e/specs/mobile-real-server.spec.ts'", "'../specs/mobile-real-server.spec.ts'")
check('same_wrapper_hook_body_require_original', wrapper_body((OLD / 'focus3.spec.ts').read_text()) == wrapper_body(data('tests/e2e/first3-specs/mobile-first3.spec.ts').decode()))
config = data('tests/e2e/playwright.first3.config.ts').decode()
grep = re.search(r'grep: /(.*)/,', config).group(1)
old_grep = re.search(r'grep: /(.*)/,', (OLD / 'focus3.config.ts').read_text()).group(1)
check('same_exact_accepted_selection', grep == old_grep)
spec = data('tests/e2e/specs/mobile-real-server.spec.ts').decode()
titles = [(i, re.search(r"test\('([^']+)'", line).group(1)) for i, line in enumerate(spec.splitlines(), 1) if re.search(r"test\('([^']+)'", line)]
selected_lines = [line for line, title in titles if re.search(grep, title)]
check('static_selected_original_14_61_142', selected_lines == [14, 61, 142])
check('canonical_safe_reporter_and_off_flags', "reporter: [['./fixtures/safe-reporter.ts']]" in config and all(config.count(k + ": 'off'") == 2 for k in ['trace', 'video', 'screenshot']) and "PLAYWRIGHT_NO_COPY_PROMPT = '1'" in config)
check('one_worker_no_retries_desktop_chromium', "project.name === 'chromium'" in config and 'workers: 1' in config and 'retries: 0' in config and 'viewport:' not in config)
check('worker_clock_both_use_layers', config.count('    teachingInstant,') == 2 and "readTeachingInstant(process.env.RCT_E2E_TEACHING_CLOCK_DIR)" in config)
check('portable_sources_no_cloud_native_wrapper', '/workspace/' not in config + portable.decode() + data('tests/e2e/first3-specs/mobile-first3.spec.ts').decode())

frozen_paths = ['tests/e2e/specs/mobile-real-server.spec.ts', 'tests/e2e/specs/auth-token-lifecycle.spec.ts', 'tests/e2e/specs/mini-app.spec.ts', 'tests/e2e/fixtures/mobile-dataset.ts', 'tests/e2e/fixtures/artifact-privacy.ts', 'tests/e2e/fixtures/safe-reporter.ts', 'tests/e2e/fixtures/teaching-clock.ts', 'tests/e2e/playwright.config.ts', 'tests/e2e/playwright.real-mobile.config.ts', 'tests/e2e/backend-clock/prepare-clock.mjs', 'tests/e2e/infra/docker-compose.clock-fixture.yml', 'docker-compose.e2e.yml', 'tests/e2e/package.json', 'tests/e2e/package-lock.json', 'build.gradle.kts']
check('all_frozen_sources_byte_preserved', all(base(path) == data(path) for path in frozen_paths))
# Public signer/admin fixtures are compared only for byte preservation. No credential values/hashes enter output.
check('public_signer_admin_inputs_unchanged', all(base(path) == data(path) for path in ['tests/e2e/.env.ci', 'tests/e2e/fixtures/users.ts', 'tests/e2e/fixtures/telegram.ts', 'tests/e2e/infra/seed-telegram.sql']))
check('original_smoke_seven_plus_two', [len(re.findall(r'^\s*test\(', data('tests/e2e/specs/' + f).decode(), re.M)) for f in ['auth-token-lifecycle.spec.ts', 'mini-app.spec.ts']] == [7, 2])
changed = subprocess.check_output(['git', '-C', str(WT), 'diff', BASE, '--name-only', '--diff-filter=M']).decode().splitlines()
check('only_existing_ci_workflow_modified', changed == [workflow_path])
report = {'kind':'SOURCE_STATIC_ONLY', 'base':BASE, 'checks':checks, 'passed':len(checks), 'selectedSourceLines':selected_lines, 'nineSmokeDeclarations':9, 'sourcePreservation':{'observerOldSha256':digest(original_observer), 'observerPortableSha256':digest(portable), 'substitutionsOnly':2, 'frozenPathCount':len(frozen_paths)}, 'native':{'node':'NOT_RUN','typecheck':'NOT_RUN','formatter':'NOT_RUN','playwrightDiscovery':'NOT_RUN','browser':'NOT_RUN','services':'NOT_RUN','actualCI':'NOT_RUN'}, 'limits':{'writerWorktreeCapBytes':33554432,'ciJobMinutes':30,'composeWaitSeconds':480,'fixtureTimeoutSeconds':180,'testTimeoutSeconds':60,'workers':1,'retries':0,'nodeHeapMiB':512}}
(OUT / 'checks.safe.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({'kind':report['kind'],'passed':len(checks),'selectedSourceLines':selected_lines,'native':'NOT_RUN'}))
