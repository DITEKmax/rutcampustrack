set -euo pipefail
RCT_PRODUCER_HEAD_SHA="$(python3 scripts/local_release_context.py revision)"
export RCT_PRODUCER_HEAD_SHA
export RCT_PROBE_SERVICE="${RCT_CI_SERVICE:-${RCT_LOCAL_RELEASE_SERVICE:-}}"
umask 077
python3 - <<'PY_REASON_ENUM'
import hashlib, json, os, pathlib, re, shutil, subprocess, tempfile, time
import xml.etree.ElementTree as ET

app = pathlib.Path('services/attendance-service/attendance-app')
source = app / 'src/main/java/ru/rutcampustrack/attendance/grpc/StudentRequestGrpcMapper.java'
test_source = app / 'src/test/java/ru/rutcampustrack/attendance/grpc/StudentRequestGrpcMapperTest.java'
source_sha = '21c29e14a84fa54dfea8d9762b11c230d89b3ed8cfab158ee1185fcd8b6a857e'
test_sha = '634d96cfefafc74800bdeed29b55445992db45e4fdd278291a9ee17304bd0fe1'
negative_sha = '98a95e309edc13bfa0d3def34db03143ef3c179cb2f46015f8c5164af8f1486a'
package = 'ru.rutcampustrack.attendance.grpc.StudentRequestGrpcMapperTest'
method = 'studentReasonCodesRoundtripWithoutChangingPublishedNumbers'
results, reports = app / 'build/test-results/test', app / 'build/reports/tests/test'
private = pathlib.Path(tempfile.mkdtemp(prefix='reason-enum-red-', dir=os.environ['RUNNER_TEMP']))
deadline = time.monotonic() + 360
current = None
proof = {'schemaVersion': 1, 'state': 'UNEXPECTED_RULE_RESULT', 'phase': 'SOURCE_ADMISSION',
         'producerHeadSha': os.environ['RCT_PRODUCER_HEAD_SHA'],
         'checkoutSha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
         'runId': os.environ.get('GITHUB_RUN_ID'), 'runAttempt': os.environ.get('GITHUB_RUN_ATTEMPT'),
         'event': os.environ.get('GITHUB_EVENT_NAME', 'LOCAL_CONTROL'), 'service': 'attendance-service', 'task': 'test',
         'testMethod': package + '.' + method, 'testSourceSha256': test_sha,
         'restoredSourceSha256': source_sha, 'counterexampleSourceSha256': negative_sha,
         'counterexample': 'UNIVERSITY_ORDER_MAPPED_TO_SUMMONS',
         'expectedWireCode': 6, 'observedWireCode': 7, 'sourceRestoredExact': False,
         'restoredCompileCacheDisabled': True}
def run_target(label):
    remaining = deadline - time.monotonic()
    assert remaining > 0
    with (private / (label + '.private.log')).open('wb') as log:
        return subprocess.run(
            ['./gradlew', ':services:attendance-service:attendance-app:test', '--no-daemon',
             '--no-build-cache', '--tests', package + '.' + method],
            stdout=log, stderr=subprocess.STDOUT, timeout=remaining).returncode

def native_case(failed):
    result = results / ('TEST-' + package + '.xml')
    if failed:
        proof['phase'] = 'RED_XML_FILE'
    assert result.is_file() and not result.is_symlink() and 0 < result.stat().st_size <= 33554432
    raw = result.read_bytes()
    if failed:
        proof['phase'] = 'RED_XML_DTD'
    assert b'<!DOCTYPE' not in raw.upper() and b'<!ENTITY' not in raw.upper()
    suite = ET.fromstring(raw)
    expected = {'tests': '1', 'failures': '1' if failed else '0', 'errors': '0', 'skipped': '0'}
    if failed:
        proof['phase'] = 'RED_XML_SUITE'
    assert suite.tag == 'testsuite' and suite.get('name') == package
    if failed:
        proof['phase'] = 'RED_XML_COUNTS'
    assert {key: suite.get(key) for key in expected} == expected
    cases = suite.findall('testcase')
    if failed:
        proof['phase'] = 'RED_XML_CASE_IDENTITY'
    assert len(cases) == 1 and cases[0].get('classname') == package and cases[0].get('name') == method + '()'
    case = cases[0]
    if failed:
        proof['phase'] = 'RED_XML_ERRORS'
    assert not case.findall('error') and not case.findall('skipped')
    if failed:
        proof['phase'] = 'RED_XML_FAILURE_COUNT'
    assert len(case.findall('failure')) == int(failed)
    return case

try:
    assert all(re.fullmatch('[0-9a-f]{40}', proof[key]) for key in ('producerHeadSha', 'checkoutSha'))
    if proof['event'] == 'LOCAL_CONTROL':
        assert os.environ.get('RCT_LOCAL_RELEASE_REVISION') == proof['checkoutSha']
        assert proof['runId'] is None and proof['runAttempt'] is None
    else:
        assert all(re.fullmatch('[0-9]{1,20}', proof[key]) for key in ('runId', 'runAttempt'))
        assert proof['event'] in {'push', 'pull_request', 'workflow_dispatch'}
    current = source.read_bytes()
    assert 0 < len(current) <= 1048576 and hashlib.sha256(current).hexdigest() == source_sha
    assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(source)]) == b''
    assert current == subprocess.check_output(['git', 'show', 'HEAD:' + source.as_posix()])
    test_bytes = test_source.read_bytes()
    assert hashlib.sha256(test_bytes).hexdigest() == test_sha
    assert test_bytes == subprocess.check_output(['git', 'show', 'HEAD:' + test_source.as_posix()])
    target = '                .containsExactly(1, 2, 3, 4, 5, 6, 7);'
    lines = test_bytes.decode().splitlines()
    assert lines.count(target) == 1
    rule_line = lines.index(target) + 1
    assert rule_line == 152
    original = b'case UNIVERSITY_ORDER -> StudentExcuseReason.STUDENT_EXCUSE_REASON_UNIVERSITY_ORDER;'
    changed = b'case UNIVERSITY_ORDER -> StudentExcuseReason.STUDENT_EXCUSE_REASON_SUMMONS;'
    assert current.count(original) == 1
    negative = current.replace(original, changed)
    assert hashlib.sha256(negative).hexdigest() == negative_sha
    assert negative.replace(changed, original) == current
    for path, label in ((results, 'previous-results'), (reports, 'previous-reports')):
        if path.exists():
            shutil.move(path, private / label)
    source.write_bytes(negative)
    proof['phase'] = 'RED_RUN'
    red_exit = run_target('red')
    proof['red'] = {'nativeExit': red_exit}
    assert red_exit != 0
    proof['phase'] = 'RED_XML_IDENTITY'
    failure = native_case(True).find('failure')
    proof['phase'] = 'RED_FAILURE_TYPE'
    assert failure.get('type') in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}
    proof['phase'] = 'RED_MESSAGE_RULE'
    message = failure.get('message', '').removeprefix(failure.get('type') + ': ')
    assert re.fullmatch(
        r'\s*Expecting actual:\s*\[1,\s*2,\s*3,\s*4,\s*5,\s*7,\s*7\]\s*'
        r'to contain exactly \(and in same order\):\s*\[1,\s*2,\s*3,\s*4,\s*5,\s*6,\s*7\]\s*'
        r'but some elements were not found:\s*\[6\]\s*'
        r'and others were not expected:\s*\[7\]\s*', message)
    proof['phase'] = 'RED_NAMED_FRAME'
    named_lines = re.findall(
        r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?' + re.escape(package + '.' + method)
        + r'\(StudentRequestGrpcMapperTest\.java:([1-9][0-9]{0,5})\)[ \t]*$', failure.text or '')
    assert named_lines == [str(rule_line)]
    proof['red'].update(tests=1, failures=1, errors=0, skipped=0,
                        namedMethodFrameCount=1, expectedRuleFrameMatched=True, ruleFrameLine=rule_line)
    proof['phase'] = 'RESTORE'
    source.write_bytes(current)
    assert source.read_bytes() == current
    assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(source)]) == b''
    proof['sourceRestoredExact'] = True
    for path, label in ((results, 'red-results'), (reports, 'red-reports')):
        if path.exists():
            shutil.move(path, private / label)
    proof['phase'] = 'RESTORED_GREEN_RUN'
    green_exit = run_target('green')
    proof['green'] = {'nativeExit': green_exit}
    assert green_exit == 0
    proof['phase'] = 'RESTORED_GREEN_XML'
    native_case(False)
    proof['green'].update(tests=1, failures=0, errors=0, skipped=0)
    proof.update(state='EXPECTED_RULE_FAILURE_RESTORED_GREEN', phase='COMPLETE')
except Exception as error:
    proof['failureClass'] = type(error).__name__ if type(error).__name__ in {
        'AssertionError', 'TimeoutExpired', 'CalledProcessError', 'FileNotFoundError',
        'ParseError', 'UnicodeDecodeError', 'ValueError', 'OSError'} else 'UNKNOWN'
finally:
    try:
        if current is not None:
            source.write_bytes(current)
            assert source.read_bytes() == current
            assert hashlib.sha256(source.read_bytes()).hexdigest() == source_sha
            assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(source)]) == b''
            proof['sourceRestoredExact'] = True
        assert hashlib.sha256(test_source.read_bytes()).hexdigest() == test_sha
        for path, label in ((results, 'final-results'), (reports, 'final-reports')):
            if path.exists():
                shutil.move(path, private / label)
    except Exception:
        proof.update(state='SOURCE_RESTORE_OR_PRIVATE_CLEANUP_FAILED', sourceRestoredExact=False)
    output = pathlib.Path('build/ci-test-outcomes/attendance-service')
    output.mkdir(mode=0o700, parents=True, exist_ok=True)
    encoded = json.dumps(proof, indent=2) + '\n'
    assert len(encoded.encode()) <= 32768
    (output / 'reason-enum-regression-safe.json').write_text(encoded)
    if not os.environ.get('RCT_LOCAL_RELEASE_REVISION'):shutil.rmtree(private)
raise SystemExit(0 if proof['state'] == 'EXPECTED_RULE_FAILURE_RESTORED_GREEN' else 1)
PY_REASON_ENUM
