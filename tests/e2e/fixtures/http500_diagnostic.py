"""Shared closed HTTP diagnostic logic for CI and admitted Ubuntu lanes."""
import hashlib, json, os, re, selectors, stat, subprocess, time
import argparse
import importlib.util
import math
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
EVENT_SCHEMA = 'rct-diagnostic-event.v1'


@dataclass(frozen=True)
class DiagnosticScope:
    stage: str
    mode: str
    run: Path
    project: str
    compose: tuple
    environment: dict
    nonce: str | None = None


def owned_directory(path):
    info = path.lstat()
    if not stat.S_ISDIR(info.st_mode) or stat.S_IMODE(info.st_mode) != 0o700 or info.st_uid != os.getuid():
        raise ValueError('Invalid private directory')


def owned_bytes(path, bound):
    before = path.lstat()
    with os.fdopen(os.open(path, os.O_RDONLY | os.O_NOFOLLOW), 'rb') as stream:
        info = os.fstat(stream.fileno())
        if (not stat.S_ISREG(info.st_mode) or stat.S_IMODE(info.st_mode) != 0o600 or info.st_uid != os.getuid()
                or not 0 < info.st_size <= bound or (info.st_dev, info.st_ino) != (before.st_dev, before.st_ino)):
            raise ValueError('Invalid closed input')
        raw = stream.read(bound + 1)
        after = os.fstat(stream.fileno())
        if len(raw) != info.st_size or (info.st_size, info.st_mtime_ns, info.st_ctime_ns) != (after.st_size, after.st_mtime_ns, after.st_ctime_ns):
            raise ValueError('Closed input changed')
    return raw


def admit_scope(config, mode='ci', run_dir=None, environment=None):
    environment = dict(os.environ if environment is None else environment)
    if config not in STAGES.values():
        raise ValueError('Unknown diagnostic stage')
    stage = next(key for key, value in STAGES.items() if value == config)
    if mode == 'ci':
        if (environment.get('GITHUB_ACTIONS') != 'true' or environment.get('RUNNER_ENVIRONMENT') != 'github-hosted'
                or environment.get('RCT_E2E_SYNTHETIC_SEED') != 'true'
                or Path(environment.get('GITHUB_WORKSPACE', '')).resolve() != ROOT):
            raise ValueError('Disposable diagnostic unavailable')
        run, attempt = environment.get('GITHUB_RUN_ID', ''), environment.get('GITHUB_RUN_ATTEMPT', '')
        if not re.fullmatch('[1-9][0-9]{0,19}', run) or not re.fullmatch('[1-9][0-9]{0,5}', attempt):
            raise ValueError('Invalid diagnostic binding')
        temporary, work = Path(environment.get('RUNNER_TEMP', '')), Path(environment.get(config[0], ''))
        if not temporary.is_absolute() or work != temporary / (config[1] + run + '-' + attempt):
            raise ValueError('Invalid diagnostic directory')
        compose = ('docker', 'compose', '--env-file', str(ROOT / 'tests/e2e/.env.ci'), '-p', 'rct-e2e',
                   '-f', str(ROOT / 'docker-compose.e2e.yml'), '-f', str(ROOT / 'tests/e2e/infra/docker-compose.clock-fixture.yml'))
        if stage == 'HOSTED_BOT_MARK500' and (environment.get('RCT_E2E_BOT_ROLE_MENU') != 'true'
                or environment.get('RCT_E2E_REMAINING5') != 'true'):
            raise ValueError('Hosted MARK diagnostic unavailable')
        scope = DiagnosticScope(stage, 'ci', work, 'rct-e2e', compose, environment)
    elif mode == 'local':
        # Reuse the existing owner/nonce/source/CA/Compose/lease admission; never mimic GHA.
        path = ROOT / 'scripts/local_e2e_async.py'
        spec = importlib.util.spec_from_file_location('_rct_local_diagnostic_admission', path)
        local = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(local)
        value = local.validate_local(environment=environment)
        if (value['mode'], stage) not in {('client', 'HOMEWORK_CREATE503'), ('lesson-lifecycle', 'STUDENT_STATS503')}:
            raise ValueError('Diagnostic stage unavailable in local lane')
        work = Path(value['run'])
        compose = ('docker', 'compose', '-p', value['project'], '-f', str(work / 'compose.json'))
        scope = DiagnosticScope(stage, 'local', work, value['project'], compose, environment, value['nonce'])
    else:
        raise ValueError('Unknown diagnostic context')
    if run_dir is not None and Path(run_dir).resolve() != scope.run:
        raise ValueError('Foreign diagnostic directory')
    owned_directory(scope.run)
    return scope


def selected_scope(config, context):
    context = admit_scope(config) if context is None else context
    if type(context) is not DiagnosticScope or STAGES.get(context.stage) != config:
        raise ValueError('Foreign diagnostic context')
    return context

STAGES = {
    'HOMEWORK_CREATE503': ('RCT_REMAINING5_RUN', 'rct-remaining5-', ('artifacts',),
                           'mobile-real-server.spec.ts', 'DATASET_HOMEWORK_CREATE_HTTP',
                           'academic-service', 'ACADEMIC_HOMEWORK_CREATE', '', ''),
    'STUDENT_STATS503': ('RCT_LESSON_LIFECYCLE_RUN', 'rct-lesson-lifecycle-',
                         ('artifacts',), 'mobile-schedule-lifecycle.spec.ts', 'STUDENT_STATS_HTTP503',
                         'attendance-service', 'ATTENDANCE_STUDENT_PROJECTION',
                         'ru.rutcampustrack.attendance.grpc.AttendanceStudentGrpcServiceImpl.getStudentAttendanceProjection',
                         'AttendanceStudentGrpcServiceImpl.java'),
    'REPORT500': ('RCT_SEMESTER_RECONCILIATION_RUN', 'rct-semester-reconciliation-',
                  ('artifacts',), 'semester-lifecycle.spec.ts', 'DATASET_ATTENDANCE_REPORT_HTTP',
                  'attendance-service', 'ATTENDANCE_LESSON_REPORT',
                  'ru.rutcampustrack.attendance.report.ReportController.getLessonAttendance',
                  'ReportController.java'),
    'HOSTED_BOT_MARK500': ('RCT_REMAINING5_RUN', 'rct-u20-bot-',
                ('artifacts',), 'mobile-real-server.spec.ts', 'DATASET_ATTENDANCE_MARK_HTTP',
                'attendance-service', 'ATTENDANCE_MARK',
                'ru.rutcampustrack.attendance.marking.MarkingController.mark', 'MarkingController.java'),
    'MARK500': ('RCT_SEMESTER_LIFECYCLE_RUN', 'rct-semester-lifecycle-',
                ('artifacts',), 'semester-lifecycle.spec.ts', 'DATASET_ATTENDANCE_MARK_HTTP',
                'attendance-service', 'ATTENDANCE_MARK',
                'ru.rutcampustrack.attendance.marking.MarkingController.mark', 'MarkingController.java'),
    'LESSON_CREATE500': ('RCT_ASSIGNMENT_LIFECYCLE_RUN', 'rct-assignment-lifecycle-',
                        ('artifacts', 'prepare'), 'assignment-lifecycle.spec.ts',
                        'DATASET_LESSON_CREATE_HTTP', 'schedule-service', 'SCHEDULE_ONEOFF_LESSON_CREATE',
                        'ru.rutcampustrack.schedule.oneoff.OneOffLessonController.createOneOffLesson',
                        'OneOffLessonController.java'),
}
CLASSES = {
    'io.grpc.StatusRuntimeException': 'GRPC_STATUS',
    'io.grpc.StatusException': 'GRPC_STATUS',
    'org.postgresql.util.PSQLException': 'POSTGRESQL',
    'javax.net.ssl.SSLHandshakeException': 'TLS_HANDSHAKE',
    'java.net.ConnectException': 'CONNECT',
    'java.lang.IllegalStateException': 'ILLEGAL_STATE',
    'java.lang.IllegalArgumentException': 'ILLEGAL_ARGUMENT',
    'java.lang.NullPointerException': 'NULL_POINTER',
    'org.springframework.dao.DataIntegrityViolationException': 'DATA_INTEGRITY',
    'org.springframework.http.converter.HttpMessageNotWritableException': 'HTTP_SERIALIZATION',
    'org.springframework.web.server.ResponseStatusException': 'HTTP_STATUS',
    'reactor.netty.http.client.PrematureCloseException': 'UPSTREAM_CLOSED',
}
HTTP500_STAGES = {'ATTENDANCE_LESSON_REPORT', 'SCHEDULE_ONEOFF_LESSON_CREATE', 'ATTENDANCE_MARK'}
ORIGINS = {'attendance-service': 'ATTENDANCE_MVC', 'schedule-service': 'SCHEDULE_MVC',
           'api-gateway': 'GATEWAY_WEBFLUX'}
GATEWAY_LOGGER = 'org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler'
AMBIGUITY_REASONS = {
    'MULTIPLE_CONTROLLER_FRAMES', 'UNQUALIFIED_SUPPRESSION', 'MULTIPLE_CONTROLLER_RECORDS',
    'MULTIPLE_FALLBACK_RECORDS', 'CROSS_ORIGIN_CANDIDATES', 'MISSING_EXCEPTION_STACK',
    'INVALID_EXCEPTION_STACK', 'STACK_LIMIT', 'LOG_LIMIT', 'MALFORMED_LOG_RECORD',
    'UNMAPPED_EXCEPTION_CLASS', 'EVIDENCE_LIMIT', 'NO_ELIGIBLE_ERROR_RECORD',
}
OWN_FRAME_SOURCES = {'ATTENDANCE_REPORT_SERVICE': ('attendance-service',
                               'ru.rutcampustrack.attendance.report.ReportService',
                               'ReportService.java'),
 'ATTENDANCE_READ_PORT': ('attendance-service',
                          'ru.rutcampustrack.attendance.checkin.AttendanceReadPortImpl',
                          'AttendanceReadPortImpl.java'),
 'HEADMAN_APPROVED_TICKET_RESOLVER': ('attendance-service',
                                      'ru.rutcampustrack.attendance.report.HeadmanApprovedTicketResolver',
                                      'HeadmanApprovedTicketResolver.java'),
 'ATTENDANCE_REPORT_CONTROLLER': ('attendance-service',
                                  'ru.rutcampustrack.attendance.report.ReportController',
                                  'ReportController.java'),
 'SCHEDULE_ONEOFF_SERVICE': ('schedule-service',
                             'ru.rutcampustrack.schedule.oneoff.OneOffLessonService',
                             'OneOffLessonService.java'),
 'SCHEDULE_ONEOFF_COORDINATOR': ('schedule-service',
                                 'ru.rutcampustrack.schedule.oneoff.OneOffLessonCoordinator',
                                 'OneOffLessonCoordinator.java'),
 'SCHEDULE_ONEOFF_WRITER': ('schedule-service',
                            'ru.rutcampustrack.schedule.oneoff.OneOffLessonWriter',
                            'OneOffLessonWriter.java'),
 'SCHEDULE_SEMESTER_ARCHIVE_WRITE_FENCE': ('schedule-service',
                                           'ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence',
                                           'ScheduleSemesterArchiveWriteFence.java'),
 'SCHEDULE_ONEOFF_CONTROLLER': ('schedule-service',
                                'ru.rutcampustrack.schedule.oneoff.OneOffLessonController',
                                'OneOffLessonController.java'),
 'INTERNAL_JWT_ISSUER_FILTER': ('api-gateway',
                                'ru.rutcampustrack.gateway.security.InternalJwtIssuerFilter',
                                'InternalJwtIssuerFilter.java'),
 'INTERNAL_JWT_ISSUER_CLIENT': ('api-gateway',
                                'ru.rutcampustrack.gateway.security.InternalJwtIssuerClient',
                                'InternalJwtIssuerClient.java')}
PROJECTION_KINDS = {
    'INVALID_OCCURRENCE', 'DUPLICATE_OCCURRENCE', 'UNSUPPORTED_ATTENDANCE_STATUS',
    'INVALID_ROSTER', 'INVALID_METRICS', 'ACADEMIC_DEPENDENCY', 'SCHEDULE_DEPENDENCY', 'UNKNOWN',
} | {'RPC_' + code for code in (
    'OK', 'CANCELLED', 'UNKNOWN', 'INVALID_ARGUMENT', 'DEADLINE_EXCEEDED', 'NOT_FOUND',
    'ALREADY_EXISTS', 'PERMISSION_DENIED', 'RESOURCE_EXHAUSTED', 'FAILED_PRECONDITION',
    'ABORTED', 'OUT_OF_RANGE', 'UNIMPLEMENTED', 'INTERNAL', 'UNAVAILABLE', 'DATA_LOSS', 'UNAUTHENTICATED')}
PROJECTION_STAGES = {'ACADEMIC_SCOPE', 'ACADEMIC_MEMBERS', 'SCHEDULE_LESSONS',
                     'CURRENT_GROUP_SCOPE', 'GROUP_CATALOGUE', 'GROUP_SERIES', 'OWN_MARKS',
                     'LESSON_SNAPSHOT', 'RANK', 'UNKNOWN'}
LIMIT = 8388608

def decode(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('Duplicate closed field')
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=pairs,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))


def load_catalog():
    raw = Path(__file__).with_name('http-diagnostic-cases.json').read_bytes()
    if not 0 < len(raw) <= 32768:
        raise ValueError('Catalog size')
    value = decode(raw)
    if (type(value) is not dict or set(value) != {'schema', 'cases'}
            or value['schema'] != 'rct-diagnostic-catalog.v1' or type(value['cases']) is not dict):
        raise ValueError('Catalog schema')
    for case, item in value['cases'].items():
        if (not re.fullmatch('[A-Z][A-Z0-9_]{0,63}', case) or type(item) is not dict
                or set(item) != {'events'} or type(item['events']) is not list or not 1 <= len(item['events']) <= 8):
            raise ValueError('Catalog case')
        for event in item['events']:
            if (type(event) is not dict or set(event) != {'stageId', 'eventCode', 'httpStatus'}
                    or event['stageId'] not in {'DATASET_HTTP', 'FRAMEWORK', 'D002_RECONNECT'}
                    or type(event['eventCode']) is not str or not re.fullmatch('[A-Z][A-Z0-9_]{0,63}', event['eventCode'])
                    or type(event['httpStatus']) is not int or event['httpStatus'] not in {0, 500, 503}):
                raise ValueError('Catalog event')
    return value['cases']


CATALOG = load_catalog()
# The remaining5 caller selects this finite cohort. ASYNC_DOCX_RESUME has the
# same setup event permission, but belongs to its separate async consumer.
CLIENT_CASES = {
    'LIMITED_ADMIN_ACCOUNTS', 'HOMEWORK_FOREIGN_RETRY', 'SESSION_REVOKE_RECOVERY',
    'D003_DEVICE_REUSE_SUSPENDED_GRANT', 'D003_HEADMAN_TMA_MARK',
    'D002_WARM_OFFLINE_REVOKED', 'D002_RECONNECT_ACCOUNT_AUTHORITY', 'D002_TMA_ONLINE_ONLY',
    'N2_STUDENT_RETRY_ACTIVE', 'N2_STUDENT_RETRY_SUSPENDED',
}
STATS_CASES = {'STUDENT_MANUAL_BUDGET_AUTO_APPROVAL', 'HEADMAN_LESSON_CANCEL_RESTORE',
               'HEADMAN_RECURRING_SLOT_HISTORY', 'STUDENT_SEMESTER_ROOM_REFRESH'}
TARGET_CASES = {'HOMEWORK_CREATE503': CLIENT_CASES, 'STUDENT_STATS503': STATS_CASES,
                'REPORT500': {'SEMESTER_ARCHIVE_RESTORE'}, 'MARK500': {'SEMESTER_ARCHIVE_RESTORE'},
                'HOSTED_BOT_MARK500': {'HOSTED_PRIVATE_BOT_AUTHORITY'},
                'LESSON_CREATE500': {'ASSIGNMENT_LIFECYCLE_API'}}
REPORTED_HINTS = {'UNKNOWN', 'INVALID_TIME_VALUE', 'REPORTED_CLASS_ERROR', 'REPORTED_CLASS_TYPE_ERROR',
                  'REPORTED_CLASS_RANGE_ERROR', 'REPORTED_CLASS_REFERENCE_ERROR', 'REPORTED_CLASS_SYNTAX_ERROR',
                  'REPORTED_CLASS_TIMEOUT_ERROR', 'REPORTED_FRAME_DATASET_CALL', 'REPORTED_FRAME_DATASET_DATE',
                  'REPORTED_FRAME_DATASET_INITIALIZE'}
HTTP_CODES = {event['eventCode'] for item in CATALOG.values() for event in item['events'] if event['stageId'] == 'DATASET_HTTP'}


def closed_event(value, case):
    return (type(value) is dict and set(value) == {'schema', 'caseId', 'stageId', 'eventCode', 'httpStatus'}
            and value['schema'] == EVENT_SCHEMA and value['caseId'] == case and case in CATALOG
            and type(value['httpStatus']) is int
            and {key: value[key] for key in ('stageId', 'eventCode', 'httpStatus')} in CATALOG[case]['events'])


def has_event(row, stage, code, status):
    expected = {'schema': EVENT_SCHEMA, 'caseId': row['caseId'], 'stageId': stage, 'eventCode': code, 'httpStatus': status}
    return expected in row['diagnosticEvents']


def _closed_case_row(row):
    required = {'caseId', 'diagnosticEvents', 'project', 'status', 'expectedStatus', 'durationMs', 'errorCount', 'diagnostics'}
    optional = {'file', 'line', 'errorLocations', 'reportedFrameworkError', 'd002ReconnectObservation'}
    if (type(row) is not dict or not required <= set(row) or not set(row) <= required | optional
            or type(row['caseId']) is not str or row['caseId'] not in CATALOG or row['project'] != 'chromium'
            or row['status'] not in {'passed', 'failed', 'timedOut', 'skipped', 'interrupted'}
            or row['expectedStatus'] != 'passed' or type(row['errorCount']) is not int or not 0 <= row['errorCount'] <= 8
            or type(row['durationMs']) not in (int, float) or not math.isfinite(row['durationMs']) or not 0 <= row['durationMs'] <= 1800000
            or type(row['diagnosticEvents']) is not list or len(row['diagnosticEvents']) > 8
            or not all(closed_event(item, row['caseId']) for item in row['diagnosticEvents'])
            or len({json.dumps(item, sort_keys=True) for item in row['diagnosticEvents']}) != len(row['diagnosticEvents'])
            or (row['diagnosticEvents'] and (row['status'] not in {'failed', 'timedOut', 'interrupted'} or row['errorCount'] == 0))):
        return False
    if 'file' in row and not diagnostic_filename(row['file']):
        return False
    if 'line' in row and (type(row['line']) is not int or not 0 < row['line'] < 1000000):
        return False
    locations = row.get('errorLocations', [])
    if (type(locations) is not list or len(locations) > min(8, row['errorCount']) or not all(
            type(item) is dict and set(item) == {'file', 'line'} and diagnostic_filename(item['file'])
            and type(item['line']) is int and 0 < item['line'] < 1000000 for item in locations)):
        return False
    diagnostics = row['diagnostics']
    if type(diagnostics) is not list or len(diagnostics) > 8:
        return False
    for item in diagnostics:
        if (type(item) is not dict or set(item) != {'stage', 'cause', 'httpStatus'} or type(item['httpStatus']) is not int
                or not ((item['stage'] == 'UNKNOWN' and item['cause'] in REPORTED_HINTS and item['httpStatus'] == 0)
                        or (item['stage'] == 'DATASET_HTTP' and item['cause'] in HTTP_CODES
                            and item['httpStatus'] in {200, 201, 202, 400, 401, 403, 404, 409, 410, 422, 423, 429, 500, 502, 503, 504}))):
            return False
    if 'reportedFrameworkError' in row and (row['status'] not in {'failed', 'timedOut', 'interrupted'}
            or not row['errorCount'] or not closed_reported_framework_error(row['reportedFrameworkError'])):
        return False
    if 'd002ReconnectObservation' in row and (row['caseId'] != 'D002_RECONNECT_ACCOUNT_AUTHORITY'
            or not has_event(row, 'D002_RECONNECT', 'OBSERVATION', 0)
            or not closed_d002_reconnect_observation(row['d002ReconnectObservation'])):
        return False
    if has_event(row, 'D002_RECONNECT', 'OBSERVATION', 0) and 'd002ReconnectObservation' not in row:
        return False
    return True
def diagnostic_filename(value):
    return type(value) is str and re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9_.-]{0,119}\.(?:ts|js)', value) is not None


def closed_case_row(row):
    try:
        return _closed_case_row(row)
    except (ValueError, TypeError, KeyError, RecursionError, UnicodeError):
        return False


def output(state, stage, category='UNKNOWN', chain=None, own_frame=None,
           origin='UNKNOWN', class_chain=None):
    value = {'state': state, 'exceptionCategory': category, 'sourceStage': stage}
    if stage in HTTP500_STAGES:
        value.update(reportedExceptionChain=[] if chain is None else chain,
                     reportedOwnFrame=own_frame, reportedOrigin=origin,
                     reportedExceptionClassChain=[] if class_chain is None else class_chain,
                     reportedAmbiguityEvidence=[])
    return value

INITIAL_MEMBERSHIP_CATEGORIES = {
    'ACADEMIC_UNAVAILABLE', 'SCHEDULE_UNAVAILABLE', 'RESOURCE_NOT_FOUND',
    'INVALID_ARGUMENT', 'TRANSACTION_ROLLBACK', 'MONGO_TRANSACTION',
    'DUPLICATE_KEY', 'EFFECT_REJECTED', 'OTHER',
    'ACADEMIC_RPC_UNAVAILABLE',
    'ACADEMIC_RPC_DEADLINE',
    'ACADEMIC_RPC_AUTHENTICATION',
    'ACADEMIC_RPC_PERMISSION',
    'ACADEMIC_RPC_OTHER',
    'ACADEMIC_DIRECTED_IDENTITY',
    'ACADEMIC_SEMESTER_ECHO',
    'ACADEMIC_SEMESTER_ADMISSION',
    'ACADEMIC_ROSTER_ECHO',
    'ACADEMIC_ROSTER_IDENTITY',
}

INITIAL_MEMBERSHIP_STARTUP_BANNER = {
    '  .   ____          _            __ _ _',
    " /\\\\ / ___'_ __ _ _(_)_ __  __ _ \\ \\ \\ \\",
    "( ( )\\___ | '_ | '_| | '_ \\/ _` | \\ \\ \\ \\",
    ' \\\\/  ___)| |_)| | | | | || (_| |  ) ) ) )',
    "  '  |____| .__|_| |_|_| |_\\__, | / / / /",
    ' =========|_|==============|___/=/_/_/_/',
}

def initial_membership_output(counts='UNKNOWN', unknown_reason='UNKNOWN'):
    return {'state': 'UNKNOWN' if counts == 'UNKNOWN' else 'REPORTED',
            'sourceStage': 'INITIAL_MEMBERSHIP', 'categoryCounts': counts,
            'unknownReason': unknown_reason if counts == 'UNKNOWN' else 'NOT_APPLICABLE'}

def is_initial_membership_failure(value):
    keys = {
        'schema', 'phase', 'configuredListenerAttempts', 'measuredListenerAttempts',
        'exactDlqEnvelope', 'pendingBeforeReplay', 'appliedAfterReplay',
        'sameArchiveCompleted', 'duplicateBusinessUnchanged', 'recoveryPublishAttempted',
        'duplicateAttempted', 'retainedDlqCopies', 'restorationSucceeded', 'clockUnchanged',
        'cleanupOutcome', 'firstRestartFailure', 'mainCloseLastRead',
        'firstHttp500OwnedServices', 'strandedLastRead', 'setupQuiescenceLastRead',
        'replayLastRead', 'replayFailureLog', 'archiveReadinessLastRead',
    }
    if type(value) is dict and value.get('schema') in ('semester-reconciliation.v9', 'semester-reconciliation.v10', 'semester-reconciliation.v11'):
        keys |= {'readinessFailureLog', 'recoveryHealthLastRead'}
    if type(value) is dict and value.get('schema') == 'semester-reconciliation.v11':
        keys.add('academicRecoveryHealthLastRead')
    if (type(value) is not dict or set(value) != keys
            or value['schema'] not in ('semester-reconciliation.v7', 'semester-reconciliation.v8', 'semester-reconciliation.v9', 'semester-reconciliation.v10', 'semester-reconciliation.v11') or value['phase'] != 'SETUP'
            or type(value['configuredListenerAttempts']) is not int
            or value['configuredListenerAttempts'] != 3
            or value['measuredListenerAttempts'] != 'UNKNOWN'
            or value['retainedDlqCopies'] != 'UNKNOWN'
            or value['cleanupOutcome'] != 'PASSED'
            or any(value[key] is not False for key in (
                'exactDlqEnvelope', 'pendingBeforeReplay', 'appliedAfterReplay',
                'sameArchiveCompleted', 'duplicateBusinessUnchanged',
                'recoveryPublishAttempted', 'duplicateAttempted'))
            or any(value[key] is not True for key in ('restorationSucceeded', 'clockUnchanged'))
            or any(value[key] is not None for key in (
                'firstRestartFailure', 'mainCloseLastRead', 'firstHttp500OwnedServices',
                'strandedLastRead', 'replayLastRead', 'replayFailureLog', 'archiveReadinessLastRead'))
            or (value['schema'] in ('semester-reconciliation.v9', 'semester-reconciliation.v10', 'semester-reconciliation.v11') and any(value[key] is not None
                for key in ('readinessFailureLog', 'recoveryHealthLastRead')))
            or (value['schema'] == 'semester-reconciliation.v11'
                and value['academicRecoveryHealthLastRead'] is not None)):
        return False
    read = value['setupQuiescenceLastRead']
    counters = {'mainReady', 'mainUnacked', 'mainConsumers', 'deadReady', 'deadUnacked',
                'retryReady', 'retryUnacked', 'scheduleUnproven', 'scheduleLegacy', 'scheduleDue'}
    return (type(read) is dict and set(read) == counters | {
                'firstFailed', 'initialExpectedApplied', 'initialNoPendingWork', 'initialProducerSent'}
            and all(type(read[key]) is int and 0 <= read[key] <= 1000000 for key in counters)
            and read['firstFailed'] == 'DLQ_READY' and read['deadReady'] > 0
            and read['mainReady'] == 0 and read['mainUnacked'] == 0 and read['mainConsumers'] == 1
            and read['initialExpectedApplied'] is True
            and read['initialNoPendingWork'] is False and read['initialProducerSent'] is True)

def classify_initial_membership(payload):
    # Reported bounded-log counts do not identify an event or establish DLQ causality.
    counts = {category: 0 for category in sorted(INITIAL_MEMBERSHIP_CATEGORIES)}
    lines = payload.splitlines()
    if len(payload) > LIMIT:
        return initial_membership_output(unknown_reason='LOG_BYTE_LIMIT')
    if len(lines) >= 2000:
        return initial_membership_output(unknown_reason='LOG_TAIL_LIMIT')
    startup, startup_lines = True, 0
    for line in lines:
        if len(line) > 131072:
            return initial_membership_output(unknown_reason='LOG_LINE_LIMIT')
        try:
            record = decode(line)
        except (ValueError, UnicodeError, RecursionError):
            # Only the source-proven JVM/Spring preamble before any JSON is noise.
            if (startup and startup_lines < 16 and (
                    line == b'' or line in {item.encode() for item in INITIAL_MEMBERSHIP_STARTUP_BANNER}
                    or re.fullmatch(rb'Picked up JAVA_TOOL_OPTIONS: [^\r\n]{1,8192}', line)
                    or re.fullmatch(rb' :: Spring Boot :: {1,64}\(v3\.5\.16\)', line))):
                startup_lines += 1
                continue
            return initial_membership_output(unknown_reason='LOG_FORMAT')
        startup = False
        if type(record) is not dict:
            return initial_membership_output(unknown_reason='LOG_RECORD_SHAPE')
        if (record.get('logger') != 'ru.rutcampustrack.attendance.event.EventConsumer'
                or record.get('level') != 'WARN'):
            continue
        message = record.get('msg')
        if type(message) is not str:
            return initial_membership_output(unknown_reason='TARGET_MESSAGE_SHAPE')
        if not message.startswith('event consumption failure family=INITIAL_MEMBERSHIP'):
            continue
        match = re.fullmatch(r'event consumption failure family=INITIAL_MEMBERSHIP category=([A-Z_]+)', message)
        if not match or match[1] not in INITIAL_MEMBERSHIP_CATEGORIES:
            return initial_membership_output(unknown_reason='TARGET_TEMPLATE')
        counts[match[1]] += 1
        if sum(counts.values()) > 200:
            return initial_membership_output(unknown_reason='MATCH_COUNT_LIMIT')
    return (initial_membership_output(counts) if sum(counts.values())
            else initial_membership_output(unknown_reason='NO_MATCHING_WARN'))


# This receiver is disjoint from the legacy semester MARK diagnostic.
def is_hosted_mark(config):
    return config == STAGES['HOSTED_BOT_MARK500']

def is_ordinary_mark(config):
    return config == STAGES['MARK500']

HOSTED_FRAME_SOURCES = {'ATTENDANCE_MARKING_CONTROLLER': ('attendance-service',
                                   'ru.rutcampustrack.attendance.marking.MarkingController',
                                   'MarkingController.java'),
 'ATTENDANCE_MARKING_SERVICE': ('attendance-service',
                                'ru.rutcampustrack.attendance.marking.MarkingService',
                                'MarkingService.java'),
 'ATTENDANCE_PAIR_WRITE_COORDINATOR': ('attendance-service',
                                       'ru.rutcampustrack.attendance.student.PairWriteCoordinator',
                                       'PairWriteCoordinator.java'),
 'INTERNAL_JWT_ISSUER_FILTER': ('api-gateway',
                                'ru.rutcampustrack.gateway.security.InternalJwtIssuerFilter',
                                'InternalJwtIssuerFilter.java'),
 'INTERNAL_JWT_ISSUER_CLIENT': ('api-gateway',
                                'ru.rutcampustrack.gateway.security.InternalJwtIssuerClient',
                                'InternalJwtIssuerClient.java')}
ORDINARY_MARK_FRAME_SOURCES = {'ATTENDANCE_MARKING_CONTROLLER': ('attendance-service',
                                   'ru.rutcampustrack.attendance.marking.MarkingController',
                                   'MarkingController.java'),
 'ATTENDANCE_MARKING_SERVICE': ('attendance-service',
                                'ru.rutcampustrack.attendance.marking.MarkingService',
                                'MarkingService.java'),
 'ATTENDANCE_PAIR_WRITE_COORDINATOR': ('attendance-service',
                                       'ru.rutcampustrack.attendance.student.PairWriteCoordinator',
                                       'PairWriteCoordinator.java'),
 'ATTENDANCE_SEMESTER_ARCHIVE_FENCE': ('attendance-service',
                                       'ru.rutcampustrack.attendance.event.SemesterArchiveFence',
                                       'SemesterArchiveFence.java'),
 'INTERNAL_JWT_ISSUER_FILTER': ('api-gateway',
                                'ru.rutcampustrack.gateway.security.InternalJwtIssuerFilter',
                                'InternalJwtIssuerFilter.java'),
 'INTERNAL_JWT_ISSUER_CLIENT': ('api-gateway',
                                'ru.rutcampustrack.gateway.security.InternalJwtIssuerClient',
                                'InternalJwtIssuerClient.java')}
# Driver 5.5.2 / Spring Data MongoDB 4.5.13; no suffix or package wildcard.
HOSTED_CLASSES = {
    **CLASSES,
    **{ 'org.springframework.transaction.' + name: 'MONGO_TRANSACTION' for name in (
        'TransactionSystemException', 'CannotCreateTransactionException')},
    **{ 'org.springframework.data.mongodb.' + name: 'MONGO_TRANSACTION' for name in (
        'MongoTransactionException', 'ClientSessionException', 'TransientClientSessionException',
        'UncategorizedMongoDbException', 'InvalidMongoDbApiUsageException', 'TransientMongoDbException')},
    'org.springframework.dao.DuplicateKeyException': 'DUPLICATE_KEY',
    'org.springframework.dao.DataAccessResourceFailureException': 'DATA_ACCESS_RESOURCE',
    'org.springframework.dao.InvalidDataAccessApiUsageException': 'INVALID_DATA_ACCESS',
    **{ 'com.mongodb.' + name: 'MONGO' for name in (
        'MongoCommandException', 'MongoWriteException', 'MongoWriteConcernException', 'MongoException',
        'MongoTimeoutException', 'MongoOperationTimeoutException', 'MongoExecutionTimeoutException',
        'MongoSocketOpenException', 'MongoSocketReadException', 'MongoSocketWriteException',
        'MongoSocketReadTimeoutException', 'MongoSocketClosedException', 'MongoSocketException',
        'MongoSecurityException', 'MongoNotPrimaryException', 'MongoNodeIsRecoveringException',
        'MongoServerUnavailableException', 'MongoConfigurationException')},
}

def exception_classes(config):
    return HOSTED_CLASSES if is_hosted_mark(config) or is_ordinary_mark(config) else CLASSES


def is_hosted_mark_row(row):
    return (closed_case_row(row) and row['caseId'] == 'HOSTED_PRIVATE_BOT_AUTHORITY'
            and row['durationMs'] <= 360000
            and has_event(row, 'DATASET_HTTP', 'DATASET_ATTENDANCE_MARK_HTTP', 500))

def load_hosted_mark_witness(config, context=None):
    # mtime stays private and binds the original failure, even if this alias runs later.
    path = selected_scope(config, context).run / 'mark-http500.request-id'
    before = path.lstat()
    with os.fdopen(os.open(path, os.O_RDONLY | os.O_NOFOLLOW), 'rb') as stream:
        info = os.fstat(stream.fileno())
        if (not stat.S_ISREG(info.st_mode) or stat.S_IMODE(info.st_mode) != 0o600
                or info.st_uid != os.getuid() or not 0 < info.st_size <= 64
                or (info.st_dev, info.st_ino) != (before.st_dev, before.st_ino)
                or not 0 < info.st_mtime_ns <= time.time_ns()):
            raise ValueError('Invalid private hosted MARK witness')
        raw = stream.read(65)
        after = os.fstat(stream.fileno())
        if (len(raw) != info.st_size
                or (info.st_size, info.st_mtime_ns, info.st_ctime_ns)
                    != (after.st_size, after.st_mtime_ns, after.st_ctime_ns)):
            raise ValueError('Private hosted MARK witness changed')
    value = raw.decode('ascii').removesuffix('\n')
    if not canonical_request_id(value):
        raise ValueError('Missing canonical hosted MARK witness')
    def seconds(nanos):
        whole, fraction = divmod(nanos, 1000000000)
        return str(whole) + '.' + str(fraction).zfill(9)
    return value, (seconds(info.st_mtime_ns - 180000000000), seconds(info.st_mtime_ns))

def mongo_unknown(state='UNKNOWN'):
    return {'state': state, 'code': 'UNKNOWN', 'serverLabels': None, 'site': 'UNKNOWN'}

def mongo_site(stack):
    # A stack location is not a transaction outcome. Framework methods are exact names.
    matches = re.findall(r'(?m)^[ \t]*at org\.springframework\.data\.mongodb\.'
                         r'MongoTransactionManager\.(doBegin|doCommit|doRollback)'
                         r'\(MongoTransactionManager\.java:[1-9][0-9]{0,5}\)[ \t]*$', stack)
    sites = {'doBegin': 'BEGIN', 'doCommit': 'COMMIT', 'doRollback': 'ROLLBACK'}
    observed = {sites[match] for match in matches}
    if len(observed) == 1:
        return next(iter(observed))
    if observed:
        return 'UNKNOWN'
    for symbol in ('ATTENDANCE_MARKING_SERVICE', 'ATTENDANCE_PAIR_WRITE_COORDINATOR'):
        _, clazz, source = HOSTED_FRAME_SOURCES[symbol]
        pattern = (r'(?m)^[ \t]*at ' + re.escape(clazz)
                   + r'\.[A-Za-z_$<>][A-Za-z0-9_$<>]*\('
                   + re.escape(Path(source).name) + r':([1-9][0-9]{0,5})\)[ \t]*$')
        if any(int(match[1]) <= 1000000 for match in re.finditer(pattern, stack)):
            return 'BODY'
    return 'UNKNOWN'

def mongo_command(stack):
    # Parse only the driver's own complete header. Errmsg is never searched for codes/labels.
    lines = re.findall(r'(?m)^[ \t]*(?:Caused by: )?com\.mongodb\.MongoCommandException(?::[^\r\n]*|$)', stack)
    if len(lines) != 1:
        return mongo_unknown('AMBIGUOUS' if len(lines) > 1 else 'UNKNOWN')
    header = re.match(r"[ \t]*(?:Caused by: )?com\.mongodb\.MongoCommandException: "
                      r"Command failed with error ((?:0|[1-9][0-9]{0,9}))(?: \(([A-Za-z][A-Za-z0-9]{0,63})\))?: '", lines[0])
    if header is None:
        return mongo_unknown()
    message, separator, raw = lines[0][header.end():].rpartition('. The full response is ')
    _, server_separator, server = message.rpartition("' on server ")
    if (not separator or not server_separator or not 0 < len(server) <= 1024
            or not raw.startswith('{') or not raw.endswith('}') or len(raw.encode()) > 65536):
        return mongo_unknown()
    try:
        response = decode(raw)
    except (ValueError, UnicodeError, RecursionError):
        return mongo_unknown()
    if (type(response) is not dict or type(response.get('code')) is not int
            or response['code'] != int(header[1]) or not 0 <= response['code'] <= 2147483647
            or ('codeName' in response and (type(response['codeName']) is not str
                or re.fullmatch('[A-Za-z][A-Za-z0-9]{0,63}', response['codeName']) is None))
            or (header[2] is not None and response.get('codeName') != header[2])):
        return mongo_unknown()
    pending, visited = [(response, 0)], 0
    while pending:
        item, depth = pending.pop()
        visited += 1
        if depth > 8 or visited > 256:
            return mongo_unknown()
        if type(item) is dict:
            pending.extend((value, depth + 1) for value in item.values())
        elif type(item) is list:
            pending.extend((value, depth + 1) for value in item)
    codes = {112: ('WriteConflict', 'WRITE_CONFLICT'), 11000: ('DuplicateKey', 'DUPLICATE_KEY'),
             251: ('NoSuchTransaction', 'NO_SUCH_TRANSACTION'), 13: ('Unauthorized', 'UNAUTHORIZED'),
             18: ('AuthenticationFailed', 'AUTHENTICATION_FAILED')}
    named = codes.get(response['code'])
    if named and response.get('codeName') != named[0]:
        return mongo_unknown()
    labels = response.get('errorLabels', [])
    allowed = {'TransientTransactionError': 'TRANSIENT_TRANSACTION_ERROR',
               'UnknownTransactionCommitResult': 'UNKNOWN_TRANSACTION_COMMIT_RESULT'}
    if (type(labels) is not list or len(labels) > 2 or any(type(label) is not str or label not in allowed for label in labels)
            or len(set(labels)) != len(labels)):
        return mongo_unknown()
    return {'state': 'REPORTED', 'code': named[1] if named else 'OTHER',
            'serverLabels': sorted(allowed[label] for label in labels), 'site': mongo_site(stack)}

def hosted_mongo(payloads, config, selected, request_id):
    if (selected['state'] == 'AMBIGUOUS'
            or selected['reportedOrigin'] not in ORIGINS.values()):
        return mongo_unknown('AMBIGUOUS' if selected['state'] == 'AMBIGUOUS' else 'UNKNOWN')
    service = next(service for service, origin in ORIGINS.items() if origin == selected['reportedOrigin'])
    payload = payloads[service]
    if len(payload) > LIMIT or len(payload.splitlines()) >= 200:
        return mongo_unknown()
    stacks = []
    for line in payload.splitlines():
        if len(line) > 131072:
            return mongo_unknown()
        try:
            record = decode(line)
        except (ValueError, UnicodeError, RecursionError):
            return mongo_unknown()
        if type(record) is not dict:
            return mongo_unknown()
        logger = GATEWAY_LOGGER if service == 'api-gateway' else 'ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler'
        if (record.get('requestId') != request_id or record.get('logger') != logger
                or record.get('level') != 'ERROR'
                or (service == 'api-gateway' and record.get('service') != service)):
            continue
        stack = record.get('stack')
        if type(stack) is not str or not 0 < len(stack.encode()) <= 131072:
            return mongo_unknown()
        stacks.append(stack)
    if len(stacks) != 1:
        return mongo_unknown('AMBIGUOUS' if len(stacks) > 1 else 'UNKNOWN')
    return mongo_command(stacks[0])

def frame_sources(config):
    if is_hosted_mark(config):
        return HOSTED_FRAME_SOURCES
    if is_ordinary_mark(config):
        return ORDINARY_MARK_FRAME_SOURCES
    if config[4] != 'DATASET_ATTENDANCE_MARK_HTTP':
        return OWN_FRAME_SOURCES
    return {**OWN_FRAME_SOURCES, 'ATTENDANCE_MARKING_CONTROLLER': (
        'attendance-service', 'ru.rutcampustrack.attendance.marking.MarkingController',
        'MarkingController.java')}


def reported_own_frame(stack, config, captured_service=None):
    matches = []
    for symbol, (service, clazz, source) in frame_sources(config).items():
        if service != (config[5] if captured_service is None else captured_service):
            continue
        pattern = (r'(?m)^[ \t]*at (?:[A-Za-z0-9_.$/@-]{1,256}/)?'
                   + re.escape(clazz) + r'\.[A-Za-z_$<>][A-Za-z0-9_$<>]*\('
                   + re.escape(Path(source).name) + r':([1-9][0-9]{0,5})\)[ \t]*$')
        for match in re.finditer(pattern, stack):
            line = int(match[1])
            if line <= 1000000:
                matches.append((match.start(), {'symbol': symbol, 'line': line}))
    return min(matches, key=lambda item: item[0])[1] if matches else None

def closed_reported_framework_error(value):
    keys = {'reportedErrorClass', 'reportedTransportCode', 'reportedSourceFrame', 'reportedHttpStatus'}
    if (type(value) is not dict or set(value) != keys
            or type(value['reportedErrorClass']) is not str
            or value['reportedErrorClass'] not in {'Error', 'TypeError', 'RangeError', 'ReferenceError', 'SyntaxError', 'TimeoutError', 'UNKNOWN'}
            or type(value['reportedTransportCode']) is not str
            or value['reportedTransportCode'] not in {'TLS_CERTIFICATE', 'ECONNREFUSED', 'ECONNRESET', 'ETIMEDOUT', 'ENOTFOUND', 'EACCES', 'ENOENT', 'TARGET_CLOSED', 'TIMEOUT', 'UNKNOWN'}
            or type(value['reportedHttpStatus']) is not int
            or value['reportedHttpStatus'] not in {0, 200, 201, 202, 400, 401, 403, 404, 409, 410, 422, 423, 429, 500, 502, 503, 504}):
        return False
    frame = value['reportedSourceFrame']
    if frame is not None:
        files = {
            'MOBILE_DATASET': 'tests/e2e/fixtures/mobile-dataset.ts',
            'PWA_AUTH': 'tests/e2e/fixtures/pwa-auth.ts',
            'TEACHING_CLOCK': 'tests/e2e/fixtures/teaching-clock.ts',
            'ARTIFACT_PRIVACY': 'tests/e2e/fixtures/artifact-privacy.ts',
            'MOBILE_SPEC': 'tests/e2e/specs/mobile-real-server.spec.ts',
            'PRIVACY_CONTROL': 'tests/e2e/artifact-controls/privacy.control.spec.ts',
            'PLAYWRIGHT_FIXTURE_RUNNER': 'tests/e2e/node_modules/playwright/lib/worker/fixtureRunner.js',
            'PLAYWRIGHT_TEST_INFO': 'tests/e2e/node_modules/playwright/lib/worker/testInfo.js',
            'PLAYWRIGHT_INDEX': 'tests/e2e/node_modules/playwright/lib/index.js',
        }
        if (type(frame) is not dict or set(frame) != {'file', 'line', 'column'}
                or type(frame['file']) is not str or frame['file'] not in files
                or any(type(frame[key]) is not int or not 0 < frame[key] <= 1000000 for key in ('line', 'column'))):
            return False
    return len(json.dumps(value, ensure_ascii=True, separators=(',', ':')).encode()) <= 1024

def without_reported_framework_error(row):
    if type(row) is not dict or 'reportedFrameworkError' not in row:
        return row
    if (type(row.get('status')) is not str or row['status'] not in {'failed', 'timedOut', 'interrupted'}
            or type(row.get('errorCount')) is not int or not 0 < row['errorCount'] <= 8
            or not closed_reported_framework_error(row['reportedFrameworkError'])):
        return None
    # Only this separately validated observation is projected away; core keys/gates stay exact.
    return {key: value for key, value in row.items() if key != 'reportedFrameworkError'}

def is_student_stats_failure(value):
    if type(value) is not dict or type(value.get('results')) is not list or len(value['results']) != 4:
        return False
    rows = value['results']
    if not all(closed_case_row(row) for row in rows) or {row['caseId'] for row in rows} != STATS_CASES:
        return False
    target = next(row for row in rows if row['caseId'] == 'HEADMAN_RECURRING_SLOT_HISTORY')
    return (target['status'] == 'failed' and target['errorCount'] == 1
            and has_event(target, 'DATASET_HTTP', 'STUDENT_STATS_HTTP503', 503))



def closed_d002_reconnect_observation(value):
    # Same closed JSON projection as the source-bound SafeReporter; no arbitrary strings.
    try:
        if len(json.dumps(value, separators=(',', ':'), allow_nan=False).encode()) > 2048:
            return False
    except (TypeError, ValueError, RecursionError, UnicodeError):
        return False
    def exact(item, keys):
        return type(item) is dict and set(item) == set(keys)
    def flags(item, keys):
        return exact(item, keys) and all(type(item[key]) is bool for key in keys)
    endpoints, epochs = ('refresh', 'current'), ('preOnline', 'postOnline')
    top_flags = ('offlineSeen', 'onlineSeen', 'online', 'observationUnknown')
    if (not exact(value, (*top_flags, 'native', 'pageHttp', 'priorPending', 'refreshHttpStatus'))
            or any(type(value[key]) is not bool for key in top_flags)
            or not exact(value['native'], epochs) or not exact(value['pageHttp'], endpoints)
            or not flags(value['priorPending'], endpoints)):
        return False
    for epoch in epochs:
        states = value['native'][epoch]
        if not exact(states, endpoints):
            return False
        for endpoint in endpoints:
            state = states[endpoint]
            if (not exact(state, ('phase', 'multiple')) or type(state['phase']) is not str
                    or state['phase'] not in {'NOT_CALLED', 'PENDING', 'FULFILLED', 'REJECTED'}
                    or type(state['multiple']) is not bool):
                return False
    if any(not flags(value['pageHttp'][endpoint], ('requested', 'responded', 'failed'))
           for endpoint in endpoints):
        return False
    status = value['refreshHttpStatus']
    return ((type(status) is str and status in {'UNOBSERVED', 'OTHER'})
            or (type(status) is int and status in {200, 401, 403, 429, 503}))

def is_homework_failure(value):
    rows = value['results']
    return (all(closed_case_row(row) and row['caseId'] in CLIENT_CASES for row in rows)
            and len({row['caseId'] for row in rows}) == len(rows)
            and any(row['status'] == 'failed' and row['errorCount'] > 0
                    and has_event(row, 'DATASET_HTTP', 'DATASET_HOMEWORK_CREATE_HTTP', 503) for row in rows))


def classify_homework(payloads, config):
    hints = set()
    messages = {'Нельзя вызвать binding API без подписанного аутентифицированного пользователя': ('MISSING_ACTOR', 'ACADEMIC_BINDING_IDENTITY'), 'Нельзя вызвать binding API без directed Academic service identity': ('MISSING_DIRECTED_IDENTITY', 'ACADEMIC_BINDING_IDENTITY')}
    actions = {'зарезервировать привязку домашней работы': 'ACADEMIC_BINDING_RESERVE', 'создать ДЗ на календарный день': 'ACADEMIC_DATE_BINDING_RESERVE'}
    sites = {'ru.rutcampustrack.academic.grpc.ScheduleGrpcClient': 'ACADEMIC_SCHEDULE_CLIENT', 'ru.rutcampustrack.academic.homework.HomeworkService': 'ACADEMIC_HOMEWORK', 'ru.rutcampustrack.schedule.grpc.ScheduleGrpcServiceImpl': 'SCHEDULE_GRPC', 'ru.rutcampustrack.schedule.homework.HomeworkBindingService': 'SCHEDULE_BINDING', 'ru.rutcampustrack.gateway.security.InternalJwtIssuerClient': 'GATEWAY_ISSUER', 'ru.rutcampustrack.gateway.security.InternalJwtIssuerFilter': 'GATEWAY_ISSUER_FILTER'}
    statuses = {'OK', 'CANCELLED', 'UNKNOWN', 'INVALID_ARGUMENT', 'DEADLINE_EXCEEDED', 'NOT_FOUND', 'ALREADY_EXISTS', 'PERMISSION_DENIED', 'RESOURCE_EXHAUSTED', 'FAILED_PRECONDITION', 'ABORTED', 'OUT_OF_RANGE', 'UNIMPLEMENTED', 'INTERNAL', 'UNAVAILABLE', 'DATA_LOSS', 'UNAUTHENTICATED'}
    for service, payload in payloads.items():
        if len(payload) > LIMIT or len(payload.splitlines()) > 200:
            return output('UNAVAILABLE_LOG_LIMIT', config[6])
        for line in payload.splitlines():
            if len(line) > 131072:
                return output('UNAVAILABLE_LOG_LIMIT', config[6])
            try:
                record = decode(line)
                if type(record) is not dict:
                    raise ValueError()
            except (ValueError, TypeError):
                return output('UNAVAILABLE_LOG_FORMAT', config[6])
            logger, message = record.get('logger'), record.get('msg')
            if type(message) is not str:
                continue
            if service == 'academic-service' and logger == 'ru.rutcampustrack.academic.exception.GlobalExceptionHandler' and record.get('level') == 'WARN' and message.startswith('schedule-service недоступен: '):
                text = message.removeprefix('schedule-service недоступен: ')
                if text in messages:
                    cause, site = messages[text]
                    hints.add((service, cause, 'NONE', site))
                for action, site in actions.items():
                    match = re.fullmatch(re.escape('Не удалось ' + action + ' в schedule-service: ') + r'Status\{code=([A-Z_]+), description=[\s\S]*, cause=[\s\S]*\}', text)
                    if match and match[1] in statuses:
                        hints.add((service, 'GRPC_STATUS', match[1], site))
            expected = (logger == GATEWAY_LOGGER and bool(re.match(r'^\[[A-Za-z0-9-]{1,64}\]  [45][0-9]{2} Server Error for HTTP ', message))) if service == 'api-gateway' else (logger == 'ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler' and message.startswith('Unhandled exception correlation='))
            expected = expected or (service == 'schedule-service' and logger == 'ru.rutcampustrack.schedule.grpc.GrpcExceptionAdvice' and message == 'gRPC internal error')
            if record.get('level') != 'ERROR' or not expected:
                continue
            stack = record.get('stack')
            if type(stack) is not str or len(stack.encode()) > 131072:
                return output('UNAVAILABLE_EXCEPTION_STACK', config[6])
            category = 'REQUEST_SCOPE' if re.search(r'(?m)^(?:Caused by: )?org\.springframework\.beans\.factory\.support\.ScopeNotActiveException:', stack) else next((label for clazz, label in CLASSES.items() if re.search(r'(?m)^(?:Caused by: )?' + re.escape(clazz) + r'(?::|$)', stack)), 'UNKNOWN')
            rpc = re.search(r'(?m)^(?:Caused by: )?io\.grpc\.Status(?:Runtime)?Exception: ([A-Z_]+)(?::|$)', stack)
            code = rpc[1] if rpc and rpc[1] in statuses else 'NONE'
            if rpc and code != 'NONE' and category == 'UNKNOWN':
                category = 'GRPC_STATUS'
            for clazz, site in sites.items():
                if not clazz.startswith({'academic-service': ('ru.rutcampustrack.academic.',), 'schedule-service': ('ru.rutcampustrack.schedule.',), 'api-gateway': ('ru.rutcampustrack.gateway.',)}[service]):
                    continue
                if re.search(r'(?m)^\s*at ' + re.escape(clazz) + r'\.[A-Za-z_$<>][A-Za-z0-9_$<>]*\(', stack):
                    hints.add((service, category, code, site))
    result = output('ABSENT' if not hints else 'REPORTED' if len(hints) == 1 else 'AMBIGUOUS', config[6])
    result['reportedHints'] = [dict(service=s, exceptionCategory=c, grpcStatus=g, sourceSite=p) for s, c, g, p in sorted(hints)[:8]]
    result['qualification'] = 'Unkeyed bounded log hints; exact request cause remains UNKNOWN'
    return result

def is_target_failure(value, config):
    core = {'status', 'runnerErrors', 'unsafeArtifact', 'suppressedStdoutBytes', 'suppressedStderrBytes', 'results'}
    if (type(value) is not dict or set(value) not in (core, core | {'diagnosticScope'})
            or ('diagnosticScope' in value and value['diagnosticScope'] != 'LOCAL_CLIENT_FIRST_FAILURE')
            or value['status'] != 'failed' or value['unsafeArtifact'] is not False
            or any(type(value[key]) is not int or value[key] != 0 for key in
                   ('runnerErrors', 'suppressedStdoutBytes', 'suppressedStderrBytes'))
            or type(value['results']) is not list or config not in STAGES.values()):
        return False
    stage = next(key for key, selected in STAGES.items() if selected == config)
    rows = value['results']
    if 'diagnosticScope' in value and (stage != 'HOMEWORK_CREATE503' or len(rows) != 1):
        return False
    if not all(closed_case_row(row) and row['caseId'] in TARGET_CASES[stage] for row in rows):
        return False
    if stage == 'HOMEWORK_CREATE503':
        return 1 <= len(rows) <= len(CLIENT_CASES) and is_homework_failure(value)
    if stage == 'STUDENT_STATS503':
        return is_student_stats_failure(value)
    if len(rows) != 1:
        return False
    row = rows[0]
    return (row['status'] == 'failed' and 0 < row['errorCount'] <= 8
            and has_event(row, 'DATASET_HTTP', config[4], 500)
            and (not is_hosted_mark(config) or is_hosted_mark_row(row)))


def is_async_success(value):
    core = {'status', 'runnerErrors', 'unsafeArtifact', 'suppressedStdoutBytes', 'suppressedStderrBytes', 'results'}
    if (type(value) is not dict or set(value) != core or value['status'] != 'passed'
            or value['unsafeArtifact'] is not False
            or any(type(value[key]) is not int or value[key] != 0 for key in
                   ('runnerErrors', 'suppressedStdoutBytes', 'suppressedStderrBytes'))
            or type(value['results']) is not list or len(value['results']) != 1):
        return False
    row = value['results'][0]
    return (closed_case_row(row) and row['caseId'] == 'ASYNC_DOCX_RESUME'
            and row['status'] == row['expectedStatus'] == 'passed' and row['errorCount'] == 0)

def classify_projection(payload, config):
    # An unkeyed log record is a reported operation hint, never exact request causality.
    stage = config[6]
    pattern = re.compile(r'Student projection failed: kind=([A-Z_]+), stage=([A-Z_]+)')
    matches, unusable = [], False
    for line in payload.splitlines():
        if len(line) > 131072:
            unusable = True
            continue
        try:
            record = decode(line)
        except (ValueError, UnicodeError, RecursionError):
            unusable = True
            continue
        if type(record) is not dict:
            unusable = True
            continue
        if (record.get('logger') != 'ru.rutcampustrack.attendance.grpc.AttendanceStudentGrpcServiceImpl'
                or record.get('level') != 'WARN'):
            continue
        message = record.get('msg')
        match = pattern.fullmatch(message) if type(message) is str else None
        if not match or match[1] not in PROJECTION_KINDS or match[2] not in PROJECTION_STAGES:
            unusable = True
            continue
        matches.append((match[1], match[2]))
    if unusable:
        return output('UNAVAILABLE_LOG_FORMAT', stage)
    distinct = sorted(set(matches))
    if len(distinct) > 1:
        pairs = '|'.join(kind + '@' + source for kind, source in distinct)
        return output('AMBIGUOUS', stage, 'REPORTED_PAIRS:' + pairs)
    return (output('UNIQUE', stage + '_' + distinct[0][1], 'REPORTED_' + distinct[0][0])
            if distinct else output('ABSENT', stage))

def classify(payload, config, captured_service=None, request_id=None):
    known_classes = exception_classes(config)
    stage = config[6]
    if config[4] == 'DATASET_ATTENDANCE_MARK_HTTP' and not canonical_request_id(request_id):
        return output('UNAVAILABLE_PRIVATE_INPUT', stage)
    captured_service = config[5] if captured_service is None else captured_service
    if captured_service not in {config[5], 'api-gateway'}:
        return output('UNAVAILABLE_SERVICE_OWNERSHIP', stage)
    gateway = captured_service == 'api-gateway'
    origin = ORIGINS[captured_service]
    if (len(payload) > LIMIT or len(payload.splitlines()) > 200
            or (is_hosted_mark(config) and len(payload.splitlines()) == 200)):
        return output('UNAVAILABLE_LOG_FORMAT' if config[4] == 'STUDENT_STATS_HTTP503'
                      else 'UNAVAILABLE_LOG_LIMIT', stage)
    if config[4] == 'STUDENT_STATS_HTTP503':
        return classify_projection(payload, config)
    frame = re.compile(r'(?m)^[ \t]*at (?:[A-Za-z0-9_.$/@-]{1,256}/)?'
                       + re.escape(config[7] + '(' + config[8] + ':') + r'[1-9][0-9]{0,5}\)')
    throwable = re.compile(r'(?m)^[ \t]*(?:Caused by: )?'
                           r'((?:[A-Za-z_$][A-Za-z0-9_$]*\.)+'
                           r'[A-Za-z_$][A-Za-z0-9_$]*(?:Exception|Error))(?::|$)')
    reported_header = re.compile(r'(?m)^[ \t]*(?:Caused by: )?'
                                 r'((?:[A-Za-z_$][A-Za-z0-9_$]*\.)+'
                                 r'(?:[A-Za-z_$][A-Za-z0-9_$]*)?(?:Exception|Error))(?::[^\r\n]*|$)')
    stack_frame = re.compile(r'(?m)^[ \t]*at (?:[A-Za-z0-9_.$/@-]{1,256}/)?'
                             r'(?:[A-Za-z_$][A-Za-z0-9_$]*\.)+[A-Za-z_$<>][A-Za-z0-9_$<>]*'
                             r'\((?:[A-Za-z0-9_$.-]+\.java:[1-9][0-9]{0,5}|Native Method|Unknown Source)\)[ \t]*$')
    matches, fallback = [], []
    unusable, invalid_stack, fallback_ambiguous = False, False, False
    line_limit = controller_seen = False
    for line in payload.splitlines():
        if len(line) > 131072:
            unusable = line_limit = True
            continue
        try:
            record = decode(line)
        except (ValueError, UnicodeError, RecursionError):
            unusable = True
            continue
        if type(record) is not dict:
            unusable = True
            continue
        if config[4] == 'DATASET_ATTENDANCE_MARK_HTTP' and record.get('requestId') != request_id:
            continue
        expected_logger = GATEWAY_LOGGER if gateway else 'ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler'
        if (record.get('logger') != expected_logger or record.get('level') != 'ERROR'
                or (gateway and record.get('service') != 'api-gateway')):
            continue
        stack = record.get('stack')
        try:
            bounded_stack = type(stack) is str and 0 < len(stack.encode()) <= 131072
        except UnicodeError:
            bounded_stack = False
        if not bounded_stack:
            unusable = invalid_stack = True
            continue
        frames = frame.findall(stack)
        suppressed = bool(re.search(r'(?m)^[ \t]*Suppressed:', stack))
        # Reactor assembly checkpoints are metadata, not an additional causal exception.
        # This allowance is only for reported Gateway hints, never exact-controller UNIQUE.
        if gateway and suppressed:
            suppressed_types = re.findall(r'(?m)^[ \t]*Suppressed: ([A-Za-z0-9_.$]+)(?::|$)', stack)
            suppressed_count = len(re.findall(r'(?m)^[ \t]*Suppressed:', stack))
            suppressed = (len(suppressed_types) != suppressed_count
                          or any(kind != 'reactor.core.publisher.FluxOnAssembly$OnAssemblyException'
                                 for kind in suppressed_types))
        kinds = throwable.findall(stack)
        chain_kinds = reported_header.findall(stack)
        if frames and not gateway:
            controller_seen = True
            if len(frames) != 1 or suppressed:
                return output('AMBIGUOUS', stage)
            if len(chain_kinds) > 16:
                unusable = invalid_stack = True
                continue
            chain = [known_classes.get(kind, 'UNKNOWN') for kind in chain_kinds]
            # Retain the pre-existing exact-controller leaf-category meaning.
            category = known_classes.get(kinds[-1], 'UNKNOWN') if kinds else 'UNKNOWN'
            class_chain = [kind if kind in known_classes else 'UNKNOWN' for kind in chain_kinds]
            matches.append((category, chain, reported_own_frame(stack, config), class_chain))
            continue
        if suppressed:
            fallback_ambiguous = True
            continue
        if (not 1 <= len(chain_kinds) <= 16 or not reported_header.match(stack)
                or not stack_frame.search(stack)):
            unusable = invalid_stack = True
            continue
        chain = [known_classes.get(kind, 'UNKNOWN') for kind in chain_kinds]
        class_chain = [kind if kind in known_classes else 'UNKNOWN' for kind in chain_kinds]
        fallback.append((chain[-1], chain, reported_own_frame(stack, config, captured_service), class_chain))
    if matches:
        if len(matches) != 1:
            return output('AMBIGUOUS', stage)
        category, chain, own_frame, class_chain = matches[0]
        return output('REPORTED_PARTIAL_LOG_FORMAT' if unusable else 'UNIQUE',
                      stage, category, chain, own_frame, origin, class_chain)
    if controller_seen:
        return output('UNAVAILABLE_EXCEPTION_STACK', stage)
    # No request correlation is inferred from a Global-handler ERROR alone.
    if fallback_ambiguous or len(fallback) > 1:
        return output('AMBIGUOUS', stage)
    if fallback:
        category, chain, own_frame, class_chain = fallback[0]
        if gateway:
            return output('REPORTED_GATEWAY_PARTIAL_LOG_FORMAT' if unusable else 'REPORTED_GATEWAY',
                          stage, category, chain, own_frame, origin, class_chain)
        return output('REPORTED_PARTIAL_LOG_FORMAT_NO_CONTROLLER' if unusable
                      else 'REPORTED_NO_CONTROLLER', stage, category, chain, own_frame, origin, class_chain)
    if gateway and unusable and not invalid_stack and not line_limit:
        return output('UNAVAILABLE_LOG_FORMAT', stage)
    return output('UNAVAILABLE_EXCEPTION_STACK' if invalid_stack else
                  'UNAVAILABLE_LOG_LIMIT' if line_limit else 'UNAVAILABLE_NO_ERROR_RECORD', stage)


def combine(domain, gateway, config):
    # No correlation key is collected: a second eligible origin defeats any unique hint.
    def candidate(value):
        return (value['reportedOrigin'] != 'UNKNOWN'
                or value['state'] in {'AMBIGUOUS', 'UNAVAILABLE_EXCEPTION_STACK'})
    selected = [value for value in (domain, gateway) if candidate(value)]
    if len(selected) > 1:
        return output('AMBIGUOUS', config[6])
    if selected:
        value = selected[0]
        other = gateway if value is domain else domain
        if other['state'] != 'UNAVAILABLE_NO_ERROR_RECORD':
            partial = {'UNIQUE': 'REPORTED_PARTIAL_LOG_FORMAT',
                       'REPORTED_NO_CONTROLLER': 'REPORTED_PARTIAL_LOG_FORMAT_NO_CONTROLLER',
                       'REPORTED_GATEWAY': 'REPORTED_GATEWAY_PARTIAL_LOG_FORMAT'}
            value = dict(value, state=partial.get(value['state'], value['state']))
        return value
    for state in ('UNAVAILABLE_LOG_LIMIT', 'UNAVAILABLE_LOG_FORMAT'):
        if any(value['state'] == state for value in (domain, gateway)):
            return output(state, config[6])
    return domain

def ambiguity_evidence(payloads, config, domain, gateway, request_id=None):
    known_classes = exception_classes(config)
    # Unkeyed facts from the verified captured streams; never request causality.
    # Keep classify/combine unchanged so evidence cannot promote or veto their result.
    header = re.compile(r'(?m)^[ \t]*(?:Caused by: )?'
                        r'((?:[A-Za-z_$][A-Za-z0-9_$]*\.)+'
                        r'(?:[A-Za-z_$][A-Za-z0-9_$]*)?(?:Exception|Error))(?::[^\r\n]*|$)')
    stack_frame = re.compile(r'(?m)^[ \t]*at (?:[A-Za-z0-9_.$/@-]{1,256}/)?'
                            r'(?:[A-Za-z_$][A-Za-z0-9_$]*\.)+[A-Za-z_$<>][A-Za-z0-9_$<>]*'
                            r'\((?:[A-Za-z0-9_$.-]+\.java:[1-9][0-9]{0,5}|Native Method|Unknown Source)\)[ \t]*$')
    controller = re.compile(r'(?m)^[ \t]*at (?:[A-Za-z0-9_.$/@-]{1,256}/)?'
                           + re.escape(config[7] + '(' + config[8] + ':') + r'[1-9][0-9]{0,5}\)')
    cross_origin = all(value['reportedOrigin'] != 'UNKNOWN'
                       or value['state'] in {'AMBIGUOUS', 'UNAVAILABLE_EXCEPTION_STACK'}
                       for value in (domain, gateway))
    evidence = []
    for service in (config[5], 'api-gateway'):
        payload = payloads[service]
        reasons = {'CROSS_ORIGIN_CANDIDATES'} if cross_origin else set()
        classes, frames = set(), set()
        seen = False
        controller_records = fallback_records = 0
        lines = payload.splitlines()
        if len(payload) > LIMIT or len(lines) > 200:
            reasons.add('LOG_LIMIT')
        else:
            for line in lines:
                if len(line) > 131072:
                    reasons.add('LOG_LIMIT')
                    continue
                try:
                    record = decode(line)
                except (ValueError, UnicodeError, RecursionError):
                    reasons.add('MALFORMED_LOG_RECORD')
                    continue
                if type(record) is not dict:
                    reasons.add('MALFORMED_LOG_RECORD')
                    continue
                if config[4] == 'DATASET_ATTENDANCE_MARK_HTTP' and record.get('requestId') != request_id:
                    continue
                is_gateway = service == 'api-gateway'
                logger = GATEWAY_LOGGER if is_gateway else 'ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler'
                if (record.get('logger') != logger or record.get('level') != 'ERROR'
                        or (is_gateway and record.get('service') != 'api-gateway')):
                    continue
                seen = True
                stack = record.get('stack')
                if type(stack) is not str or not stack:
                    reasons.add('MISSING_EXCEPTION_STACK')
                    continue
                try:
                    size = len(stack.encode())
                except UnicodeError:
                    reasons.add('INVALID_EXCEPTION_STACK')
                    continue
                if size > 131072:
                    reasons.add('STACK_LIMIT')
                    continue
                kinds = header.findall(stack)
                classes.update(kind for kind in kinds if kind in known_classes)
                if any(kind not in known_classes for kind in kinds):
                    reasons.add('UNMAPPED_EXCEPTION_CLASS')
                own_frame = reported_own_frame(stack, config, service)
                if own_frame is not None:
                    frames.add((own_frame['symbol'], own_frame['line']))
                suppressed_types = re.findall(r'(?m)^[ \t]*Suppressed: ([A-Za-z0-9_.$]+)(?::|$)', stack)
                suppressed_count = len(re.findall(r'(?m)^[ \t]*Suppressed:', stack))
                classes.update(kind for kind in suppressed_types if kind in known_classes)
                assembly = 'reactor.core.publisher.FluxOnAssembly$OnAssemblyException'
                if any(kind not in known_classes and not (is_gateway and kind == assembly)
                       for kind in suppressed_types):
                    reasons.add('UNMAPPED_EXCEPTION_CLASS')
                suppressed = (bool(suppressed_count) and (not is_gateway
                              or len(suppressed_types) != suppressed_count
                              or any(kind != assembly for kind in suppressed_types)))
                if suppressed:
                    reasons.add('UNQUALIFIED_SUPPRESSION')
                matches = controller.findall(stack)
                if matches and not is_gateway:
                    controller_records += 1
                    if len(matches) != 1:
                        reasons.add('MULTIPLE_CONTROLLER_FRAMES')
                    if len(kinds) > 16:
                        reasons.add('STACK_LIMIT')
                    continue
                if suppressed:
                    continue
                if not 1 <= len(kinds) <= 16 or not header.match(stack) or not stack_frame.search(stack):
                    reasons.add('STACK_LIMIT' if len(kinds) > 16 else 'INVALID_EXCEPTION_STACK')
                    continue
                fallback_records += 1
        if not seen and 'LOG_LIMIT' not in reasons:
            reasons.add('NO_ELIGIBLE_ERROR_RECORD')
        if controller_records > 1:
            reasons.add('MULTIPLE_CONTROLLER_RECORDS')
        if fallback_records > 1:
            reasons.add('MULTIPLE_FALLBACK_RECORDS')
        if len(frames) > 16:
            reasons.add('EVIDENCE_LIMIT')
        assert reasons <= AMBIGUITY_REASONS
        evidence.append({'capturedOrigin': ORIGINS[service], 'reasons': sorted(reasons),
                         'exceptionClasses': sorted(classes),
                         'ownFrames': [{'symbol': symbol, 'line': line}
                                       for symbol, line in sorted(frames)[:16]]})
    return evidence


def canonical_request_id(value):
    return type(value) is str and re.fullmatch(
        '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', value) is not None

def load_mark_request_id(config, context=None):
    scope = selected_scope(config, context)
    value = owned_bytes(scope.run / 'mark-http500.request-id', 64).decode('ascii').removesuffix('\n')
    if not canonical_request_id(value):
        raise ValueError('Missing canonical MARK witness')
    return value

def closed_ordinary_mark_result(value):
    # Artifact data is a second allowlist boundary; no request/log text is accepted.
    keys = {'schema', 'state', 'exceptionCategory', 'sourceStage', 'reportedExceptionChain',
            'reportedOwnFrame', 'reportedOrigin', 'reportedExceptionClassChain',
            'reportedAmbiguityEvidence', 'reportedMongo'}
    states = {'UNAVAILABLE_PRIVATE_INPUT', 'UNAVAILABLE_TARGET_SIGNATURE',
              'UNAVAILABLE_SERVICE_OWNERSHIP', 'UNAVAILABLE_LOG_CAPTURE',
              'UNAVAILABLE_LOG_FORMAT', 'UNAVAILABLE_LOG_LIMIT',
              'UNAVAILABLE_EXCEPTION_STACK', 'UNAVAILABLE_NO_ERROR_RECORD', 'AMBIGUOUS',
              'UNIQUE', 'REPORTED_PARTIAL_LOG_FORMAT', 'REPORTED_NO_CONTROLLER',
              'REPORTED_PARTIAL_LOG_FORMAT_NO_CONTROLLER', 'REPORTED_GATEWAY',
              'REPORTED_GATEWAY_PARTIAL_LOG_FORMAT'}
    def sequence(items, allowed, bound):
        return (type(items) is list and len(items) <= bound
                and all(type(item) is str and item in allowed for item in items))
    def frame(item):
        return (type(item) is dict and set(item) == {'symbol', 'line'}
                and type(item['symbol']) is str and item['symbol'] in ORDINARY_MARK_FRAME_SOURCES
                and type(item['line']) is int
                and 0 < item['line'] <= 1000000)
    if (type(value) is not dict or set(value) != keys
            or value['schema'] != 'attendance-mark-http500.v1'
            or value['state'] not in states or value['sourceStage'] != 'ATTENDANCE_MARK'
            or value['exceptionCategory'] not in set(HOSTED_CLASSES.values()) | {'UNKNOWN'}
            or value['reportedOrigin'] not in {'ATTENDANCE_MVC', 'GATEWAY_WEBFLUX', 'UNKNOWN'}
            or not sequence(value['reportedExceptionChain'], set(HOSTED_CLASSES.values()) | {'UNKNOWN'}, 16)
            or not sequence(value['reportedExceptionClassChain'], set(HOSTED_CLASSES) | {'UNKNOWN'}, 16)
            or (value['reportedOwnFrame'] is not None and not frame(value['reportedOwnFrame']))):
        return False
    evidence = value['reportedAmbiguityEvidence']
    if type(evidence) is not list or len(evidence) > 2:
        return False
    for item in evidence:
        if (type(item) is not dict or set(item) != {'capturedOrigin', 'reasons', 'exceptionClasses', 'ownFrames'}
                or item['capturedOrigin'] not in {'ATTENDANCE_MVC', 'GATEWAY_WEBFLUX'}
                or not sequence(item['reasons'], AMBIGUITY_REASONS, len(AMBIGUITY_REASONS))
                or not sequence(item['exceptionClasses'], set(HOSTED_CLASSES), len(HOSTED_CLASSES))
                or type(item['ownFrames']) is not list or len(item['ownFrames']) > 16
                or not all(frame(own) for own in item['ownFrames'])):
            return False
    mongo = value['reportedMongo']
    return (type(mongo) is dict and set(mongo) == {'state', 'code', 'serverLabels', 'site'}
            and mongo['state'] in {'UNKNOWN', 'AMBIGUOUS', 'REPORTED'}
            and mongo['code'] in {'UNKNOWN', 'OTHER', 'WRITE_CONFLICT', 'DUPLICATE_KEY',
                                 'NO_SUCH_TRANSACTION', 'UNAUTHORIZED', 'AUTHENTICATION_FAILED'}
            and mongo['site'] in {'UNKNOWN', 'BEGIN', 'BODY', 'COMMIT', 'ROLLBACK'}
            and (mongo['serverLabels'] is None or sequence(mongo['serverLabels'],
                 {'TRANSIENT_TRANSACTION_ERROR', 'UNKNOWN_TRANSACTION_COMMIT_RESULT'}, 2)))

def finalize_ordinary_mark_result(value):
    value = dict(value, schema='attendance-mark-http500.v1')
    value.setdefault('reportedMongo', mongo_unknown())
    state = None
    if not closed_ordinary_mark_result(value):
        state = 'UNAVAILABLE_LOG_FORMAT'
    elif len((json.dumps(value, sort_keys=True) + '\n').encode()) > 4096:
        state = 'UNAVAILABLE_LOG_LIMIT'
    if state is not None:
        value = dict(output(state, 'ATTENDANCE_MARK'), schema='attendance-mark-http500.v1',
                     reportedMongo=mongo_unknown())
    return value

def persist_ordinary_mark_result(config, value, context=None):
    if not is_ordinary_mark(config) or not closed_ordinary_mark_result(value):
        raise ValueError('Ordinary MARK result is not closed')
    scope = selected_scope(config, context)
    for directory in (scope.run, scope.run / 'artifacts'):
        owned_directory(directory)
    if owned_bytes(scope.run / 'disposable-target', 64) != b'RCT_SEMESTER_DISPOSABLE\n':
        raise ValueError('Invalid disposable marker')
    payload = (json.dumps(value, sort_keys=True) + '\n').encode()
    if len(payload) > 4096:
        raise ValueError('Closed MARK result exceeds bound')
    descriptor = os.open(scope.run / 'mark-http500.safe.json', os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, 'wb') as target:
        target.write(payload)

def load_failure(config, initial_membership=False, context=None):
    scope = selected_scope(config, context)
    if initial_membership and config[4] != 'DATASET_ATTENDANCE_REPORT_HTTP':
        raise ValueError('Initial membership diagnostic unavailable')
    work = scope.run
    for directory in [work] + [work.joinpath(*config[2][:i]) for i in range(1, len(config[2]) + 1)]:
        owned_directory(directory)
    path = work / 'reconciliation.safe.json' if initial_membership else work.joinpath(*config[2], 'safe-results.json')
    return decode(owned_bytes(path, 32768))

def collect(config, initial_membership=False, mark_window=None, context=None):
    scope = selected_scope(config, context)
    global result
    deadline, remaining_bytes = time.monotonic() + 15, LIMIT
    def remaining():
        seconds = deadline - time.monotonic()
        if seconds <= 0:
            raise TimeoutError('Diagnostic deadline')
        return seconds
    def call(argv, merge_stderr=False):
        nonlocal remaining_bytes
        payload = bytearray()
        with subprocess.Popen(argv, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT if merge_stderr else subprocess.DEVNULL, cwd=ROOT, env=scope.environment) as child:
            try:
                with selectors.DefaultSelector() as ready:
                    ready.register(child.stdout, selectors.EVENT_READ)
                    while True:
                        if not ready.select(remaining()):
                            raise TimeoutError('Diagnostic deadline')
                        chunk = os.read(child.stdout.fileno(), min(65536, remaining_bytes + 1))
                        if not chunk:
                            break
                        remaining_bytes -= len(chunk)
                        if remaining_bytes < 0:
                            raise ValueError('Diagnostic byte limit')
                        payload.extend(chunk)
                if child.wait(timeout=remaining()):
                    raise ValueError('Diagnostic command failed')
            finally:
                if child.poll() is None:
                    child.kill()
                child.wait(timeout=5)
        return bytes(payload)
    compose = scope.compose

    services = ('attendance-service',) if initial_membership else (('academic-service', 'schedule-service', 'api-gateway') if config[4] == 'DATASET_HOMEWORK_CREATE_HTTP' else ((config[5],) if config[4] == 'STUDENT_STATS_HTTP503' else (config[5], 'api-gateway')))
    payloads = {}
    for service in services:
        cid = call([*compose, 'ps', '-q', service]).decode().strip()
        if not re.fullmatch('[0-9a-f]{64}', cid):
            raise ValueError('Owned service unavailable')
        template = '[{{json .Id}},{{json (index .Config.Labels "com.docker.compose.project")}},{{json (index .Config.Labels "com.docker.compose.service")}},{{json .State.Running}},{{json (index .Config.Labels "rct.local.stage17")}}]'
        started_at = None
        if initial_membership:
            template = template[:-1] + ',{{json .State.StartedAt}}]'
        def verify():
            nonlocal started_at
            identity = decode(call(['docker', 'inspect', '--format', template, cid]))
            if initial_membership:
                if (type(identity) is not list or len(identity) != 6
                        or identity[:5] != [cid, scope.project, service, True, scope.nonce or '']
                        or type(identity[5]) is not str
                        or not re.fullmatch(r'[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,9})?Z', identity[5])):
                    raise ValueError('Owned service start unavailable')
                if started_at is None:
                    started_at = identity[5]
                if identity[5] != started_at:
                    raise ValueError('Owned service restarted')
            elif identity != [cid, scope.project, service, True, scope.nonce or '']:
                raise ValueError('Owned service changed')
        verify()
        result = initial_membership_output() if initial_membership else output('UNAVAILABLE_LOG_CAPTURE', config[6])
        if is_hosted_mark(config):
            if initial_membership or mark_window is None:
                raise ValueError('Missing hosted MARK window')
            argv = ['docker', 'logs', '--since', mark_window[0], '--until', mark_window[1], '--tail', '200', cid]
        else:
            argv = ['docker', 'logs', '--since', started_at if initial_membership else '180s', '--tail', '2000' if initial_membership else '200', cid]
        payload = call(argv, True)
        result = initial_membership_output() if initial_membership else output('UNAVAILABLE_SERVICE_OWNERSHIP', config[6])
        verify()
        payloads[service] = payload
    return payloads


def run_diagnostic(stage, context):
    global result
    result = output('UNAVAILABLE_PRIVATE_INPUT', 'UNKNOWN')
    try:
        config = STAGES[stage]
        selected_scope(config, context)
        result = output('UNAVAILABLE_PRIVATE_INPUT', config[6])
        initial_failure = False
        if config[4] == 'DATASET_ATTENDANCE_REPORT_HTTP':
            try:
                initial_failure = is_initial_membership_failure(load_failure(config, initial_membership=True, context=context))
            except Exception:
                pass
        if initial_failure:
            result = initial_membership_output()
            payloads = collect(config, initial_membership=True, context=context)
            result = classify_initial_membership(payloads['attendance-service'])
        else:
            failure = load_failure(config, context=context)
            result = output('UNAVAILABLE_TARGET_SIGNATURE', config[6])
            if is_target_failure(failure, config):
                request_id = None
                mark_window = None
                if config[4] == 'DATASET_ATTENDANCE_MARK_HTTP':
                    result = output('UNAVAILABLE_PRIVATE_INPUT', config[6])
                    if is_hosted_mark(config):
                        request_id, mark_window = load_hosted_mark_witness(config, context=context)
                    else:
                        request_id = load_mark_request_id(config, context=context)
                result = output('UNAVAILABLE_SERVICE_OWNERSHIP', config[6])
                payloads = collect(config, mark_window=mark_window, context=context) if is_hosted_mark(config) else collect(config, context=context)
                result = output('UNAVAILABLE_LOG_FORMAT', config[6])
                if config[4] == 'DATASET_HOMEWORK_CREATE_HTTP':
                    result = classify_homework(payloads, config)
                elif config[4] == 'STUDENT_STATS_HTTP503':
                    result = classify(payloads[config[5]], config)
                else:
                    domain = classify(payloads[config[5]], config, request_id=request_id)
                    gateway = classify(payloads['api-gateway'], config, 'api-gateway', request_id=request_id)
                    result = combine(domain, gateway, config)
                    result['reportedAmbiguityEvidence'] = ambiguity_evidence(payloads, config, domain, gateway, request_id=request_id)
                    if is_hosted_mark(config) or is_ordinary_mark(config):
                        result['reportedMongo'] = hosted_mongo(payloads, config, result, request_id)
    except Exception as error:
        if 'initial_failure' in locals() and initial_failure:
            reason = {
                'Diagnostic byte limit': 'CAPTURE_BYTE_LIMIT',
                'Diagnostic command failed': 'CAPTURE_COMMAND_FAILURE',
                'Owned service unavailable': 'OWNED_SERVICE_UNAVAILABLE',
                'Owned service start unavailable': 'OWNERSHIP_UNAVAILABLE',
                'Owned service changed': 'OWNERSHIP_CHANGED',
                'Owned service restarted': 'OWNERSHIP_CHANGED',
            }.get(str(error), 'CAPTURE_UNAVAILABLE')
            if isinstance(error, (TimeoutError, subprocess.TimeoutExpired)):
                reason = 'CAPTURE_TIMEOUT'
            elif isinstance(error, (UnicodeError, json.JSONDecodeError)):
                reason = 'CAPTURE_FORMAT'
            result = initial_membership_output(unknown_reason=reason)
    if 'config' in locals() and is_hosted_mark(config):
        result.setdefault('reportedMongo', mongo_unknown())
    if 'config' in locals() and is_ordinary_mark(config):
        result = finalize_ordinary_mark_result(result)
        try:
            persist_ordinary_mark_result(config, result, context=context)
        except Exception:
            # Diagnostic refusal cannot replace the original failed operation.
            pass
    return result


def receipt_pairs(items):
    value = {}
    for key, item in items:
        if key in value:
            raise ValueError('Duplicate receipt field')
        value[key] = item
    return value

def closed_privacy_receipt(value):
    def flags(item, keys):
        return (type(item) is dict and set(item) == set(keys)
                and all(type(item[key]) is bool for key in keys))
    def boundary(item):
        keys = {'stage', 'markerAbsent', 'failureRetained', 'attachmentNotYetCreated', 'interrupted'}
        return (item is None or (type(item) is dict and set(item) == keys
                and item['stage'] == 'AUTOMATIC_CONTEXT_BEFORE_ATTACHMENT'
                and flags({key: item[key] for key in keys - {'stage'}}, keys - {'stage'})))
    if type(value) is not dict or value.get('browser') != 'NOT_STARTED':
        return False
    if value.get('state') == 'CONTROL_EXECUTION_ERROR':
        return set(value) == {'state', 'rawErrorSuppressed', 'browser'} and value['rawErrorSuppressed'] is True
    boolean_keys = {'markerAbsent', 'originalMetadataLeakReproduced', 'expectedFailureRetained',
                    'successRetained', 'interruptionPassed', 'missingGuardRejectedBeforeOperation', 'startupPassed'}
    keys = boolean_keys | {'state', 'normalChildStatus', 'producerBoundary', 'interruptedChildStatus',
                           'interruptedChildSignal', 'interruptionBoundary', 'browser'}
    if (set(value) not in (keys, keys | {'guardControl'})
            or value['state'] not in {'PRIVACY_BOUNDARIES_PASSED', 'PRIVACY_CONTROL_FAILED'}
            or not all(type(value[key]) is bool for key in boolean_keys)
            or (value['state'] == 'PRIVACY_BOUNDARIES_PASSED' and value['startupPassed'] is not True)
            or not boundary(value['producerBoundary']) or not boundary(value['interruptionBoundary'])
            or value['interruptedChildSignal'] not in {None, 'SIGTERM', 'SIGKILL', 'SIGINT', 'SIGABRT', 'SIGSEGV'}
            or any(value[key] is not None and (type(value[key]) is not int or not 0 <= value[key] <= 255)
                   for key in ('normalChildStatus', 'interruptedChildStatus'))):
        return False
    guard_keys = {'canonicalReporterAccepted', 'sameBasenameAlternateRejected', 'extraReporterRejected',
                  'traceRejected', 'videoRejected', 'screenshotRejected', 'missingSnapshotGuardRejected',
                  'wrongSnapshotGuardRejected'}
    return 'guardControl' not in value or flags(value['guardControl'], guard_keys)


def source_manifest():
    paths = {Path(__file__).resolve(), Path(__file__).with_name('http-diagnostic-cases.json').resolve(),
             ROOT / 'tests/e2e/fixtures/safe-reporter.ts', ROOT / 'tests/e2e/fixtures/mobile-dataset.ts',
             ROOT / 'tests/e2e/fixtures/artifact-privacy.ts'}
    paths.update((ROOT / 'tests/e2e/fixtures').glob('diagnostic-*.ts'))
    paths.update(ROOT / relative for relative in (
        'scripts/local_e2e_async.py', 'tests/e2e/fixtures/semester-lifecycle.spec.ts',
        'tests/e2e/fixtures/assignment-lifecycle.spec.ts', 'tests/e2e/specs/mobile-real-server.spec.ts',
        'tests/e2e/specs/mobile-schedule-lifecycle.spec.ts', 'tests/e2e/specs/mobile-lesson-lifecycle.spec.ts',
        'tests/e2e/specs/mobile-attendance-requests.spec.ts'))
    return {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted(paths) if path.is_file()}


def privacy_receipt_status(status):
    if type(status) is not int or not 0 <= status <= 255:
        raise ValueError('Invalid control status')
    run, attempt = os.environ['GITHUB_RUN_ID'], os.environ['GITHUB_RUN_ATTEMPT']
    if not re.fullmatch('[1-9][0-9]{0,19}', run) or not re.fullmatch('[1-9][0-9]{0,5}', attempt):
        raise ValueError('Invalid receipt identity')
    path = Path(os.environ['RCT_E2E_CONTROL_RECEIPT_PATH'])
    if path != Path(os.environ['RUNNER_TEMP']) / ('rct-artifact-privacy-client-' + run + '-' + attempt + '.safe.json'):
        raise ValueError('Foreign receipt')
    if not closed_privacy_receipt(decode(owned_bytes(path, 4096))):
        raise ValueError('Invalid receipt')
    with open(os.environ['GITHUB_OUTPUT'], 'a') as target:
        target.write('safe_receipt=true\n')
    return status


class ClosedArgumentParser(argparse.ArgumentParser):
    def error(self, _message):
        print(json.dumps({'state': 'INVALID_ARGUMENTS'}))
        raise SystemExit(2)


def main(argv=None):
    parser = ClosedArgumentParser(description=__doc__)
    parser.add_argument('--stage')
    parser.add_argument('--context', choices=('ci', 'local'), default='ci')
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--run-dir', type=Path)
    parser.add_argument('--validate-report', type=Path, help='Offline closed report admission only; no runtime calls')
    parser.add_argument('--privacy-receipt-status', type=int)
    args = parser.parse_args(argv)
    if args.root.resolve() != ROOT:
        raise ValueError('Checkout root differs from executing module')
    if args.privacy_receipt_status is not None:
        if args.stage or args.run_dir or args.validate_report:
            raise ValueError('Mixed diagnostic modes')
        return privacy_receipt_status(args.privacy_receipt_status)
    if args.stage not in STAGES:
        raise ValueError('Unknown diagnostic stage')
    config = STAGES[args.stage]
    if args.validate_report is not None:
        value = decode(owned_bytes(args.validate_report, 32768))
        accepted = is_target_failure(value, config)
        print(json.dumps({'schema': 'rct-diagnostic-admission.v1', 'stage': args.stage, 'accepted': accepted}))
        return 0 if accepted else 1
    before = source_manifest()
    source_revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT,
                                              stderr=subprocess.DEVNULL, text=True, timeout=5).strip()
    if not re.fullmatch('[0-9a-f]{40}', source_revision):
        raise ValueError('Source revision unavailable')
    try:
        context = admit_scope(config, args.context, args.run_dir)
        observed = run_diagnostic(args.stage, context)
    except Exception:
        observed = output('UNAVAILABLE_PRIVATE_INPUT', config[6])
        if is_ordinary_mark(config):
            observed = finalize_ordinary_mark_result(observed)
    unchanged = before == source_manifest()
    if not unchanged:
        observed = output('UNAVAILABLE_LOG_FORMAT', config[6])
        if is_ordinary_mark(config):
            observed = finalize_ordinary_mark_result(observed)
    # A fresh manifest is observed before capture, never a manually maintained future-source hash.
    print(json.dumps({'schema': 'rct-diagnostic-provenance.v1', 'sourceRevision': source_revision,
                      'sourceManifest': before, 'sourceUnchanged': unchanged, 'context': args.context}, sort_keys=True))
    print(json.dumps(observed, sort_keys=True))
    return 0  # Supplemental diagnosis must not replace the original failed test's nonzero exit.


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception:
        print(json.dumps({'state': 'DIAGNOSTIC_INPUT_REJECTED'}))
        raise SystemExit(1)

