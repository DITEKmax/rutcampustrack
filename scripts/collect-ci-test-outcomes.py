"""Collect privacy-safe JUnit identities/outcomes; never copy XML text or display names."""

import argparse
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path
from xml.etree import ElementTree

MODULES = {
    "api-gateway": "services/api-gateway",
    "auth-service": "services/auth-service/auth-app",
    "academic-service": "services/academic-service/academic-app",
    "schedule-service": "services/schedule-service/schedule-app",
    "attendance-service": "services/attendance-service/attendance-app",
    "notification-service": "services/notification-service/notification-app",
    "mobile-bff": "services/mobile-bff/mobile-bff-app",
}
TEST_ANNOTATIONS = {"Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate"}
IDENTIFIER = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*\Z")
MAX_XML_BYTES = 32 * 1024 * 1024
CAP_CLASS = "ru.rutcampustrack.schedule.integration.RecurringScheduleItemIT"
CAP_METHOD = "capNarrowingCommitIsFreshBeforeBlockedPhysicalInsert"
CAP_FAILURE_CLASSES = (
    "OTHER",
    "AssertionError",
    "CannotAcquireLockException",
    "DeadlockLoserDataAccessException",
    "PessimisticLockingFailureException",
    "DataIntegrityViolationException",
    "BadSqlGrammarException",
    "DataAccessResourceFailureException",
    "UncategorizedSQLException",
    "CannotGetJdbcConnectionException",
    "TransactionSystemException",
    "IllegalStateException",
    "SQLException",
    "PSQLException",
)
CAP_SQL_STATES = ("NONE", "OTHER", "40P01", "55P03", "40001", "23502", "23503", "23505", "23514", "P0001")
CAP_MARKER = re.compile(
    r"(?:(?:java\.lang\.AssertionError|org\.opentest4j\.AssertionFailedError):\s*)?"
    r"CAP_FUTURE_DIAGNOSTIC_V1 class=(" + "|".join(CAP_FAILURE_CLASSES) + r") "
    r"sqlState=(" + "|".join(CAP_SQL_STATES) + r") barrier=(NOT_OBSERVED|TIMED_OUT|INTERRUPTED)"
)
# Exact native exception classes map to closed categories; never emit class/message text.
NOTIFICATION_FAILURE_CLASSES = {
    "INIT": ("java.lang.ExceptionInInitializerError", "java.lang.IllegalStateException"),
    "NO_CLASS": ("java.lang.NoClassDefFoundError", "java.lang.ClassNotFoundException"),
    "BEAN": (
        "org.springframework.beans.factory.BeanCreationException",
        "org.springframework.beans.BeanInstantiationException",
    ),
    "DEPENDENCY": ("org.springframework.beans.factory.UnsatisfiedDependencyException",),
    "CONTAINER": (
        "org.testcontainers.containers.ContainerLaunchException",
        "org.testcontainers.containers.ContainerFetchException",
    ),
    "MONGO": (
        "com.mongodb.MongoException",
        "com.mongodb.MongoTimeoutException",
        "com.mongodb.MongoSocketOpenException",
        "com.mongodb.MongoSocketReadException",
        "com.mongodb.MongoSecurityException",
        "com.mongodb.MongoCommandException",
        "com.mongodb.MongoConfigurationException",
    ),
    "AMQP": (
        "org.springframework.amqp.AmqpException",
        "org.springframework.amqp.AmqpIOException",
        "org.springframework.amqp.AmqpConnectException",
        "org.springframework.amqp.AmqpAuthenticationException",
        "org.springframework.amqp.rabbit.listener.exception.FatalListenerStartupException",
        "org.springframework.amqp.rabbit.support.ListenerExecutionFailedException",
        "com.rabbitmq.client.AuthenticationFailureException",
        "com.rabbitmq.client.PossibleAuthenticationFailureException",
        "com.rabbitmq.client.ShutdownSignalException",
        "com.rabbitmq.client.AlreadyClosedException",
    ),
    "OOM": ("java.lang.OutOfMemoryError",),
    "ASSERTION": ("java.lang.AssertionError", "org.opentest4j.AssertionFailedError"),
}
NOTIFICATION_FAILURE_CODES = {name: code for code, names in NOTIFICATION_FAILURE_CLASSES.items() for name in names}
NOTIFICATION_CAUSE_HEADER = re.compile(
    r"(?m)^[ \t]{0,32}Caused by:[ \t]{1,8}([A-Za-z_$][A-Za-z0-9_.$]{0,255})(?=:|\r?$)"
)
NOTIFICATION_DIAGNOSTIC_CHARS = 65536
# Gradle wrapper types/exit formatter are verified against public Gradle v8.12.0.
# A suite wrapper can also wrap assertion/context failures; codes are not causes.
ATTENDANCE_FAILURE_CLASSES = {
    "GRADLE_SUITE": ("org.gradle.api.internal.tasks.testing.TestSuiteExecutionException",),
    "GRADLE_WORKER": ("org.gradle.process.internal.worker.WorkerProcessException",),
    "PROCESS_EXEC": ("org.gradle.process.internal.ExecException",),
    "ASSERTION": NOTIFICATION_FAILURE_CLASSES["ASSERTION"],
    "INITIALIZER": ("java.lang.ExceptionInInitializerError",),
    "ILLEGAL_STATE": ("java.lang.IllegalStateException",),
    "NO_CLASS": NOTIFICATION_FAILURE_CLASSES["NO_CLASS"],
    "CONTEXT": NOTIFICATION_FAILURE_CLASSES["BEAN"],
    "DEPENDENCY": NOTIFICATION_FAILURE_CLASSES["DEPENDENCY"],
    "CONTAINER": NOTIFICATION_FAILURE_CLASSES["CONTAINER"],
    "MONGO": NOTIFICATION_FAILURE_CLASSES["MONGO"],
    "AMQP": NOTIFICATION_FAILURE_CLASSES["AMQP"],
    "IO": ("java.io.IOException",),
    "OOM": NOTIFICATION_FAILURE_CLASSES["OOM"],
}
ATTENDANCE_FAILURE_CODES = {name: code for code, names in ATTENDANCE_FAILURE_CLASSES.items() for name in names}
ATTENDANCE_DIAGNOSTIC_CHARS = 65536
ATTENDANCE_EXEC_TYPE = "org.gradle.process.internal.ExecException"
ATTENDANCE_EXEC_MESSAGE = re.compile(
    r"Process '[^'\r\n]{1,512}' finished with non-zero exit value ([1-9][0-9]{0,2})"
)
ATTENDANCE_EXEC_HEADER = re.compile(
    r"(?m)^[ \t]{0,32}(?:Caused by:[ \t]{1,8})?"
    + re.escape(ATTENDANCE_EXEC_TYPE)
    + r": "
    + ATTENDANCE_EXEC_MESSAGE.pattern
    + r"\r?$"
)
ACADEMIC_SCHEMA_CLASS = "ru.rutcampustrack.academic.homework.HomeworkStudentCompletionConcurrencyIT"
ACADEMIC_SCHEMA_METHOD = "currentCompletionUnionReturnsOldLessonOnceWithStoredTimestamp"
ACADEMIC_SCHEMA_SOURCE = "services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java"
# Closed names verified against current Academic (22) and Auth (2) @Entity/@Table mappings.
# Auth names identify accidental cross-owner mappings without exposing arbitrary relations.
ACADEMIC_SCHEMA_TABLES = (
    "academic_outbox",
    "academic_role_directory",
    "academic_user_directory",
    "assignment_replacement_operations",
    "assignments",
    "attendance_thresholds",
    "auth_identities",
    "auth_outbox",
    "campus_settings",
    "event_consumer_processed",
    "group_history_coverage",
    "group_promotion_cycle_record",
    "groups",
    "headman_assistants",
    "homework_binding_archives",
    "homework_completions",
    "homework_weekly_digest_runs",
    "homeworks",
    "semester_archive_operations",
    "semesters",
    "student_group_history",
    "subject_lesson_types",
    "subjects",
    "teacher_subject_groups",
)
ACADEMIC_SCHEMA_EXCEPTION = "org.hibernate.tool.schema.spi.SchemaManagementException"
ACADEMIC_SCHEMA_DIAGNOSTIC_CHARS = 65536
ACADEMIC_SCHEMA_IDENTIFIER = r'(?:[A-Za-z_][A-Za-z0-9_]{0,127}|"[A-Za-z_][A-Za-z0-9_]{0,127}")'
ACADEMIC_SCHEMA_MISSING = (
    r"Schema-validation: missing table \[("
    + ACADEMIC_SCHEMA_IDENTIFIER
    + r"(?:\."
    + ACADEMIC_SCHEMA_IDENTIFIER
    + r")?)\]"
)
ACADEMIC_SCHEMA_MESSAGE = re.compile(ACADEMIC_SCHEMA_MISSING)
ACADEMIC_SCHEMA_HEADER = re.compile(
    r"(?m)^[ \t]{0,32}(?:Caused by:[ \t]{1,8})?"
    + re.escape(ACADEMIC_SCHEMA_EXCEPTION)
    + r": "
    + ACADEMIC_SCHEMA_MISSING
    + r"\r?$"
)
# First source-backed failure only in these observed JDBC/Flyway-blocked classes.
ACADEMIC_JDBC_SOURCES = {
    "ru.rutcampustrack.academic."
    + name: "services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/" + path
    for name, path in (
        ("integration.UserOwnershipLifecycleIT", "integration/UserOwnershipLifecycleIT.java"),
        ("integration.UserArchiveLifecycleIT", "integration/UserArchiveLifecycleIT.java"),
        ("map.CampusMapReadRepositoryIT", "map/CampusMapReadRepositoryIT.java"),
        ("notification.NotificationAudienceIT", "notification/NotificationAudienceIT.java"),
    )
}
ACADEMIC_JDBC_DIAGNOSTIC_CHARS = 65536
ACADEMIC_JDBC_EXCEPTION = "org.postgresql.util.PSQLException"
ACADEMIC_JDBC_HEADER = re.compile(
    r"(?m)^[ \t]{0,32}(?:Caused by:[ \t]{1,8})?"
    + re.escape(ACADEMIC_JDBC_EXCEPTION)
    + r"(?::[ \t]{0,8}([^\r\n]{0,2048}))?\r?$"
)
ACADEMIC_JDBC_STATE = re.compile(
    r"(?m)^[ \t]{0,32}(?:SQLSTATE|SQLState|SQL State)[ \t]{0,8}:[ \t]{0,8}([A-Z0-9]{5})[ \t]{0,8}\r?$"
)
ACADEMIC_JDBC_SQL_STATES = {
    "53300": "TOO_MANY_CONNECTIONS",
    "28P01": "AUTHENTICATION_FAILED",
    "28000": "AUTHORIZATION_FAILED",
    "3D000": "MISSING_DATABASE",
    "42P01": "MISSING_RELATION",
    "42703": "MISSING_COLUMN",
    "08001": "CONNECTION_FAILURE",
    "08006": "CONNECTION_FAILURE",
    "57P01": "SERVER_UNAVAILABLE",
    "57P02": "SERVER_UNAVAILABLE",
    "57P03": "SERVER_UNAVAILABLE",
}
# Match whole native message lines; variable slots stay private and are never returned.
ACADEMIC_JDBC_MESSAGES = (
    ("TOO_MANY_CONNECTIONS", r"FATAL: sorry, too many clients already"),
    (
        "TOO_MANY_CONNECTIONS",
        r"FATAL: remaining connection slots are reserved for (?:non-replication superuser connections|roles with the SUPERUSER attribute)",
    ),
    ("AUTHENTICATION_FAILED", r'FATAL: password authentication failed for user "[^"\r\n]{1,256}"'),
    ("MISSING_DATABASE", r'FATAL: database "[^"\r\n]{1,256}" does not exist'),
    ("MISSING_RELATION", r'ERROR: relation "[^"\r\n]{1,256}" does not exist'),
    ("MISSING_COLUMN", r'ERROR: column "[^"\r\n]{1,256}" does not exist'),
    (
        "CONNECTION_REFUSED",
        r"Connection to [^\r\n]{1,512} refused\. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections\.",
    ),
    (
        "SERVER_UNAVAILABLE",
        r"FATAL: (?:the database system is (?:shutting down|starting up|in recovery mode)|terminating connection due to administrator command)",
    ),
)
HEADMAN_RACE_CLASS = "ru.rutcampustrack.academic.group.GroupHeadmanAssignmentIT"
HEADMAN_RACE_METHOD = "concurrentCandidatesHaveOneWinnerAndOneCasConflict"
HEADMAN_RACE_SOURCE = "services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupHeadmanAssignmentIT.java"
HEADMAN_RACE_HTTP_CATEGORIES = ("HTTP200", "HTTP202", "HTTP401", "HTTP403", "HTTP409", "HTTP429", "HTTP503", "OTHER")
HEADMAN_RACE_MARKER = re.compile(
    r"(?:(?:java\.lang\.AssertionError|org\.opentest4j\.AssertionFailedError):\s*)?"
    r"HEADMAN_RACE_HTTP_DIAGNOSTIC_V1 category=(" + "|".join(HEADMAN_RACE_HTTP_CATEGORIES) + r")"
)
BOT_BRIDGE_CLASS = "ru.rutcampustrack.academic.botcredentials.BotCredentialPythonBridgeIT"
BOT_BRIDGE_METHOD = "actualAcademicTlsOwnCardsReachPythonDispatcherAndExactProviderCleanup"
BOT_BRIDGE_SOURCE = "services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/botcredentials/BotCredentialPythonBridgeIT.java"
BOT_BRIDGE_PHASES = (
    "SETUP",
    "REAL_TLS_DIRECTED_AUTH_BOUNDARIES",
    "OWN_ROLES_NATIVE_DISPATCHER",
    "PRIVACY_BEFORE_ANY_LOOKUP",
    "FOREIGN_UID_DENIAL",
    "ORIGINAL_UID_CLEANUP_AFTER_REBIND",
    "EVENT_BEFORE_REGISTER",
    "LATE_EDIT_AFTER_EXACT_DELETE",
    "UNCERTAIN_OUTCOME_PRESERVES_DURABLE_RETRY",
    "SETTLED_SANITIZE_FALLBACK",
    "OLD_EVENT_CANNOT_REMOVE_LATER_CARD",
    "GRANT_REVOCATION_NO_NEW_DELIVERY",
    "ARCHIVE_PRESERVES_EXACT_OLD_CARD_CLEANUP",
    "OWN_HOMEWORK_PROFILE",
    "OWN_HOMEWORK_HANDLERS",
    "OWN_HOMEWORK_TRANSPORT_DENIALS",
    "OWN_HOMEWORK_REVOKED_GRANT",
    "OWN_HOMEWORK_REBOUND_TELEGRAM",
    "COMPLETE",
    "OTHER",
)
BOT_BRIDGE_MARKER = re.compile(
    r"(?:(?:java\.lang\.AssertionError|org\.opentest4j\.AssertionFailedError):[ \t]{0,8})?"
    r"\[NP01_BRIDGE branch=(OWN_HOMEWORK|CREDENTIALS) phase=(" + "|".join(BOT_BRIDGE_PHASES) + r")\] ?"
)
BOOTSTRAP_CLI_CLASS = "ru.rutcampustrack.academic.bootstrap.InitialAdministratorBootstrapIT"
BOOTSTRAP_CLI_METHOD = "operatorCliLoadsPrivateConfigAndCommitsFirstAdministrator"
BOOTSTRAP_CLI_SOURCE = "services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/bootstrap/InitialAdministratorBootstrapIT.java"
BOOTSTRAP_CLI_DIAGNOSTIC_CHARS = 65536
BOOTSTRAP_CLI_RESULT_MARKER = re.compile(
    r"(?:(?:java\.lang\.AssertionError|org\.opentest4j\.AssertionFailedError):\s*)?"
    r"BOOTSTRAP_CLI_RESULT_V1 invocation=(MISSING_CONFIG|FRESH|REPEAT) "
    r"guard=(UNEXPECTED_EXIT|OVERSIZED_STDOUT|NONEMPTY_STDERR|SECRET_MARKER|INVALID_DIAGNOSTIC|UNEXPECTED_REFUSAL_PHASE) "
    r"expectedExit=(0|2) actualExit=(0|[1-9][0-9]?|1[0-9]{2}|2[0-4][0-9]|25[0-5]|OTHER)"
    r"(?: phase=(PARSE|READ_REQUEST|VALIDATE_REQUEST|AUTH_PREPARE|ACADEMIC_APPLY|ACADEMIC_VERIFY|AUTH_ACTIVATE|ACADEMIC_CURSOR|AUTH_CATALOG|ACADEMIC_FINALIZE|RESULT|OTHER)"
    r" reason=(INPUT|IO|CHILD_START|CHILD_TIMEOUT|CHILD_EXIT|OUTPUT_LIMIT|REPLY_JSON|REPLY_GUARD|PROTOCOL|OTHER)"
    r" ownerPhase=(NONE|INPUT|CONFIG|EXECUTE|OUTPUT|NO_MARKER|OTHER)"
    r" category=(NONE|CONFIG|INPUT|DB_CONNECT|DB_PERMISSION|DB_SCHEMA|DB_CONSTRAINT|GUARD|OTHER))?"
)
BOOTSTRAP_CLI_EXCEPTIONS = {
    "java.lang.AssertionError": "ASSERTION",
    "org.opentest4j.AssertionFailedError": "ASSERTION",
    "java.lang.IllegalStateException": "ILLEGAL_STATE",
    "java.lang.IllegalArgumentException": "ILLEGAL_ARGUMENT",
    "java.lang.NullPointerException": "NULL_POINTER",
    "java.io.IOException": "IO",
    "java.nio.file.FileSystemException": "IO",
    "java.nio.file.AccessDeniedException": "IO",
    "java.nio.file.NoSuchFileException": "IO",
    "java.util.concurrent.TimeoutException": "TIMEOUT",
    "org.testcontainers.containers.ContainerLaunchException": "CONTAINER",
    "org.testcontainers.containers.ContainerFetchException": "CONTAINER",
    "com.github.dockerjava.api.exception.DockerException": "DOCKER",
    "com.github.dockerjava.api.exception.NotFoundException": "DOCKER",
    "com.github.dockerjava.api.exception.InternalServerErrorException": "DOCKER",
    "org.springframework.jdbc.CannotGetJdbcConnectionException": "JDBC",
    "org.springframework.dao.DataIntegrityViolationException": "JDBC",
    "org.springframework.jdbc.BadSqlGrammarException": "JDBC",
    "org.postgresql.util.PSQLException": "JDBC",
    "org.springframework.util.PlaceholderResolutionException": "CONFIG",
    "com.fasterxml.jackson.core.JsonParseException": "JSON",
    "com.fasterxml.jackson.databind.JsonMappingException": "JSON",
}
BOOTSTRAP_CLI_SITES = {
    BOOTSTRAP_CLI_METHOD: "TEST",
    "freshOwnDatabases": "SETUP",
    "configure": "SETUP",
    "migrate": "SETUP",
    "data": "SETUP",
    "loopback": "SETUP",
    "runOperatorCli": "CLI_RESULT",
    "localCliImage": "IMAGE_ADMISSION",
    "cliProject": "PROJECT_ADMISSION",
    "cliService": "PRIVATE_CONFIG",
    "releaseOnlyFreshOwnedContainer": "CLEANUP",
}
BOOTSTRAP_CLI_FRAME = re.compile(
    r"(?m)^[ \t]{0,32}at (?:app//)?"
    + re.escape(BOOTSTRAP_CLI_CLASS)
    + r"\.([A-Za-z_$][A-Za-z0-9_$]{0,127})"
    + r"\(InitialAdministratorBootstrapIT\.java:([1-9][0-9]{0,4})\)\r?$"
)
BOOTSTRAP_CLI_DECLARATION = re.compile(
    r"^    (?:private (?:static )?)?"
    r"(?:void|String|ObjectNode|Map<String, Object>|DriverManagerDataSource) "
    r"([A-Za-z_$][A-Za-z0-9_$]*)\("
)
METADATA_ENV = (
    "RCT_CI_PR_HEAD_SHA",
    "RCT_CI_RUN_ID",
    "RCT_CI_RUN_ATTEMPT",
    "RCT_CI_EVENT_NAME",
    "RCT_CI_TEST_OUTCOME",
    "RCT_CI_JOB_ID",
    "RCT_BOOTSTRAP_CLI_ACCEPTANCE",
)


def git(root: Path, *arguments: str) -> str:
    result = subprocess.run(["git", *arguments], cwd=root, capture_output=True, text=True, check=False)
    if result.returncode:
        raise ValueError("Checkout metadata unavailable")
    return result.stdout.strip()


def java_test_declarations(source: str) -> dict[str, set[str]]:
    # Mask comments, strings, chars and text blocks before reading declaration tokens.
    masked = re.sub(
        r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/',
        " ",
        source,
    )
    tokens = re.findall(r"[A-Za-z_$][A-Za-z0-9_$]*|[{}()@.;]", masked)
    package = re.search(r"\bpackage\s+([A-Za-z_$][A-Za-z0-9_$.]*)\s*;", masked)
    prefix = package.group(1) + "." if package else ""
    classes: list[str] = []
    braces: list[str | None] = []
    result: dict[str, set[str]] = {}
    pending_class = None
    pending_test = False
    index = 0
    while index < len(tokens):
        token = tokens[index]
        if token == "@":
            index += 1
            annotation = []
            if index < len(tokens) and IDENTIFIER.fullmatch(tokens[index]):
                annotation.append(tokens[index])
                index += 1
            while index + 1 < len(tokens) and tokens[index] == "." and IDENTIFIER.fullmatch(tokens[index + 1]):
                annotation.extend(tokens[index : index + 2])
                index += 2
            pending_test = pending_test or bool(annotation and annotation[-1] in TEST_ANNOTATIONS)
            if index < len(tokens) and tokens[index] == "(":
                depth = 1
                index += 1
                while index < len(tokens) and depth:
                    depth += (tokens[index] == "(") - (tokens[index] == ")")
                    index += 1
            continue
        if (
            token in {"class", "interface", "record", "enum"}
            and index + 1 < len(tokens)
            and IDENTIFIER.fullmatch(tokens[index + 1])
            and (index == 0 or tokens[index - 1] != ".")
        ):
            pending_class = tokens[index + 1]
            pending_test = False
        elif token == "{":
            braces.append(pending_class)
            if pending_class:
                classes.append(pending_class)
                pending_class = None
        elif token == "}":
            if braces and braces.pop():
                classes.pop()
            pending_test = False
        elif token == "(" and pending_test and classes and index:
            method = tokens[index - 1]
            if IDENTIFIER.fullmatch(method):
                result.setdefault(prefix + "$".join(classes), set()).add(method)
            pending_test = False
        index += 1
    return result


def unlinked_scoped_path(root: Path, path: Path, scope: Path) -> bool:
    # Reject links before globbing, reading or hashing; resolved-in-repo alone
    # would still permit a link into another task or source directory.
    try:
        relative = path.relative_to(root)
        path.relative_to(scope)
        if any(part in {".", ".."} for part in relative.parts):
            return False
        current = root
        for part in relative.parts:
            current = current / part
            if current.is_symlink():
                return False
        return path.resolve() == root.resolve() / relative
    except (OSError, ValueError):
        return False


def source_registry(root: Path, module: str) -> dict[str, dict]:
    registry = {}
    paths = git(root, "ls-files", "-z", "--", f"{module}/src/test/java").split("\0")
    for relative in paths:
        if not relative.endswith(".java"):
            continue
        path = root / relative
        if not unlinked_scoped_path(root, path, root / module / "src/test/java"):
            continue
        data = path.read_bytes()
        for classname, methods in java_test_declarations(data.decode("utf8")).items():
            registry[classname] = {"methods": methods, "source": relative, "sha256": hashlib.sha256(data).hexdigest()}
    return registry


def metadata(root: Path, service: str, task: str, environment: dict[str, str]) -> dict:
    checkout = git(root, "rev-parse", "HEAD")
    head = environment.get("RCT_CI_PR_HEAD_SHA", checkout)
    if not all(re.fullmatch(r"[0-9a-f]{40}", value) for value in (checkout, head)):
        raise ValueError("Invalid revision metadata")
    expected_job = "java-unit-test" if task == "test" else "java-integration-test"
    job = environment.get("RCT_CI_JOB_ID", expected_job)
    bootstrap_cli = (
        job == "e2e-auth"
        and service == "academic-service"
        and task == "integrationTest"
        and environment.get("RCT_BOOTSTRAP_CLI_ACCEPTANCE") == "true"
    )
    if job not in {expected_job, "java-coverage"} and not bootstrap_cli:
        raise ValueError("Invalid job scope")
    result = {
        "checkoutSha": checkout,
        "prHeadSha": head,
        "service": service,
        "task": task,
        "job": job,
    }
    for variable, field in (("RCT_CI_RUN_ID", "runId"), ("RCT_CI_RUN_ATTEMPT", "runAttempt")):
        value = environment.get(variable)
        if value is not None and not re.fullmatch(r"[0-9]{1,20}", value):
            raise ValueError("Invalid run metadata")
        result[field] = value
    event = environment.get("RCT_CI_EVENT_NAME", "LOCAL_CONTROL")
    outcome = environment.get("RCT_CI_TEST_OUTCOME", "UNKNOWN")
    if event not in {"push", "pull_request", "LOCAL_CONTROL"} or outcome not in {
        "success",
        "failure",
        "cancelled",
        "skipped",
        "UNKNOWN",
    }:
        raise ValueError("Invalid execution metadata")
    if job == "java-coverage":
        result["testStepScope"] = "unit+integration+report"
    return {**result, "event": event, "testStepOutcome": outcome}


def number(value: str | None) -> int | None:
    return int(value) if value is not None and re.fullmatch(r"[0-9]{1,5}", value) else None


def cap_failure_diagnostic(case: ElementTree.Element) -> dict:
    failures = case.findall("failure")
    result = {"state": "ABSENT_OR_UNRECOGNIZED", "stage": "UNKNOWN"}
    if len(failures) != 1:
        return result
    failure = failures[0]
    message = failure.get("message", "")
    candidates = [message] if len(message) <= 2048 else []
    text = failure.text or ""
    start = 0
    for _ in range(3):
        end = text.find("\n", start, 2049)
        if end >= 0:
            candidates.append(text[start:end])
            start = end + 1
        elif len(text) <= 2048:
            candidates.append(text[start:])
            break
        else:
            # A bounded prefix is not a whole original line and cannot attest a marker.
            break
    matches = {match.groups() for value in candidates if (match := CAP_MARKER.fullmatch(value.strip()))}
    if len(matches) != 1:
        return result
    failure_class, sql_state, barrier = matches.pop()
    return {
        "state": "CLOSED_MARKER",
        "capturedClass": failure_class,
        "sqlState": sql_state,
        "barrier": barrier,
        "stage": "AWAIT_BARRIER" if barrier != "NOT_OBSERVED" else "UNKNOWN",
    }


def notification_failure_diagnostic(case: ElementTree.Element) -> dict:
    failures = case.findall("failure")
    if len(failures) != 1:
        return {"state": "NO_SINGLE_FAILURE", "codes": [], "overflow": False}
    failure = failures[0]
    codes = [NOTIFICATION_FAILURE_CODES.get(failure.get("type"), "OTHER")]
    text = failure.text or ""
    overflow = len(text) > NOTIFICATION_DIAGNOSTIC_CHARS
    prefix = text[:NOTIFICATION_DIAGNOSTIC_CHARS]
    if overflow:
        # Never classify a header cut from a longer original line.
        prefix = prefix.rsplit("\n", 1)[0] if "\n" in prefix else ""
    for match in NOTIFICATION_CAUSE_HEADER.finditer(prefix):
        code = NOTIFICATION_FAILURE_CODES.get(match.group(1), "OTHER")
        if code not in codes:
            if len(codes) < 8:
                codes.append(code)
            else:
                overflow = True
    return {"state": "CLOSED_CLASSES", "codes": codes, "overflow": overflow}


def attendance_failure_diagnostic(case: ElementTree.Element) -> dict:
    result = {
        "kind": "ATTENDANCE_FAILURE_V1",
        "state": "NO_SINGLE_FAILURE",
        "codes": [],
        "overflow": False,
        "processExit": {"state": "ABSENT_OR_UNRECOGNIZED", "value": None},
    }
    failures = case.findall("failure") + case.findall("error")
    if len(failures) != 1:
        return result
    failure = failures[0]
    failure_type = failure.get("type", "")
    if len(failure_type) > 256:
        failure_type = ""
    codes = [ATTENDANCE_FAILURE_CODES.get(failure_type, "OTHER")]
    text = failure.text or ""
    overflow = len(text) > ATTENDANCE_DIAGNOSTIC_CHARS
    prefix = text[:ATTENDANCE_DIAGNOSTIC_CHARS]
    if overflow:
        prefix = prefix.rsplit("\n", 1)[0] if "\n" in prefix else ""
    for match in NOTIFICATION_CAUSE_HEADER.finditer(prefix):
        code = ATTENDANCE_FAILURE_CODES.get(match.group(1), "OTHER")
        if code not in codes:
            # The exact allowlist plus OTHER bounds this array to fifteen codes.
            codes.append(code)
    exits = {int(match.group(1)) for match in ATTENDANCE_EXEC_HEADER.finditer(prefix) if int(match.group(1)) <= 255}
    message = failure.get("message", "")
    if failure_type == ATTENDANCE_EXEC_TYPE and len(message) <= 2048:
        match = ATTENDANCE_EXEC_MESSAGE.fullmatch(message)
        if match and int(match.group(1)) <= 255:
            exits.add(int(match.group(1)))
    if len(exits) == 1:
        result["processExit"] = {"state": "CLOSED_VALUE", "value": exits.pop()}
    elif exits:
        result["processExit"]["state"] = "AMBIGUOUS"
    result.update(state="CLOSED_CLASSES", codes=codes, overflow=overflow)
    return result


def academic_schema_failure_diagnostic(case: ElementTree.Element) -> dict:
    result = {"state": "ABSENT_OR_UNRECOGNIZED", "table": "OTHER", "qualifier": "OTHER"}
    failures = case.findall("failure")
    if len(failures) != 1:
        return result
    failure = failures[0]
    matches = set()
    message = failure.get("message", "")
    if (
        failure.get("type") == ACADEMIC_SCHEMA_EXCEPTION
        and len(message) <= 512
        and (match := ACADEMIC_SCHEMA_MESSAGE.fullmatch(message))
    ):
        matches.add(match.group(1))
    text = failure.text or ""
    prefix = text[:ACADEMIC_SCHEMA_DIAGNOSTIC_CHARS]
    if len(text) > ACADEMIC_SCHEMA_DIAGNOSTIC_CHARS:
        # A truncated original line cannot attest a native exception header.
        prefix = prefix.rsplit("\n", 1)[0] if "\n" in prefix else ""
    matches.update(match.group(1) for match in ACADEMIC_SCHEMA_HEADER.finditer(prefix))
    if len(matches) != 1:
        return result
    parts = matches.pop().split(".")
    table = parts[-1].strip('"')
    qualifier = "UNQUALIFIED"
    if len(parts) == 2:
        qualifier = {"public": "PUBLIC", "pg_catalog": "PG_CATALOG"}.get(parts[0].strip('"'), "OTHER")
    return {
        "state": "NATIVE_MISSING_TABLE",
        "table": table if table in ACADEMIC_SCHEMA_TABLES else "OTHER",
        "qualifier": qualifier,
    }


def academic_jdbc_failure_diagnostic(case: ElementTree.Element) -> dict:
    result = {
        "state": "ABSENT_OR_UNRECOGNIZED",
        "category": "UNKNOWN",
        "sqlState": "NOT_OBSERVED",
        "nativeCausePresent": False,
        "overflow": False,
    }
    failures = case.findall("failure")
    if len(failures) != 1:
        return result
    failure = failures[0]
    text = failure.text or ""
    prefix = text[:ACADEMIC_JDBC_DIAGNOSTIC_CHARS]
    result["overflow"] = len(text) > ACADEMIC_JDBC_DIAGNOSTIC_CHARS
    if result["overflow"]:
        prefix = prefix.rsplit("\n", 1)[0] if "\n" in prefix else ""
    headers = list(ACADEMIC_JDBC_HEADER.finditer(prefix))
    native = failure.get("type") == ACADEMIC_JDBC_EXCEPTION
    result["nativeCausePresent"] = native or bool(headers)
    if not result["nativeCausePresent"]:
        return result
    messages = [match.group(1) or "" for match in headers]
    message = failure.get("message", "")
    if native and len(message) <= 2048:
        messages.append(message)
    categories = {
        category for value in messages for category, pattern in ACADEMIC_JDBC_MESSAGES if re.fullmatch(pattern, value)
    }
    states = {match.group(1) for match in ACADEMIC_JDBC_STATE.finditer(prefix)}
    if states:
        result["sqlState"] = (
            next(iter(states)) if len(states) == 1 and states <= ACADEMIC_JDBC_SQL_STATES.keys() else "OTHER"
        )
        if result["sqlState"] == "OTHER":
            return result
        categories.add(ACADEMIC_JDBC_SQL_STATES[result["sqlState"]])
    if categories == {"CONNECTION_FAILURE", "CONNECTION_REFUSED"}:
        categories = {"CONNECTION_REFUSED"}
    if len(categories) == 1:
        result.update(state="CLOSED_NATIVE_CAUSE", category=categories.pop())
    return result


def headman_race_failure_diagnostic(case: ElementTree.Element) -> dict:
    result = {"state": "ABSENT_OR_UNRECOGNIZED"}
    failures = case.findall("failure")
    if len(failures) != 1 or failures[0].get("type") not in {
        "java.lang.AssertionError", "org.opentest4j.AssertionFailedError"
    }:
        return result
    failure = failures[0]
    message = failure.get("message", "")
    candidates = [message] if len(message) <= 256 else []
    text = failure.text or ""
    start = 0
    for _ in range(2):
        end = text.find("\n", start, start + 257)
        if end >= 0:
            candidates.append(text[start:end])
            start = end + 1
        elif len(text) - start <= 256:
            candidates.append(text[start:])
            break
        else:
            # A bounded prefix of a longer original line cannot attest the marker.
            break
    categories = {
        match.group(1) for value in candidates if (match := HEADMAN_RACE_MARKER.fullmatch(value.strip()))
    }
    if len(categories) == 1:
        return {"state": "CLOSED_MARKER", "category": categories.pop()}
    return result


def bot_bridge_failure_diagnostic(case: ElementTree.Element) -> dict:
    result = {"state": "ABSENT_OR_UNRECOGNIZED"}
    failures = case.findall("failure")
    if len(failures) != 1 or failures[0].get("type") not in {
        "java.lang.AssertionError", "org.opentest4j.AssertionFailedError"
    }:
        return result
    failure = failures[0]
    markers = set()
    for carrier in (failure.get("message", ""), failure.text or ""):
        # Inspect only complete first lines, never a truncated marker or arbitrary suffix.
        start = 0
        observed = []
        for _ in range(2):
            end = carrier.find("\n", start, start + 257)
            if end < 0:
                if len(carrier) - start > 256:
                    return result
                line = carrier[start:]
            else:
                line = carrier[start:end]
            line = line.removesuffix("\r")
            if "NP01_BRIDGE" in line:
                match = BOT_BRIDGE_MARKER.fullmatch(line)
                if match is None:
                    return result
                observed.append(match.groups())
            if end < 0:
                break
            start = end + 1
        if len(observed) > 1:
            return result
        markers.update(observed)
    # JUnit normally mirrors the same assertion in message and the first stack line.
    if len(markers) == 1:
        branch, phase = markers.pop()
        return {"state": "CLOSED_MARKER", "branch": branch, "phase": phase}
    return result


def bootstrap_cli_failure_diagnostic(case: ElementTree.Element, source: str) -> dict:
    result = {
        "state": "ABSENT_OR_UNRECOGNIZED",
        "exception": "UNKNOWN",
        "site": "UNKNOWN",
        "sourceLine": None,
    }
    failures = case.findall("failure")
    if len(failures) != 1:
        return result
    failure = failures[0]
    text = failure.text or ""
    if len(text) > BOOTSTRAP_CLI_DIAGNOSTIC_CHARS:
        return {**result, "state": "OVERFLOW"}
    result["exception"] = BOOTSTRAP_CLI_EXCEPTIONS.get(failure.get("type"), "UNKNOWN")
    frame = BOOTSTRAP_CLI_FRAME.search(text)
    if frame and frame[1] in BOOTSTRAP_CLI_SITES:
        # This exact owned file uses four-space method declarations/closing braces.
        # A changed shape fails closed; no foreign frame or source text is projected.
        lines = source.splitlines()
        line = int(frame[2])
        for start, content in enumerate(lines, 1):
            declaration = BOOTSTRAP_CLI_DECLARATION.match(content)
            if declaration and declaration[1] == frame[1]:
                end = next(
                    (offset for offset in range(start + 1, len(lines) + 1) if lines[offset - 1] == "    }"),
                    start,
                )
                if start <= line <= end:
                    result.update(site=BOOTSTRAP_CLI_SITES[frame[1]], sourceLine=line)
                    break
    if result["exception"] != "UNKNOWN" or result["site"] != "UNKNOWN":
        result["state"] = "PROJECTED"
    if result["exception"] == "ASSERTION" and result["site"] == "CLI_RESULT":
        # Only a whole bounded message/first stack line can carry the closed marker.
        message = failure.get("message", "")
        first_line = text.split("\n", 1)[0]
        candidates = [value for value in (message, first_line) if value]
        if any(len(value) > 384 or not BOOTSTRAP_CLI_RESULT_MARKER.fullmatch(value)
               for value in candidates):
            return result
        markers = {
            match.groups()
            for value in (message, first_line)
            if len(value) <= 384 and (match := BOOTSTRAP_CLI_RESULT_MARKER.fullmatch(value))
        }
        if len(markers) == 1:
            invocation, guard, expected, actual, phase, reason, owner_phase, category = markers.pop()
            expected_for_invocation = "2" if invocation == "MISSING_CONFIG" else "0"
            unexpected_exit = actual != expected
            if expected == expected_for_invocation and (guard == "UNEXPECTED_EXIT") == unexpected_exit:
                result.update(
                    state="CLOSED_MARKER", invocation=invocation, guard=guard,
                    expectedExit=int(expected), actualExit=int(actual) if actual != "OTHER" else "OTHER",
                )
                if phase is not None:
                    result.update(phase=phase, reason=reason, ownerPhase=owner_phase, category=category)
    return result


def collect(root: Path, service: str, task: str, environment: dict[str, str]) -> dict:
    if service not in MODULES or task not in {"test", "integrationTest"}:
        raise ValueError("Invalid outcome scope")
    module = MODULES[service]
    registry = source_registry(root, module)
    report = {
        "schemaVersion": 1,
        "metadata": metadata(root, service, task, environment),
        "collection": "NO_RESULTS",
        "acceptance": "NOT_ASSESSED",
        "xml": [],
        "cases": [],
    }
    directory = root / module / "build/test-results" / task
    directory_safe = unlinked_scoped_path(root, directory, directory)
    incomplete = not directory_safe
    jdbc_diagnosed = set()
    paths = sorted(directory.glob("TEST-*.xml")) if directory_safe else []
    for xml_ordinal, path in enumerate(paths, 1):
        if not unlinked_scoped_path(root, path, directory):
            report["xml"].append({"ordinal": xml_ordinal, "sha256": None, "suites": [], "state": "UNINSPECTABLE_XML"})
            incomplete = True
            continue
        with path.open("rb") as stream:
            data = stream.read(MAX_XML_BYTES + 1)
        entry = {
            "ordinal": xml_ordinal,
            "sha256": hashlib.sha256(data).hexdigest() if len(data) <= MAX_XML_BYTES else None,
            "suites": [],
        }
        report["xml"].append(entry)
        if len(data) > MAX_XML_BYTES or b"<!DOCTYPE" in data or b"<!ENTITY" in data:
            entry["state"] = "UNINSPECTABLE_XML"
            incomplete = True
            continue
        try:
            document = ElementTree.fromstring(data)
        except ElementTree.ParseError:
            entry["state"] = "MALFORMED_XML"
            incomplete = True
            continue
        suites = [document] if document.tag == "testsuite" else list(document.iter("testsuite"))
        entry["state"] = "PARSED"
        for suite_ordinal, suite in enumerate(suites, 1):
            declared = {key: number(suite.get(key)) for key in ("tests", "failures", "errors", "skipped")}
            nodes = list(suite.findall("testcase"))
            counts = {key: 0 for key in ("PASSED", "FAILED", "ERROR", "SKIPPED", "NOT_EXECUTED")}
            for ordinal, case in enumerate(nodes, 1):
                tags = {child.tag for child in case}
                status = (
                    "ERROR"
                    if "error" in tags
                    else "FAILED"
                    if "failure" in tags
                    else "SKIPPED"
                    if "skipped" in tags
                    else "NOT_EXECUTED"
                    if case.get("status") in {"notrun", "notexecuted", "disabled"}
                    else "PASSED"
                )
                counts[status] += 1
                raw_class = case.get("classname", "")
                known = registry.get(raw_class)
                candidate = case.get("name", "").split("(", 1)[0]
                method = candidate if known and candidate in known["methods"] else None
                safe = {
                    "xmlOrdinal": xml_ordinal,
                    "suiteOrdinal": suite_ordinal,
                    "ordinal": ordinal,
                    "classname": raw_class if known else None,
                    "method": method,
                    "status": status,
                    "identity": "SOURCE_METHOD" if method else "ORDINAL_ONLY",
                }
                if known:
                    safe.update(source=known["source"], sourceSha256=known["sha256"])
                if (
                    service == "attendance-service"
                    and task == "integrationTest"
                    and environment.get("RCT_CI_JOB_ID") == "java-coverage"
                    and status in {"FAILED", "ERROR"}
                    and safe["identity"] == "ORDINAL_ONLY"
                    and safe["classname"] is None
                    and safe["method"] is None
                ):
                    safe["failureDiagnostic"] = attendance_failure_diagnostic(case)
                if (
                    service == "schedule-service"
                    and task == "integrationTest"
                    and status == "FAILED"
                    and raw_class == CAP_CLASS
                    and method == CAP_METHOD
                ):
                    safe["failureDiagnostic"] = cap_failure_diagnostic(case)
                if (
                    service == "notification-service"
                    and task == "integrationTest"
                    and status == "FAILED"
                    and known
                    and raw_class.startswith("ru.rutcampustrack.notification.")
                ):
                    safe["failureDiagnostic"] = notification_failure_diagnostic(case)
                if (
                    service == "academic-service"
                    and task == "integrationTest"
                    and status == "FAILED"
                    and safe["identity"] == "SOURCE_METHOD"
                    and raw_class == ACADEMIC_SCHEMA_CLASS
                    and method == ACADEMIC_SCHEMA_METHOD
                    and known["source"] == ACADEMIC_SCHEMA_SOURCE
                ):
                    safe["failureDiagnostic"] = academic_schema_failure_diagnostic(case)
                if (
                    service == "academic-service"
                    and task == "integrationTest"
                    and status == "FAILED"
                    and safe["identity"] == "SOURCE_METHOD"
                    and raw_class in ACADEMIC_JDBC_SOURCES
                    and known["source"] == ACADEMIC_JDBC_SOURCES[raw_class]
                    and raw_class not in jdbc_diagnosed
                ):
                    safe["failureDiagnostic"] = academic_jdbc_failure_diagnostic(case)
                    jdbc_diagnosed.add(raw_class)
                if (
                    service == "academic-service"
                    and task == "integrationTest"
                    and status == "FAILED"
                    and safe["identity"] == "SOURCE_METHOD"
                    and raw_class == HEADMAN_RACE_CLASS
                    and method == HEADMAN_RACE_METHOD
                    and known["source"] == HEADMAN_RACE_SOURCE
                ):
                    safe["failureDiagnostic"] = headman_race_failure_diagnostic(case)
                if (
                    service == "academic-service"
                    and task == "integrationTest"
                    and status == "FAILED"
                    and safe["identity"] == "SOURCE_METHOD"
                    and raw_class == BOT_BRIDGE_CLASS
                    and method == BOT_BRIDGE_METHOD
                    and known["source"] == BOT_BRIDGE_SOURCE
                ):
                    safe["failureDiagnostic"] = bot_bridge_failure_diagnostic(case)
                if (
                    service == "academic-service"
                    and task == "integrationTest"
                    and status == "FAILED"
                    and safe["identity"] == "SOURCE_METHOD"
                    and raw_class == BOOTSTRAP_CLI_CLASS
                    and method == BOOTSTRAP_CLI_METHOD
                    and known["source"] == BOOTSTRAP_CLI_SOURCE
                    and environment.get("RCT_CI_JOB_ID") == "e2e-auth"
                    and environment.get("RCT_BOOTSTRAP_CLI_ACCEPTANCE") == "true"
                ):
                    source = root / known["source"]
                    if unlinked_scoped_path(root, source, root / module / "src/test/java"):
                        source_data = source.read_bytes()
                        if hashlib.sha256(source_data).hexdigest() == known["sha256"]:
                            safe["failureDiagnostic"] = bootstrap_cli_failure_diagnostic(
                                case, source_data.decode("utf8")
                            )
                report["cases"].append(safe)
            missing = max(0, (declared["tests"] or 0) - len(nodes))
            counts["NOT_EXECUTED"] += missing
            for ordinal in range(len(nodes) + 1, len(nodes) + missing + 1):
                report["cases"].append(
                    {
                        "xmlOrdinal": xml_ordinal,
                        "suiteOrdinal": suite_ordinal,
                        "ordinal": ordinal,
                        "classname": None,
                        "method": None,
                        "status": "NOT_EXECUTED",
                        "identity": "ORDINAL_ONLY",
                    }
                )
            consistent = (
                declared["tests"] == len(nodes)
                and declared["failures"] == counts["FAILED"]
                and declared["errors"] == counts["ERROR"]
                and declared["skipped"] == counts["SKIPPED"]
            )
            incomplete = incomplete or not consistent or bool(counts["NOT_EXECUTED"])
            entry["suites"].append(
                {"ordinal": suite_ordinal, "declared": declared, "observed": counts, "countsConsistent": consistent}
            )
    report["summary"] = {
        status: sum(case["status"] == status for case in report["cases"])
        for status in ("PASSED", "FAILED", "ERROR", "SKIPPED", "NOT_EXECUTED")
    }
    if report["cases"]:
        report["collection"] = "INCOMPLETE" if incomplete else "RESULTS"
    elif incomplete:
        report["collection"] = "UNINSPECTABLE_RESULTS"
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--service", required=True, choices=MODULES)
    parser.add_argument("--task", required=True, choices=("test", "integrationTest"))
    arguments = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    output = root / "build/ci-test-outcomes" / arguments.service / arguments.task / "safe-outcomes.json"
    try:
        environment = {name: os.environ[name] for name in METADATA_ENV if name in os.environ}
        report = collect(root, arguments.service, arguments.task, environment)
    except Exception:
        # Unknown collector/metadata failure must not echo raw XML, paths, values or environment.
        report = {"schemaVersion": 1, "collection": "COLLECTION_ERROR", "acceptance": "NOT_ASSESSED"}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"collection": report["collection"], "acceptance": "NOT_ASSESSED"}))


if __name__ == "__main__":
    main()
