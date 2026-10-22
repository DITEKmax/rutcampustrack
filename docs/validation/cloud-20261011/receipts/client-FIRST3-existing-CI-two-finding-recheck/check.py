"""Independent bounded SOURCE/RAM recheck; no target module imports or native runners."""
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile
from zoneinfo import ZoneInfo

OWN = Path(__file__).parent
PACKET = Path('/workspace/scratch/rct-validation/client_real_acceptance/ci-first3-db177-r1')
ROOT = Path('/workspace/rutcampustrack')
OLD = 'f5a0d60000332f6560856e527e91e3defcff8a54'
NEW = '8a83e32af1c317cc084486eba8009d2a44081cdb'
def sha(data):
    return hashlib.sha256(data).hexdigest()
def git(rev, path):
    return subprocess.check_output(['git', '-C', str(ROOT), 'show', rev + ':' + path])
rows = []
def ok(name, evidence):
    rows.append({'check': name, 'pass': True, 'evidence': evidence})
assert sha((PACKET/'manifest.safe.json').read_bytes()) == '3e7d4482b5a9bb0a14765a50369f3e1fa7531de502b9717ed31b16884285f4f1'
assert sha((PACKET/'first3-ci-correction.safe.zip').read_bytes()) == '9a141bd57a9adad4537d5b8fc4cc5bdfef9cc7a2a275bcdcaafb5ff3ed976d20'
with zipfile.ZipFile(PACKET/'first3-ci-correction.safe.zip') as archive:
    manifest = json.loads(archive.read('manifest.safe.json'))
    assert archive.read('manifest.safe.json') == (PACKET/'manifest.safe.json').read_bytes()
    assert len(archive.namelist()) == 21 == len(set(archive.namelist()))
    assert set(archive.namelist()) == {r['path'] for r in manifest['files']} | {'manifest.safe.json'}
    for row in manifest['files']:
        data = archive.read(row['path'])
        assert len(data) == row['bytes'] and sha(data) == row['sha256']
    wfpath = '.github/workflows/ci.yml'
    wrapperpath = 'tests/e2e/first3-specs/mobile-first3.spec.ts'
    paths = [wfpath, wrapperpath, 'tests/e2e/playwright.first3.config.ts', 'tests/e2e/fixtures/first3-observer.ts']
    for path in paths:
        assert archive.read(path) == git(NEW, path)
    ok('sealed_packet_and_four_frozen_source_copies', '21 members; exact manifest, ZIP and four frozen Git blobs')
    wf = archive.read(wfpath).decode()
    wrapper = archive.read(wrapperpath).decode()
    invocation = '          node tests/e2e/backend-clock/prepare-clock.mjs "$RCT_E2E_TEACHING_CLOCK_DIR" "$PWD/build/e2e-clock/clock-fixture.jar"\n'
    scoped = '          (\n            umask 022\n  ' + invocation + '          )\n'
    assert wf.count(scoped) == 1
    assert wf.replace(scoped, invocation).encode() == git(OLD, wfpath)
    added = "test.beforeEach(async ({ page, dataset, teachingInstant }, info) => {\n  if (!teachingInstant) throw new Error('FIRST3 teaching instant is required');\n  // Same educational day as the dataset; real timers and backend security clocks stay enabled.\n  await page.clock.setFixedTime(teachingInstant);\n"
    prior = 'test.beforeEach(async ({ page, dataset }, info) => {\n'
    assert wrapper.count(added) == 1
    assert wrapper.replace(added, prior).encode() == git(OLD, wrapperpath)
    for path in paths[2:]:
        assert archive.read(path) == git(OLD, path)
    delta = subprocess.check_output(['git', '-C', str(ROOT), 'diff', '--name-only', OLD, NEW]).decode().splitlines()
    assert delta == [wfpath, wrapperpath]
    original_spec = 'tests/e2e/specs/mobile-real-server.spec.ts'
    assert git(OLD, original_spec) == git(NEW, original_spec)
    ok('full_reverse_two_deltas', 'Both exact reverse transformations recover complete original bytes; config, observer and original assertion source unchanged')
    assert 'umask 077\n          mkdir -m 700' in wf
    helper = git(NEW, 'tests/e2e/backend-clock/prepare-clock.mjs').decode()
    assert 'mkdirSync(directory, { mode: 0o755 })' in helper
    assert helper.count('{ mode: 0o644 }') == 2
    assert "chmodSync(join(directory, 'clock-fixture.jar'), 0o644)" in helper
    assert (0o755 & ~0o077, 0o644 & ~0o077) == (0o700, 0o600)
    assert (0o755 & ~0o022, 0o644 & ~0o022) == (0o755, 0o644)
    assert 0o700 & 0o007 == 0
    ok('public_clock_DAC_counterexample_and_scoped_positive', 'Old cross-UID directory traversal/read denied; corrected public 0755/0644 permit non-owner read; private 0700 remains denied. RAM mode algebra only')
    assert "while ([0, 6].includes(date.getUTCDay()))" in helper and "T06:00:00Z" in helper
    calendar = []
    for value in ['2026-10-16T17:00:00Z','2026-10-17T12:00:00Z','2026-10-18T20:59:59Z','2026-10-18T21:00:00Z','2026-10-16T21:30:00Z','2026-10-14T21:00:00Z']:
        real = dt.datetime.fromisoformat(value.replace('Z','+00:00'))
        browser_day = real.astimezone(ZoneInfo('Europe/Moscow')).date()
        day = browser_day
        while day.weekday() in (5,6):
            day += dt.timedelta(days=1)
        teaching = dt.datetime.combine(day, dt.time(6), tzinfo=dt.timezone.utc)
        fixed_day = teaching.astimezone(ZoneInfo('Europe/Moscow')).date()
        assert fixed_day == day
        calendar.append({'utc':value, 'old_query_has_target':browser_day == day,'fixed_query_has_target':True})
    assert sum(not row['old_query_has_target'] for row in calendar) == 3
    ok('six_calendar_cases', calendar)
    fixture = git(NEW,'tests/e2e/fixtures/mobile-dataset.ts').decode()
    cfg = archive.read(paths[2]).decode()
    assert "teachingInstant: [undefined, { scope: 'worker', option: true }]" in fixture
    assert 'async ({ playwright, teachingInstant }, use, workerInfo)' in fixture
    assert 'await dataset.initialize();' in fixture and 'this.today = moscowDate(teachingInstant);' in fixture
    assert "timezoneId: 'Europe/Moscow'" in cfg and 'teachingInstant' in cfg
    assert wrapper.index('if (!teachingInstant)') < wrapper.index('await page.clock.setFixedTime') < wrapper.index('new FocusObserver') < wrapper.index('observer.start()') < wrapper.index("require('../specs/mobile-real-server.spec.ts')")
    types = ROOT/'tests/e2e/node_modules/playwright-core/types/types.d.ts'
    assert 'Makes `Date.now` and `new Date()` return fixed fake time at all times, keeps all the timers running.' in types.read_text()
    ok('worker_fixture_and_hook_order_supported_clock_API', 'Worker dataset initialization precedes test body; awaited page clock precedes observer and all original navigation; missing date fails closed; pinned 1.59.1 public types retain real timers')
    tsconfig = json.loads(archive.read('tsconfig.ci-first3.json'))
    assert len(tsconfig['files']) == 5 and all(any(name in p for p in tsconfig['files']) for name in ['playwright.first3.config.ts','mobile-first3.spec.ts','mobile-real-server.spec.ts'])
    discovery = archive.read('source-discovery.config.ts').decode()
    assert '...first3,' in discovery and 'testDir:' not in discovery
    assert "testDir: './first3-specs'" in cfg
    vendor = ROOT/'tests/e2e/node_modules/playwright/lib/common/config.js'
    loader = ROOT/'tests/e2e/node_modules/playwright/lib/common/configLoader.js'
    assert 'pathResolve(configDir, config.testDir)' in vendor.read_text()
    assert 'return import_path.default.resolve(baseDir, relative);' in vendor.read_text()
    assert 'configDir: resolvedConfigFile ? import_path.default.dirname(resolvedConfigFile)' in loader.read_text()
    wrong = PACKET/'first3-specs'
    correct = Path('/workspace/rct-worktrees/client-ci-first3/tests/e2e/first3-specs')
    assert not wrong.exists() and (correct/'mobile-first3.spec.ts').is_file()
    ok('prospective_discovery_origin_counterexample', {'source_finding':'CI-F3-DISCOVERY-ORIGIN-003','loaded_helper_testDir':str(wrong),'actual_wrapper_directory':str(correct),'native':'NOT_RUN','explicit_types_selection':5})
result = {'kind':'INDEPENDENT_NARROW_SOURCE_RECHECK','requested_selected':'gpt-6.1-sol/high','actual_model_effort':'UNKNOWN','runtime_four_paths':'APPROVE_SOURCE_ONLY','original_two_findings':'CLOSED_SOURCE','prospective_Node_discovery_helper':'CHANGES_REQUESTED','pass':True,'checks':rows,'native_Node_browser_services_CI':'NOT_RUN','original_historical_client_causes':'UNKNOWN'}
(OWN/'checks.safe.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'checks':len(rows),'pass':True,'runtime_four_paths':result['runtime_four_paths'],'prospective_discovery':result['prospective_Node_discovery_helper']}))
