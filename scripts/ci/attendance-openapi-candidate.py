import atexit, hashlib, json, os, pathlib, re, shutil, subprocess, sys
import xml.etree.ElementTree as ET

# Only fixed stages and bounded exit codes leave this producer.
stage, stage_state, child_exit, binding = 'SOURCE_BINDING', 'NOT_APPLICABLE', 'NOT_STARTED', None

def record_failure(error_type, error, traceback):
    global stage_state, child_exit
    stage_state = 'FAILED'
    if stage == 'UPDATE_CHILD':
        if error_type is subprocess.CalledProcessError:
            code = error.returncode
            child_exit = code if type(code) is int and -127 <= code <= 255 else 'OUT_OF_RANGE'
        elif error_type is subprocess.TimeoutExpired:
            child_exit = 'TIMEOUT'

def write_stage():
    if binding is None:
        return
    try:
        target = pathlib.Path('build/ci-test-outcomes/attendance-service/openapi-candidate-stage.safe.json')
        assert all(not parent.is_symlink() for parent in target.parents)
        target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        proof = dict(binding, state=stage_state, stage=stage, childExit=child_exit,
                     originalIntegrationOutcome='failure', ordinaryFailurePreserved=True)
        with target.open('x', encoding='utf-8') as output:
            output.write(json.dumps(proof, indent=2) + '\n')
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
            output.write('stage=true\n')
    except BaseException:
        os._exit(1)

# Suppress exception text/tracebacks; the original nonzero Python exit is preserved.
sys.excepthook = record_failure
atexit.register(write_stage)

def decode(raw):
    def pairs(items):
        value = {}
        for key, item in items:
            assert key not in value
            value[key] = item
        return value
    return json.loads(raw, object_pairs_hook=pairs,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite JSON')))

package = 'ru.rutcampustrack.attendance.integration.OpenApiSnapshotIT'
method = 'apiDocsMatchesSnapshot'
app = pathlib.Path('services/attendance-service/attendance-app')
test_source = app / 'src/test/java/ru/rutcampustrack/attendance/integration/OpenApiSnapshotIT.java'
test_sha = 'cbfb5fc2fe25e11424e50ea6e0b5a7ddcf4a4a9750a20a5bcdd57a3b962533fe'
source = test_source.read_bytes()
assert hashlib.sha256(source).hexdigest() == test_sha
assert source == subprocess.check_output(['git', 'show', 'HEAD:' + test_source.as_posix()])
lines = source.decode().splitlines()
target = '                .isEqualTo(comparisonMapper.readTree(expected));'
assert lines.count(target) == 1
expected_line = lines.index(target) + 1
checkout = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
head = os.environ['RCT_CI_PR_HEAD_SHA']
run_id, run_attempt = os.environ['GITHUB_RUN_ID'], os.environ['GITHUB_RUN_ATTEMPT']
assert re.fullmatch(r'[0-9a-f]{40}', checkout) and re.fullmatch(r'[0-9a-f]{40}', head)
assert re.fullmatch(r'[1-9][0-9]{0,19}', run_id) and re.fullmatch(r'[1-9][0-9]{0,9}', run_attempt)
binding = {'schemaVersion': 1, 'producerHeadSha': head, 'checkoutSha': checkout,
           'runId': run_id, 'runAttempt': run_attempt, 'testMethod': package + '.' + method,
           'testSourceSha256': test_sha}
stage = 'SAFE_OUTCOME_ADMISSION'
report = pathlib.Path('build/ci-test-outcomes/attendance-service/integrationTest/safe-outcomes.json')
if not report.is_file() or not 0 < report.stat().st_size <= 33554432:
    raise SystemExit(0)
value = decode(report.read_bytes())
if value.get('collection') != 'RESULTS':
    raise SystemExit(0)
stage = 'SAFE_OUTCOME_BINDING'
meta = value['metadata']
assert (meta['checkoutSha'] == checkout
        and meta['prHeadSha'] == os.environ['RCT_CI_PR_HEAD_SHA']
        and meta['service'] == 'attendance-service' and meta['task'] == 'integrationTest'
        and meta['job'] == 'java-integration-test' and meta['testStepOutcome'] == 'failure'
        and meta['runId'] == os.environ['GITHUB_RUN_ID']
        and meta['runAttempt'] == os.environ['GITHUB_RUN_ATTEMPT'])
stage = 'SELECTED_SOURCE_METHOD'
selected = [case for case in value['cases']
            if case.get('classname') == package and case.get('method') == method]
if not selected:
    raise SystemExit(0)
assert len(selected) == 1
case = selected[0]
assert (case['identity'] == 'SOURCE_METHOD' and case['source'] == test_source.as_posix()
        and case['sourceSha256'] == test_sha)
if case['status'] != 'FAILED':
    raise SystemExit(0)
stage = 'NAMED_XML_ADMISSION'
result = app / ('build/test-results/integrationTest/TEST-' + package + '.xml')
assert result.is_file() and not result.is_symlink() and 0 < result.stat().st_size <= 33554432
raw = result.read_bytes()
assert b'<!DOCTYPE' not in raw.upper() and b'<!ENTITY' not in raw.upper()
suite = ET.fromstring(raw)
cases = suite.findall('testcase')
if (suite.tag != 'testsuite' or suite.get('name') != package or len(cases) != 1
        or cases[0].get('classname') != package or cases[0].get('name') != method + '()'
        or {key: suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')}
        != {'tests': '1', 'failures': '1', 'errors': '0', 'skipped': '0'}):
    raise SystemExit(0)
native_case = cases[0]
if native_case.findall('error') or native_case.findall('skipped') or len(native_case.findall('failure')) != 1:
    raise SystemExit(0)
failure = native_case.find('failure')
stage = 'ASSERTION_TYPE'
if failure.get('type') not in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}:
    raise SystemExit(0)
stage = 'EXACT_DRIFT_FRAME'
named_lines = re.findall(
    r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
    + re.escape(package + '.' + method)
    + r'\(OpenApiSnapshotIT\.java:([1-9][0-9]{0,5})\)[ \t]*$', failure.text or '')
if named_lines != [str(expected_line)]:
    raise SystemExit(0)
stage = 'WRAPPER_GUARDS'
wrapper = pathlib.Path('gradlew')
assert subprocess.check_output(['git', 'ls-tree', '--format=%(objectmode)', 'HEAD', '--', 'gradlew']) == b'100644\n'
wrapper_head = subprocess.check_output(['git', 'show', 'HEAD:gradlew'])
wrapper_sha = hashlib.sha256(wrapper_head).hexdigest()
assert wrapper_sha == 'c07475b363508dbe7cf7d1ded1b31f6d44bb97d98d069a455ab862b1fb9b25f0'
wrapper_mode = wrapper.stat().st_mode & 0o7777
assert wrapper_mode in {0o644, 0o755}

def verify_wrapper():
    assert wrapper.is_file() and not wrapper.is_symlink()
    assert wrapper.stat().st_mode & 0o7777 == wrapper_mode
    assert 0 < wrapper.stat().st_size <= 1048576
    assert hashlib.sha256(wrapper.read_bytes()).hexdigest() == wrapper_sha

verify_wrapper()
stage = 'BASELINE_GUARDS'
assert subprocess.check_output(['git', 'diff', '--cached', '--name-only', '-z']) == b''
baseline = {path for path in subprocess.check_output(
    ['git', 'diff', 'HEAD', '--name-only', '-z']).split(b'\0') if path}
assert baseline in (set(), {b'gradlew'})
if baseline:
    assert wrapper_mode == 0o755
stage, child_exit = 'UPDATE_CHILD', 'UNKNOWN'
subprocess.run(
    ['./gradlew', ':services:attendance-service:attendance-app:integrationTest', '--no-daemon',
     '--tests', package + '.' + method, '-Popenapi.snapshot.update=true'],
    stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT, timeout=360, check=True)
child_exit = 0
stage = 'GENERATED_SNAPSHOT_SCHEMA'
snapshot = pathlib.Path('docs/openapi/attendance.json')
assert not snapshot.is_symlink() and snapshot.is_file() and 0 < snapshot.stat().st_size <= 2097152
payload = snapshot.read_bytes()
generated = decode(payload)
assert (type(generated) is dict and set(generated) == {'openapi', 'info', 'tags', 'paths', 'components'}
        and generated['openapi'] == '3.1.0' and type(generated['info']) is dict
        and all(type(generated['info'].get(key)) is str for key in ('title', 'version'))
        and type(generated['tags']) is list and all(type(tag) is dict for tag in generated['tags'])
        and type(generated['paths']) is dict and generated['paths']
        and all(path.startswith('/') and type(item) is dict for path, item in generated['paths'].items())
        and type(generated['components']) is dict and type(generated['components'].get('schemas')) is dict)
stage = 'GENERATED_CHANGE_GUARDS'
changed = subprocess.check_output(['git', 'diff', 'HEAD', '--name-only', '-z']).split(b'\0')
assert {path for path in changed if path} == baseline | {b'docs/openapi/attendance.json'}
assert subprocess.check_output(['git', 'diff', '--cached', '--name-only', '-z']) == b''
verify_wrapper()
assert subprocess.check_output(['git', 'ls-tree', '--format=%(objectmode)', 'HEAD', '--', snapshot.as_posix()]) == b'100644\n'
assert snapshot.stat().st_mode & 0o7777 == 0o644
assert payload != subprocess.check_output(['git', 'show', 'HEAD:' + snapshot.as_posix()])
stage = 'OFFICIAL_CANDIDATE_WRITE'
output = pathlib.Path('build/ci-test-outcomes/attendance-service/openapi-generated')
output.mkdir(mode=0o700, parents=True, exist_ok=False)
shutil.copyfile(snapshot, output / 'attendance.json')
proof = {'schemaVersion': 1, 'state': 'OFFICIAL_ATTENDANCE_OPENAPI_GENERATED',
         'producerHeadSha': os.environ['RCT_CI_PR_HEAD_SHA'], 'checkoutSha': checkout,
         'runId': os.environ['GITHUB_RUN_ID'], 'runAttempt': os.environ['GITHUB_RUN_ATTEMPT'],
         'testMethod': package + '.' + method, 'testSourceSha256': test_sha,
         'originalDriftFrameLine': expected_line, 'originalIntegrationOutcome': 'failure',
         'generatedSnapshotSha256': hashlib.sha256(payload).hexdigest(), 'generatedSnapshotBytes': len(payload),
         'trackedChanges': sorted(path.decode('ascii') for path in changed if path),
         'preexistingWrapperModeChange': baseline == {b'gradlew'}, 'wrapperSourceSha256': wrapper_sha,
         'ordinaryFailurePreserved': True}
(output / 'generation.safe.json').write_text(json.dumps(proof, indent=2) + '\n')
stage = 'GENERATED_OUTPUT'
with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as target_output:
    target_output.write('generated=true\n')
stage_state = 'GENERATED'
