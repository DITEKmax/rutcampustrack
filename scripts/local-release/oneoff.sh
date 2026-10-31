set -euo pipefail
RCT_PRODUCER_HEAD_SHA="$(python3 scripts/local_release_context.py revision)"
export RCT_PRODUCER_HEAD_SHA
export RCT_PROBE_SERVICE="${RCT_CI_SERVICE:-${RCT_LOCAL_RELEASE_SERVICE:-}}"
set -euo pipefail
umask 077
source=services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/replacement/AssignmentReplacementService.java
results=services/schedule-service/schedule-app/build/test-results/integrationTest
reports=services/schedule-service/schedule-app/build/reports/integrationTest
private=$(mktemp -d "$RUNNER_TEMP/oneoff-regression.XXXXXX")
cp "$source" "$private/current.java"
restore() {
  result=$?
  trap - EXIT
  cp "$private/current.java" "$source" || exit 1
  cmp --silent "$private/current.java" "$source" || exit 1
  if [ -d "$results" ]; then mv "$results" "$private/red-results"; fi
  if [ -d "$reports" ]; then mv "$reports" "$private/red-reports"; fi
  if [ -f "$private/verified.safe.json" ]; then
    mkdir -p build/ci-test-outcomes/schedule-service
    cp "$private/verified.safe.json" build/ci-test-outcomes/schedule-service/oneoff-regression-safe.json
  fi
  if [ -z "${RCT_LOCAL_RELEASE_REVISION:-}" ]; then rm -rf -- "$private"; fi
  exit "$result"
}
trap restore EXIT
if [ -n "${RCT_LOCAL_RELEASE_REVISION:-}" ]; then
  git cat-file -e '75cb5ed5ae8d731991a5efd447c611b0eb9f65da^{commit}'
else
  git fetch --quiet --no-tags --depth=1 origin 75cb5ed5ae8d731991a5efd447c611b0eb9f65da
fi
git show "75cb5ed5ae8d731991a5efd447c611b0eb9f65da:$source" > "$private/previous.java"
printf '%s  %s\n' f4ed91a3f41286a379325ee172acbbf0af24b37050f00307eb5b95938d1b3482 "$private/previous.java" | sha256sum --check --status
cp "$private/previous.java" "$source"
set +e
./gradlew :services:schedule-service:schedule-app:integrationTest --no-daemon --tests 'ru.rutcampustrack.schedule.integration.AssignmentReplacementServiceIT.oneOffCloseAdoptThenNextDayReplacementPreservesHistoryAndReplay' > "$private/red.private.log" 2>&1
red_exit=$?
set -e
python3 - "$private" "$red_exit" <<'PY_ONEOFF_DIAGNOSTICS'
import json, os, pathlib, re, subprocess, sys, xml.etree.ElementTree as ET
private = pathlib.Path(sys.argv[1])
proof = {'schemaVersion':1,'state':'UNEXPECTED_ONEOFF_RED','redExit':int(sys.argv[2]),
         'producerHeadSha':os.environ['RCT_PRODUCER_HEAD_SHA'],
         'checkoutSha':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
         'diagnosticKind':'NO_JUNIT','failureType':'NONE','causeClasses':[],'stackMethods':[]}
allowed_types = {
    'org.opentest4j.AssertionFailedError','java.lang.AssertionError','java.lang.IllegalStateException',
    'org.springframework.beans.factory.BeanCreationException','org.springframework.beans.factory.UnsatisfiedDependencyException',
    'org.springframework.beans.factory.BeanDefinitionStoreException','org.springframework.beans.factory.NoSuchBeanDefinitionException',
    'org.springframework.context.ApplicationContextException','org.springframework.boot.context.properties.bind.BindException',
    'org.springframework.boot.context.properties.ConfigurationPropertiesBindException',
    'org.springframework.boot.context.properties.bind.validation.BindValidationException',
    'org.springframework.jdbc.BadSqlGrammarException','org.springframework.jdbc.CannotGetJdbcConnectionException',
    'org.springframework.jdbc.UncategorizedSQLException','org.flywaydb.core.api.FlywayException',
    'org.flywaydb.core.internal.exception.FlywayMigrateException','org.postgresql.util.PSQLException',
    'org.testcontainers.containers.ContainerLaunchException','org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException',
    'java.net.ConnectException','java.net.UnknownHostException','java.lang.NoSuchMethodError',
    'java.lang.ClassNotFoundException','java.lang.NoClassDefFoundError'}
allowed_methods = {
    'ru.rutcampustrack.schedule.integration.AssignmentReplacementServiceIT.oneOffCloseAdoptThenNextDayReplacementPreservesHistoryAndReplay',
    'org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean',
    'org.springframework.beans.factory.support.ConstructorResolver.autowireConstructor',
    'org.springframework.beans.factory.support.DefaultListableBeanFactory.resolveDependency',
    'org.flywaydb.core.Flyway.migrate','org.testcontainers.containers.GenericContainer.start',
    'org.testcontainers.containers.GenericContainer.tryStart','org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse'}
def read_log_window(path):
    try:
        if path.is_symlink() or not path.is_file():
            return '', 'UNAVAILABLE'
        with path.open('rb') as log:
            size = log.seek(0, 2)
            cutoff = size > 65536
            log.seek(max(0, size - 65536))
            data = log.read(65536)
        if cutoff:
            data = data.partition(b'\n')[2]
        # A partial original line cannot attest a public Gradle marker.
        if not data.endswith(b'\n'):
            data = data.rpartition(b'\n')[0]
        return data.decode('utf-8', errors='replace'), 'TAIL_64_KIB' if cutoff else 'FULL'
    except OSError:
        return '', 'UNREADABLE'

def boundary_diagnostic(log_text, window, report, failure_type, causes):
    tasks = {
        ':services:schedule-service:schedule-app:' + name: name
        for name in ('extractIncludeProto', 'extractProto', 'generateProto', 'compileJava',
                     'processResources', 'compileTestJava', 'processTestResources', 'integrationTest')}
    failed = [match.group(1) for line in log_text.splitlines()
              if (match := re.fullmatch(r'> Task ([^\s]+) FAILED', line))]
    task = tasks.get(failed[0], 'OTHER') if failed else 'NONE'
    dependency_markers = {
        "> Could not resolve all files for configuration ':services:schedule-service:schedule-app:" + name + "'."
        for name in ('compileClasspath', 'testCompileClasspath', 'testRuntimeClasspath',
                     'protobufToolsLocator_protoc', 'protobufToolsLocator_grpc')}
    lines = log_text.splitlines()
    category, evidence = 'UNKNOWN', 'NONE'
    if any(line in dependency_markers for line in lines):
        category, evidence = 'DEPENDENCY', 'GRADLE_MARKER'
    elif task in {'compileJava', 'compileTestJava'} and any(
            line == '> Compilation failed; see the compiler output below.' for line in lines):
        category, evidence = 'COMPILE', 'GRADLE_MARKER'
    elif any(re.fullmatch(r"> Process 'Gradle Test Executor [0-9]{1,6}' finished with non-zero exit value [1-9][0-9]{0,2}", line) for line in lines):
        category, evidence = 'TEST_WORKER', 'GRADLE_MARKER'
    elif failure_type in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}:
        category, evidence = 'TEST_ASSERTION', 'JUNIT_TYPE'
    elif any(value.startswith(('org.springframework.beans.', 'org.springframework.context.',
                              'org.springframework.boot.context.properties.'))
             for value in [failure_type, *causes]):
        category, evidence = 'STARTUP', 'JUNIT_TYPE'
    return {'logWindow': window, 'failedTask': task,
            'taskScope': 'VISIBLE_WINDOW' if window == 'TAIL_64_KIB' else 'FULL_LOG' if window == 'FULL' else 'NONE',
            'category': category, 'evidence': evidence, 'junitReport': report}

log_text, log_window = read_log_window(private/'red.private.log')
report_presence = 'MISSING'
text = ''
try:
    result = pathlib.Path('services/schedule-service/schedule-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.schedule.integration.AssignmentReplacementServiceIT.xml')
    report_presence = ('SYMLINK' if result.is_symlink() else 'MISSING' if not result.is_file()
                       else 'OVERSIZE' if result.stat().st_size > 33554432 else 'PRESENT')
    if result.is_file() and not result.is_symlink() and result.stat().st_size <= 33554432:
        data = result.read_bytes()
        assert b'<!DOCTYPE' not in data.upper() and b'<!ENTITY' not in data.upper()
        suite = ET.fromstring(data)
        failures = suite.findall('.//failure') + suite.findall('.//error')
        proof['diagnosticKind'] = 'JUNIT_TYPES_ONLY'
        if failures:
            kind = failures[0].get('type','')
            proof['failureType'] = kind if kind in allowed_types else 'OTHER'
            text = '\n'.join((node.text or '') for node in failures)
    else:
        text = log_text
    proof['causeClasses'] = sorted({value for value in re.findall(r'(?m)^\s*Caused by:\s+([A-Za-z0-9_.$]+)',text) if value in allowed_types})
    proof['stackMethods'] = sorted({value for value in re.findall(r'(?m)^\s*at\s+(?:[^\s/]+/)?([A-Za-z0-9_.$]+)\(',text) if value in allowed_methods})
except (OSError,ValueError,AssertionError,ET.ParseError):
    proof['diagnosticKind'] = 'UNREADABLE_JUNIT'
    report_presence = 'UNREADABLE'
has_junit = proof['diagnosticKind'] == 'JUNIT_TYPES_ONLY'
proof['boundaryDiagnostic'] = boundary_diagnostic(
    log_text, log_window, report_presence,
    proof['failureType'] if has_junit else 'NONE', proof['causeClasses'] if has_junit else [])
(private/'verified.safe.json').write_text(json.dumps(proof,indent=2)+'\n')
PY_ONEOFF_DIAGNOSTICS
python3 - "$private" "$red_exit" <<'PY'
import hashlib, json, os, pathlib, re, subprocess, sys, xml.etree.ElementTree as ET
private = pathlib.Path(sys.argv[1])
assert int(sys.argv[2]) != 0, 'Previous implementation must violate the rule'
source = pathlib.Path('services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/replacement/AssignmentReplacementService.java')
result = pathlib.Path('services/schedule-service/schedule-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.schedule.integration.AssignmentReplacementServiceIT.xml')
assert result.is_file() and result.stat().st_size <= 33554432
data = result.read_bytes()
assert b'<!DOCTYPE' not in data.upper() and b'<!ENTITY' not in data.upper()
suite = ET.fromstring(data)
cases = suite.findall('testcase')
assert len(cases) == 1 and cases[0].get('name') == 'oneOffCloseAdoptThenNextDayReplacementPreservesHistoryAndReplay()'
case = cases[0]
assert not case.findall('error') and not case.findall('skipped') and len(case.findall('failure')) == 1
failure = case.find('failure')
assert failure.get('type') in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}
message = failure.get('message', '')
assert '[one-off replacement moved count]' in message
assert re.search(r'expected:\s*1L?\s+but was:\s*0L?(?:\s|$)', message), 'Failure must identify the missing one-off movement'
proof = {'schemaVersion': 1, 'state': 'EXPECTED_ONE_OFF_MOVEMENT_FAILURE',
         'producerHeadSha': os.environ['RCT_PRODUCER_HEAD_SHA'],
         'checkoutSha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
         'previousRevision': '75cb5ed5ae8d731991a5efd447c611b0eb9f65da',
         'previousSourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
         'restoredSourceSha256': hashlib.sha256((private / 'current.java').read_bytes()).hexdigest(),
         'testMethod': 'oneOffCloseAdoptThenNextDayReplacementPreservesHistoryAndReplay',
         'expectedMovedCount': 1, 'observedMovedCount': 0,
         'tests': 1, 'failures': 1, 'errors': 0, 'skipped': 0}
(private / 'verified.safe.json').write_text(json.dumps(proof, indent=2) + '\n')
PY
