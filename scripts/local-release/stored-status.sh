set -euo pipefail
RCT_PRODUCER_HEAD_SHA="$(python3 scripts/local_release_context.py revision)"
export RCT_PRODUCER_HEAD_SHA
export RCT_PROBE_SERVICE="${RCT_CI_SERVICE:-${RCT_LOCAL_RELEASE_SERVICE:-}}"
umask 077
python3 - <<'PY_ENUM_RED'
import hashlib, json, os, pathlib, re, shutil, subprocess, tempfile
import xml.etree.ElementTree as ET

app = pathlib.Path('services/attendance-service/attendance-app')
source = app / 'src/main/java/ru/rutcampustrack/attendance/config/MongoConvertersConfig.java'
test_source = app / 'src/test/java/ru/rutcampustrack/attendance/integration/ReportIT.java'
package = 'ru.rutcampustrack.attendance.integration.ReportIT'
method = 'getLessonAttendance_unknownStoredStatusRefusesWithoutChangingKnownRows'
description = 'unknown stored AttendanceStatus is a typed HTTP conflict'
protected = b'''    @ReadingConverter
    static class AttendanceStatusReader implements Converter<String, AttendanceStatus> {
        @Override
        public AttendanceStatus convert(String source) {
            try {
                return AttendanceStatus.valueOf(source.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new ConflictException(UNKNOWN_STORED_CODE);
            }
        }
    }'''
previous = b'''    @ReadingConverter
    static class AttendanceStatusReader implements Converter<String, AttendanceStatus> {
        @Override
        public AttendanceStatus convert(String source) {
            return AttendanceStatus.valueOf(source.toUpperCase(Locale.ROOT));
        }
    }'''
private = pathlib.Path(tempfile.mkdtemp(prefix='attendance-enum-red-', dir=os.environ['RUNNER_TEMP']))
results = app / 'build/test-results/integrationTest'
reports = app / 'build/reports/integrationTest'
original = None
red_started = False
proof = {'schemaVersion': 1, 'producerHeadSha': os.environ['RCT_PRODUCER_HEAD_SHA'],
         'checkoutSha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
         'service': 'attendance-service', 'counterexample': 'PREVIOUS_ATTENDANCE_STATUS_READER',
         'testMethod': package + '.' + method,
         'state': 'UNEXPECTED_RED_RESULT', 'phase': 'SOURCE_ADMISSION'}
try:
    for path in (source, test_source):
        assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(path)]) == b''
        assert path.read_bytes() == subprocess.check_output(['git', 'show', 'HEAD:' + path.as_posix()])
    current = source.read_bytes()
    assert current.count(protected) == 1
    replacement = current.replace(protected, previous)
    assert replacement != current and replacement.count(previous) == 1
    lines = test_source.read_text().splitlines()
    assert sum(description in line for line in lines) == 1
    target = '.isEqualTo(HttpStatus.CONFLICT);'
    assert sum(target in line for line in lines) == 1
    assertion_line = next(i for i, line in enumerate(lines, 1) if target in line)
    assert not results.exists() and not reports.exists()
    proof.update(testSourceSha256=hashlib.sha256(test_source.read_bytes()).hexdigest(),
                 currentSourceSha256=hashlib.sha256(current).hexdigest(),
                 previousReaderSourceSha256=hashlib.sha256(replacement).hexdigest(),
                 protectedReaderSha256=hashlib.sha256(protected).hexdigest(),
                 previousReaderSha256=hashlib.sha256(previous).hexdigest())
    original = current
    source.write_bytes(replacement)
    assert source.read_bytes() == replacement
    proof['phase'] = 'RED_RUN'
    red_started = True
    with (private / 'red.private.log').open('wb') as log:
        run = subprocess.run(['./gradlew', ':services:attendance-service:attendance-app:integrationTest',
                              '--no-daemon', '--tests', package + '.' + method],
                             stdout=log, stderr=subprocess.STDOUT, timeout=360)
    proof.update(nativeExit=run.returncode, directGradleProcessReaped=True)
    assert run.returncode != 0
    proof['phase'] = 'XML_RULE'
    result = results / ('TEST-' + package + '.xml')
    assert result.is_file() and result.stat().st_size <= 33554432
    data = result.read_bytes()
    assert b'<!DOCTYPE' not in data.upper() and b'<!ENTITY' not in data.upper()
    suite = ET.fromstring(data)
    assert all(suite.get(key) == value for key, value in
               [('tests', '1'), ('failures', '1'), ('errors', '0'), ('skipped', '0')])
    cases = suite.findall('testcase')
    assert len(cases) == 1
    case = cases[0]
    assert case.get('classname') == package and case.get('name') == method + '()'
    assert not case.findall('error') and not case.findall('skipped') and len(case.findall('failure')) == 1
    failure = case.find('failure')
    assert failure.get('type') in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}
    message = failure.get('message', '')
    assert '[' + description + ']' in message
    assert re.search(r'expected:\s*409 CONFLICT\s+but was:\s*500 INTERNAL_SERVER_ERROR(?:\s|$)', message)
    assert method + '(ReportIT.java:' + str(assertion_line) + ')' in (failure.text or '')
    proof.update(state='EXPECTED_RULE_FAILURE', expectedHttpStatus=409, observedHttpStatus=500,
                 knownPositiveBeforeRefusal='PASSED_BEFORE_THE_NAMED_ASSERTION',
                 tests=1, failures=1, errors=0, skipped=0,
                 restoredGreen='REQUIRED_IN_FOLLOWING_ORDINARY_INTEGRATION_GATE')
finally:
    if original is not None:
        source.write_bytes(original)
        assert source.read_bytes() == original
        proof['restoredSourceSha256'] = hashlib.sha256(source.read_bytes()).hexdigest()
    if red_started:
        for path, name in [(results, 'red-results'), (reports, 'red-reports')]:
            if path.exists():
                shutil.move(path, private / name)
        assert not results.exists() and not reports.exists()
    output = pathlib.Path('build/ci-test-outcomes/attendance-service')
    output.mkdir(parents=True, exist_ok=True)
    (output / 'attendance-enum-regression-safe.json').write_text(json.dumps(proof, indent=2) + '\n')
    if not os.environ.get('RCT_LOCAL_RELEASE_REVISION'):shutil.rmtree(private)
PY_ENUM_RED
