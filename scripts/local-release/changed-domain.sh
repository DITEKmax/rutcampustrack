set -euo pipefail
RCT_PRODUCER_HEAD_SHA="$(python3 scripts/local_release_context.py revision)"
export RCT_PRODUCER_HEAD_SHA
export RCT_PROBE_SERVICE="${RCT_CI_SERVICE:-${RCT_LOCAL_RELEASE_SERVICE:-}}"
umask 077
python3 - <<'PY_DOMAIN_RED'
import hashlib, json, os, pathlib, re, shutil, subprocess, tempfile
import xml.etree.ElementTree as ET

service = os.environ['RCT_PROBE_SERVICE']
assert service in {'schedule-service', 'attendance-service', 'academic-service', 'notification-service', 'auth-service'}
app = pathlib.Path('services') / service / (service.removesuffix('-service') + '-app')
private = pathlib.Path(tempfile.mkdtemp(prefix='changed-domain-red-', dir=os.environ['RUNNER_TEMP']))
results = app / 'build/test-results/integrationTest'
reports = app / 'build/reports/integrationTest'
current = {}
additional = None
audit = None
admin_rights = None
scoped_read = None
progress = None
subject_digest = None
accepted_boundary = None
initial_membership = None
spring_completion = None
proof = {'schemaVersion': 1, 'producerHeadSha': os.environ['RCT_PRODUCER_HEAD_SHA'],
         'checkoutSha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
         'service': service, 'state': 'UNEXPECTED_RED_RESULT', 'phase': 'SOURCE_ADMISSION'}
source_snapshots = {}


def snapshot_source(path):
    data = path.read_bytes()
    assert not path.is_symlink() and 0 < len(data) <= 1048576
    assert data == subprocess.check_output(['git', 'show', 'HEAD:' + path.as_posix()])
    assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(path)]) == b''
    if path in source_snapshots:
        assert source_snapshots[path] == data
    source_snapshots[path] = data
    return data


def method_oracle_line(source, method, oracle, *, last_line=True):
    text = source.decode()
    # Preserve offsets while masking Java literals/comments for balanced method braces.
    masked = re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/',
                    lambda match: ' ' * len(match[0]), text)
    declarations = list(re.finditer(r'\bvoid\s+' + re.escape(method) + r'\s*\(', masked))
    assert len(declarations) == 1
    start = masked.index('{', declarations[0].end())
    depth, end = 1, start + 1
    while depth:
        assert end < len(masked)
        depth += (masked[end] == '{') - (masked[end] == '}')
        end += 1
    region = text[start:end]
    assert region.count(oracle) == 1
    oracle = oracle.rstrip('\n')
    offset = len(oracle) if last_line else 0
    return text[:start + region.index(oracle) + offset].count('\n') + 1


try:
    if service == 'schedule-service':
        package = 'ru.rutcampustrack.schedule.lesson.LessonRoomChangeIT'
        method = 'committedRoomProvenanceReachesSingleAndBatchGrpcWithoutInventingHistory'
        test_source = app / 'src/test/java/ru/rutcampustrack/schedule/lesson/LessonRoomChangeIT.java'
        previous = '4065cda44239af33cd9716273dc8e61d569b5261'
        paths = {
            app / 'src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java':
                '8150bf229736d323aac5e1a33c76b4727400996a05e6900416497436497920b6',
            app / 'src/main/java/ru/rutcampustrack/schedule/lesson/LessonRoomWriter.java':
                'cdca65ab8862a74efc1b68ef677532cf99963257826f4690e965fd06fb16e06e',
        }
        if os.environ.get('RCT_LOCAL_RELEASE_REVISION'):
            subprocess.run(['git','cat-file','-e',previous+'^{commit}'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10, check=True)
        else:
            with (private / 'fetch.private.log').open('wb') as log:
                subprocess.run(['git', 'fetch', '--quiet', '--no-tags', '--depth=1', 'origin', previous],
                               stdout=log, stderr=subprocess.STDOUT, timeout=60, check=True)
        replacements = {}
        for path, expected in paths.items():
            old = subprocess.check_output(['git', 'show', previous + ':' + path.as_posix()], timeout=30)
            assert 0 < len(old) <= 1048576 and hashlib.sha256(old).hexdigest() == expected
            replacements[path] = old
        proof['counterexample'] = 'PREVIOUS_ROOM_PROJECTION'
        proof['previousRevision'] = previous
        progress_source = app / 'src/test/java/ru/rutcampustrack/schedule/integration/LessonApiIT.java'
        progress_test = snapshot_source(progress_source)
        assert progress_test == subprocess.check_output(['git', 'show', 'HEAD:' + progress_source.as_posix()])
        assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(progress_source)]) == b''
        progress_oracle = '.andExpect(jsonPath("$.semesterProgress.items[0].done").value(1))'
        progress_line = method_oracle_line(progress_test,
            'semesterProgressCountsFullPhysicalSemesterWithoutChangingDefaultLessonPage', progress_oracle)
        progress_production = app / 'src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java'
        progress_current = snapshot_source(progress_production)
        progress_anchor = b'AND l.date + l.start_time <= CAST(:moscowNow AS timestamp)'
        assert progress_current.count(progress_anchor) == 1
        progress_negative = progress_current.replace(progress_anchor, b'')
        progress_index = progress_current.index(progress_anchor)
        assert progress_negative[:progress_index] + progress_anchor + progress_negative[progress_index:] == progress_current
        progress = {'package': 'ru.rutcampustrack.schedule.integration.LessonApiIT',
                    'method': 'semesterProgressCountsFullPhysicalSemesterWithoutChangingDefaultLessonPage',
                    'source': progress_source, 'production': progress_production}
        replacements[progress_production] = progress_negative
        proof['semesterProgressRule'] = {
            'counterexample': 'OMITTED_CLOSED_LESSON_STARTED_DATE_FILTER',
            'testMethod': progress['package'] + '.' + progress['method'],
            'testSourceSha256': hashlib.sha256(progress_test).hexdigest(),
            'productionSourceSha256': hashlib.sha256(progress_current).hexdigest(),
            'counterexampleSourceSha256': hashlib.sha256(progress_negative).hexdigest(),
            'ruleFrame': progress['package'] + '.' + progress['method'] + '(LessonApiIT.java:' + str(progress_line) + ')'}
    elif service == 'academic-service':
        package = 'ru.rutcampustrack.academic.integration.UserArchiveLifecycleIT'
        method = 'protectedArchiveReceiptRollsBackAndRestoreRequiresFreshRoleAssignment'
        test_source = app / 'src/test/java/ru/rutcampustrack/academic/integration/UserArchiveLifecycleIT.java'
        source = snapshot_source(test_source)
        archive = (b'        archive(target, request);\n'
                   b'        assertThat(\n'
                   b'                        jdbc.queryForObject(\n'
                   b'                                "SELECT phase FROM academic_identity_operations WHERE"\n'
                   b'                                        + " operation_id=?",\n'
                   b'                                String.class,\n'
                   b'                                request.operationId()))\n'
                   b'                .isEqualTo("SUCCEEDED");\n')
        oracle = (b'        assertThat(\n'
                  b'                        jdbc.queryForList(retainedHomeworkSql, homework, completion, target)\n'
                  b'                                .equals(retainedHomework))\n'
                  b'                .isTrue();\n')
        anchor = archive + oracle
        assert source.count(anchor) == 1
        update = b'''        assertThat(
                        jdbc.update(
                                "UPDATE homework_completions SET completed_at=completed_at+INTERVAL '1 second' WHERE id=? AND homework_id=? AND student_id=?",
                                completion,
                                homework,
                                target))
                .isEqualTo(1);
'''
        negative = source.replace(anchor, archive + update + oracle)
        assert negative.count(archive + update + oracle) == 1
        rule_line = method_oracle_line(negative, method, (archive + update + oracle).decode())
        replacements = {test_source: negative}
        proof.update(counterexample='CHANGED_RETAINED_COMPLETION_TIMESTAMP',
                     ruleFrame=package + '.' + method + '(UserArchiveLifecycleIT.java:' + str(rule_line) + ')')
        identity_source = app / 'src/test/java/ru/rutcampustrack/academic/integration/RestApiIT.java'
        identity_current = snapshot_source(identity_source)
        identity_oracle = (b'        assertThat(\n'
                           b'                        studentIdentity.equals(\n'
                           b'                                java.util.List.of(\n'
                           b'                                        identityFixture\n'
                           b'                                                .authJdbc()\n'
                           b'                                                .queryForMap(preservedCredentials, studentId),\n'
                           b'                                        jdbcTemplate.queryForMap(preservedProfile, studentId))))\n'
                           b'                .as("Adding a role must preserve the existing account identity and credentials")\n'
                           b'                .isTrue();\n')
        identity_update = "        assertThat(\n                        jdbcTemplate.update(\n                                \"UPDATE academic_profiles SET first_name=first_name || '-rct-identity-red' WHERE id=?\",\n                                studentId))\n                .isEqualTo(1);\n".encode()
        assert identity_current.count(identity_oracle) == 1
        identity_negative = identity_current.replace(identity_oracle, identity_update + identity_oracle)
        identity_package = 'ru.rutcampustrack.academic.integration.RestApiIT'
        identity_method = 'duplicateIdentityResolvesToExistingAccountAndRoleFailuresLeaveCommittedStateUnchanged'
        identity_line = method_oracle_line(identity_negative, identity_method, identity_oracle.decode())
        additional = {'package': identity_package, 'method': identity_method, 'source': identity_source}
        replacements[identity_source] = identity_negative
        proof['additionalRule'] = {
            'counterexample': 'CHANGED_EXISTING_ACCOUNT_IDENTITY_AFTER_ROLE_ADD',
            'testMethod': identity_package + '.' + identity_method,
            'testSourceSha256': hashlib.sha256(identity_current).hexdigest(),
            'counterexampleSourceSha256': hashlib.sha256(identity_negative).hexdigest(),
            'ruleFrame': identity_package + '.' + identity_method + '(RestApiIT.java:' + str(identity_line) + ')'}

        audit_source = app / 'src/test/java/ru/rutcampustrack/academic/homework/HomeworkEditLifecycleIT.java'
        audit_test = snapshot_source(audit_source)
        audit_oracle = b'        assertThat(payload.path("actor").path("user_id").asLong()).isEqualTo(actor);\n'
        assert audit_test.count(audit_oracle) == 1
        audit_line = method_oracle_line(audit_test,
            'nonAuthorAssistantDeletePreservesCreatorHistoryAndActualActorOutboxOnReplay', audit_oracle.decode())
        audit_production = app / 'src/main/java/ru/rutcampustrack/academic/homework/HomeworkBindingArchiveCoordinator.java'
        audit_current = snapshot_source(audit_production)
        audit_anchor = (b'            HomeworkActionRecordedEvent.publish(\n'
                        b'                    events,\n'
                        b'                    "HOMEWORK_ARCHIVED",\n'
                        b'                    homework,\n'
                        b'                    actualActorId,\n'
                        b'                    actorRole,\n'
                        b'                    before,\n'
                        b'                    java.time.OffsetDateTime.now());\n')
        assert audit_current.count(audit_anchor) == 1
        audit_negative = audit_current.replace(
            audit_anchor, audit_anchor.replace(b'                    actualActorId,\n',
                                              b'                    homework.getPublishedBy(),\n'))
        audit = {'package': 'ru.rutcampustrack.academic.homework.HomeworkEditLifecycleIT',
                 'method': 'nonAuthorAssistantDeletePreservesCreatorHistoryAndActualActorOutboxOnReplay',
                 'source': audit_source, 'production': audit_production}
        replacements[audit_production] = audit_negative
        proof['auditRule'] = {
            'counterexample': 'MANUAL_ARCHIVE_ACTOR_REPLACED_WITH_CREATOR',
            'testMethod': audit['package'] + '.' + audit['method'],
            'testSourceSha256': hashlib.sha256(audit_test).hexdigest(),
            'productionSourceSha256': hashlib.sha256(audit_current).hexdigest(),
            'counterexampleSourceSha256': hashlib.sha256(audit_negative).hexdigest(),
            'ruleFrame': audit['package'] + '.' + audit['method'] + '(HomeworkEditLifecycleIT.java:' + str(audit_line) + ')'}

        admin_source = app / 'src/test/java/ru/rutcampustrack/academic/adminattendance/AdminAttendanceReportIT.java'
        admin_test = snapshot_source(admin_source)
        assert admin_test == subprocess.check_output(['git', 'show', 'HEAD:' + admin_source.as_posix()])
        admin_oracle = (b'        assertThatThrownBy(\n'
                        b'                        () -> reports.read(semesterId, "", Sort.GROUP_NAME, Direction.ASC, 0, 25))\n'
                        b'                .isInstanceOf(AdminAccessException.class);\n')
        assert admin_test.count(admin_oracle) == 1
        admin_line = method_oracle_line(admin_test,
            'otherRegistryGrantsCannotReadAndRevocationDuringRemoteReadReturnsNoRows',
            admin_oracle.decode(), last_line=False)
        admin_production = app / 'src/main/java/ru/rutcampustrack/academic/adminattendance/AdminAttendanceReportService.java'
        admin_current = snapshot_source(admin_production)
        admin_anchor = (b'            rows.sort(comparator(sort, direction));\n'
                        b'            requireCurrentAccess();\n'
                        b'            return new FullReport(')
        admin_changed = admin_anchor.replace(b'            requireCurrentAccess();\n', b'')
        assert admin_current.count(admin_anchor) == 1
        admin_negative = admin_current.replace(admin_anchor, admin_changed)
        assert admin_negative.replace(admin_changed, admin_anchor) == admin_current
        admin_rights = {'package': 'ru.rutcampustrack.academic.adminattendance.AdminAttendanceReportIT',
                        'method': 'otherRegistryGrantsCannotReadAndRevocationDuringRemoteReadReturnsNoRows',
                        'source': admin_source, 'production': admin_production}
        replacements[admin_production] = admin_negative
        proof['adminRightsRule'] = {
            'counterexample': 'OMITTED_FINAL_DASHBOARD_VIEW_REAUTHORIZATION',
            'testMethod': admin_rights['package'] + '.' + admin_rights['method'],
            'testSourceSha256': hashlib.sha256(admin_test).hexdigest(),
            'productionSourceSha256': hashlib.sha256(admin_current).hexdigest(),
            'counterexampleSourceSha256': hashlib.sha256(admin_negative).hexdigest(),
            'ruleFrame': admin_rights['package'] + '.' + admin_rights['method'] + '(AdminAttendanceReportIT.java:' + str(admin_line) + ')'}

    elif service == 'auth-service':
        package = 'ru.rutcampustrack.auth.integration.AuthOtpFlowIT'
        method = 'ordinaryPasswordResetDoesNotIssueOrCompleteForActiveAdmin'
        test_source = app / 'src/test/java/ru/rutcampustrack/auth/integration/AuthOtpFlowIT.java'
        auth_test = snapshot_source(test_source)
        auth_oracle = b'        assertThat(delivered == null).isTrue();\n'
        assert auth_test.count(auth_oracle) == 1
        rule_line = method_oracle_line(auth_test, method, auth_oracle.decode())
        auth_repository = app / 'src/main/java/ru/rutcampustrack/auth/repository/UserRepository.java'
        snapshot_source(auth_repository)
        # Preserve current Auth-owned identity/fence SQL; vary only reset admin guards.
        paths = {
            app / 'src/main/java/ru/rutcampustrack/auth/service/PasswordResetService.java':
                [['                                && !hasActiveAdminGrant(requestedUser.getId())\n', ''], ['        if (user == null || hasActiveAdminGrant(userId)) {', '        if (user == null) {']],
            app / 'src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java':
                [['                            if (hasActiveAdminGrant(userId) != administrative) {', '                            if (!hasActiveAdminGrant(userId) && administrative) {'], ['                        && !ticket.storedHash().startsWith(ADMINISTRATIVE_RESET_PREFIX))) {', '                        && !ticket.storedHash().startsWith(ADMINISTRATIVE_RESET_PREFIX) && false)) {']],
        }
        replacements = {}
        for path, edits in paths.items():
            source = snapshot_source(path)
            negative = source
            for anchor, changed in edits:
                anchor, changed = anchor.encode(), changed.encode()
                assert negative.count(anchor) == 1
                negative = negative.replace(anchor, changed, 1)
            if path.name == 'JdbcSessionAuthority.java':
                assert b'FROM auth_identities' in negative
                assert negative.count(b'!user.admitted()') == source.count(b'!user.admitted()')
                # Omit only the public ADMIN denial; preserve trusted administrative origin.
                assert negative.count(b'ADMINISTRATIVE_RESET_PREFIX') == source.count(b'ADMINISTRATIVE_RESET_PREFIX')
            replacements[path] = negative
        proof.update(counterexample='OMITTED_ACTIVE_ADMIN_PASSWORD_RESET_GUARDS',
                     ruleFrame=package + '.' + method + '(AuthOtpFlowIT.java:' + str(rule_line) + ')',
                     productionSourceSha256={str(path): hashlib.sha256(source_snapshots[path]).hexdigest() for path in paths},
                     counterexampleSourceSha256={str(path): hashlib.sha256(replacements[path]).hexdigest() for path in paths})

        digest_source = app / 'src/main/java/ru/rutcampustrack/auth/service/SubjectDeletionProofService.java'
        digest_test_source = app / 'src/test/java/ru/rutcampustrack/auth/integration/SubjectDeletionProofHttpIT.java'
        digest_current, digest_test = snapshot_source(digest_source), snapshot_source(digest_test_source)
        assert digest_test == subprocess.check_output(['git', 'show', 'HEAD:' + digest_test_source.as_posix()])
        assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(digest_test_source)]) == b''
        digest_anchor, digest_changed = b'|| !p.digest().equals(b.previewDigest())', b'|| false'
        assert digest_current.count(digest_anchor) == 1
        digest_negative = digest_current.replace(digest_anchor, digest_changed)
        assert digest_negative.replace(digest_changed, digest_anchor) == digest_current
        digest_package = 'ru.rutcampustrack.auth.integration.SubjectDeletionProofHttpIT'
        digest_method = 'passwordAndExactPurposeScopedProofProduceOneDurableReplayableReceipt'
        digest_line = method_oracle_line(digest_test, digest_method,
            'assertPrivate(call("verify", new VerifyRequest(token, changedDigest, id, code)), 409);')
        helper_line = method_oracle_line(digest_test, 'assertPrivate',
            'assertThat(response.getStatusCode().value()).isEqualTo(expected);')
        subject_digest = {'package': digest_package, 'method': digest_method,
                          'source': digest_test_source, 'production': digest_source}
        replacements[digest_source] = digest_negative
        proof['subjectDigestRule'] = {
            'counterexample': 'OMITTED_SUBJECT_PREVIEW_DIGEST_BINDING',
            'testMethod': digest_package + '.' + digest_method,
            'testSourceSha256': hashlib.sha256(digest_test).hexdigest(),
            'productionSourceSha256': hashlib.sha256(digest_current).hexdigest(),
            'counterexampleSourceSha256': hashlib.sha256(digest_negative).hexdigest(),
            'ruleFrame': digest_package + '.' + digest_method + '(SubjectDeletionProofHttpIT.java:' + str(digest_line) + ')',
            'helperFrame': digest_package + '.assertPrivate(SubjectDeletionProofHttpIT.java:' + str(helper_line) + ')'}

    elif service == 'notification-service':
        package = 'ru.rutcampustrack.notification.history.NotificationStateV2IT'
        method = 'webPush503LeavesInboxReadableAndRetriesOnceWithoutDuplicatingHistoryOrDelivery'
        test_source = app / 'src/test/java/ru/rutcampustrack/notification/history/NotificationStateV2IT.java'
        snapshot_source(test_source)
        path = app / 'src/main/java/ru/rutcampustrack/notification/push/NotificationPushIntentDispatcher.java'
        source = snapshot_source(path)
        original = "if ((status >= 200 && status < 300) || status == 404 || status == 410) {".encode()
        assert source.count(original) == 1
        negative = source.replace(original, "if ((status >= 200 && status < 300) || status == 404 || status == 410 || status == 503) {".encode())
        replacements = {path: negative}
        notification_line = method_oracle_line(snapshot_source(test_source), method,
            'assertThat(pending.getString("state")).isEqualTo("PENDING");')
        proof.update(counterexample='HTTP503_ACKNOWLEDGED_AS_SUCCESS',
                     ruleFrame=package + '.' + method + '(NotificationStateV2IT.java:' + str(notification_line) + ')')
    else:
        package = 'ru.rutcampustrack.attendance.studentrequest.StudentRequestDomainIT'
        method = 'headmanIncreasePreservesUsedAndHasOnePermanentExactReplayReceipt'
        test_source = app / 'src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java'
        path = app / 'src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java'
        original = b'long increased = (long) before.limit() + command.increase();'
        source = snapshot_source(path)
        assert source.count(original) == 1
        replacements = {path: source.replace(original, b'long increased = (long) before.limit();')}
        proof['counterexample'] = 'OMITTED_BUDGET_INCREMENT'
        student_source = test_source
        student_test = snapshot_source(student_source)
        student_production = app / 'src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java'
        student_current = snapshot_source(student_production)
        student_anchor = (b'        long groupId = scope.getRankGroupId();\n'
                          b'        if (claims.groupId() == null\n'
                          b'                || claims.groupId() <= 0\n'
                          b'                || groupId != claims.groupId()\n'
                          b'                || !rankingVisible(scope)) {\n')
        student_changed = student_anchor.replace(b'                || groupId != claims.groupId()\n', b'')
        assert student_current.count(student_anchor) == 1
        student_negative = student_current.replace(student_anchor, student_changed)
        assert student_negative.replace(student_changed, student_anchor) == student_current
        student_line = method_oracle_line(student_test,
            'studentWeeklyGroupComparisonUsesPersistedHistoricalCellsAndRejectsForeignCatalogue',
            'assertThatThrownBy(() -> projection.project(claims, SEMESTER_ID, null, "weeks", List.of()))')
        scoped_read = {'package': package,
                       'method': 'studentWeeklyGroupComparisonUsesPersistedHistoricalCellsAndRejectsForeignCatalogue',
                       'source': student_source, 'production': student_production, 'file': 'StudentRequestDomainIT.java',
                       'proofKey': 'studentGroupRule', 'ruleLine': student_line,
                       'testSha': hashlib.sha256(student_test).hexdigest(),
                       'productionSha': hashlib.sha256(student_current).hexdigest(),
                       'negative': student_negative, 'counterexample': 'OMITTED_CURRENT_GROUP_CATALOGUE_MISMATCH'}

    if service == 'attendance-service':
        backfill_source = app / 'src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java'
        backfill_test = snapshot_source(backfill_source)
        backfill_production = app / 'src/main/java/ru/rutcampustrack/attendance/event/InitialMembershipAttendanceBackfillService.java'
        backfill_current = snapshot_source(backfill_production)
        for path in (backfill_source, backfill_production):
            assert snapshot_source(path) == subprocess.check_output(['git', 'show', 'HEAD:' + path.as_posix()])
            assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(path)]) == b''
        backfill_line = method_oracle_line(backfill_test,
            'lateInitialMembershipBackfillsOnlyDatedTargetAfterCommittedCloseAndReplaysAfterSeal',
            'assertThat(inserted != null)\n                .as("late initial member must have a stored attendance record")\n                .isTrue();')
        backfill_anchor = b'                        lessons.applyInitialMembership(snapshot);'
        backfill_changed = b'                        // Deliberate sensitivity fault: omit the admitted target effect.'
        assert backfill_current.count(backfill_anchor) == 1
        backfill_negative = backfill_current.replace(backfill_anchor, backfill_changed, 1)
        assert backfill_negative.replace(backfill_changed, backfill_anchor) == backfill_current
        initial_membership = {'package': 'ru.rutcampustrack.attendance.integration.EventConsumerIT',
                              'method': 'lateInitialMembershipBackfillsOnlyDatedTargetAfterCommittedCloseAndReplaysAfterSeal',
                              'source': backfill_source, 'production': backfill_production, 'ruleLine': backfill_line}
        replacements[backfill_production] = backfill_negative
        proof['initialMembershipRule'] = {
            'counterexample': 'OMITTED_INITIAL_MEMBERSHIP_TARGET_EFFECT',
            'testMethod': initial_membership['package'] + '.' + initial_membership['method'],
            'testSourceSha256': hashlib.sha256(backfill_test).hexdigest(),
            'productionSourceSha256': hashlib.sha256(backfill_current).hexdigest(),
            'counterexampleSourceSha256': hashlib.sha256(backfill_negative).hexdigest(),
            'ruleFrame': initial_membership['package'] + '.' + initial_membership['method'] + '(EventConsumerIT.java:' + str(backfill_line) + ')'}

    if service == 'academic-service':
        spring_source = app / 'src/test/java/ru/rutcampustrack/academic/group/GroupPromotionCycleIT.java'
        spring_test = snapshot_source(spring_source)
        assert spring_test == subprocess.check_output(['git', 'show', 'HEAD:' + spring_source.as_posix()])
        assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(spring_source)]) == b''
        spring_line = method_oracle_line(spring_test,
            'unfinishedSpringRejectsSignedManualPreviewAndExecutionWithoutChangingDurableState',
            'assertThat(unfinishedPreview.getResponse().getStatus()).isEqualTo(409);')
        spring_service = app / 'src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java'
        spring_repository = app / 'src/main/java/ru/rutcampustrack/academic/repository/SemesterRepository.java'
        spring_faults = {
            spring_service: (
                b'        if (requested.getSemesterType() != SemesterType.SPRING\n                || !cycleDateTo(requested).isBefore(LocalDate.now(clock))) {',
                b'        if (requested.getSemesterType() != SemesterType.SPRING) {'),
            spring_repository: (
                b'               AND COALESCE(promotion_cycle_date_to, date_to) < :today',
                b'               AND (COALESCE(promotion_cycle_date_to, date_to) < :today OR TRUE)'),
        }
        for path, (anchor, changed) in spring_faults.items():
            source = snapshot_source(path)
            assert source == subprocess.check_output(['git', 'show', 'HEAD:' + path.as_posix()])
            assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(path)]) == b''
            assert path not in replacements and source.count(anchor) == 1
            negative = source.replace(anchor, changed, 1)
            assert negative.replace(changed, anchor, 1) == source
            replacements[path] = negative
        spring_completion = {'package': 'ru.rutcampustrack.academic.group.GroupPromotionCycleIT',
                             'method': 'unfinishedSpringRejectsSignedManualPreviewAndExecutionWithoutChangingDurableState',
                             'source': spring_source, 'production': spring_faults, 'ruleLine': spring_line}
        proof['springCompletionRule'] = {
            'counterexample': 'OMITTED_SPRING_COMPLETION_VALIDATION',
            'testMethod': spring_completion['package'] + '.' + spring_completion['method'],
            'testSourceSha256': hashlib.sha256(spring_test).hexdigest(),
            'productionSourceSha256': {str(path): hashlib.sha256(source_snapshots[path]).hexdigest() for path in spring_faults},
            'counterexampleSourceSha256': {str(path): hashlib.sha256(replacements[path]).hexdigest() for path in spring_faults},
            'ruleFrame': spring_completion['package'] + '.' + spring_completion['method'] + '(GroupPromotionCycleIT.java:' + str(spring_line) + ')'}

        # Captured teacher pairs moved to Academic; T008 grant policy is unchanged.
        teacher_source = app / 'src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java'
        teacher_test = snapshot_source(teacher_source)
        teacher_production = app / 'src/main/java/ru/rutcampustrack/academic/exportjobs/LocalReportAuthority.java'
        teacher_current = snapshot_source(teacher_production)
        teacher_anchor = '                    if (ready && !current.containsAll(identity.teacherScope())) throw denied();\n'.encode()
        assert teacher_current.count(teacher_anchor) == 1
        teacher_negative = teacher_current.replace(teacher_anchor, b'', 1)
        teacher_line = method_oracle_line(teacher_test,
            'teacherReadyArtifactRequiresEveryCapturedCurrentAssignmentAfterScopeShrinks',
            'assertThatThrownBy(() -> local.require(captured, true))')
        scoped_read = {'package': 'ru.rutcampustrack.academic.exportjobs.AcademicExportJobsIT',
                       'method': 'teacherReadyArtifactRequiresEveryCapturedCurrentAssignmentAfterScopeShrinks',
                       'source': teacher_source, 'production': teacher_production, 'file': 'AcademicExportJobsIT.java',
                       'proofKey': 'teacherReadyRule', 'ruleLine': teacher_line,
                       'testSha': hashlib.sha256(teacher_test).hexdigest(),
                       'productionSha': hashlib.sha256(teacher_current).hexdigest(),
                       'negative': teacher_negative, 'counterexample': 'OMITTED_CAPTURED_TEACHER_PAIRS_REAUTHORIZATION'}

    # These four accepted boundary checks share the existing bounded RED/native gate.
    boundary_rules = {
        'attendance-service': {
            'package': 'ru.rutcampustrack.attendance.studentrequest.StudentRequestDomainIT',
            'method': 'blockedLessonDeniesManualRequestButAllowsExcuseAndUnblockRestoresEligibility',
            'source': 'services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java',
            'production': 'services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java',
            'anchor': '        if (lesson.blocked()) {\n            throw new ConflictException("Пара заблокирована");\n        }\n',
            'changed': '',
            'counterexample': 'OMITTED_BLOCKED_LESSON_MANUAL_REQUEST_GUARD',
            'ruleSource': '.as("blocked lesson denies manual request option")\n                .isFalse();',
            'messagePattern': '\\s*\\[blocked lesson denies manual request option\\]\\s*Expecting value to be false but was true\\s*',
        },
        'schedule-service': {
            'package': 'ru.rutcampustrack.schedule.oneoff.OneOffLessonControllerIT',
            'method': 'concurrentDifferentKeysForSamePhysicalSlot_haveOneDurableWinnerAndTypedConflict',
            'source': 'services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonControllerIT.java',
            'production': 'services/schedule-service/schedule-app/src/main/resources/db/migration/V17__student_occurrence_homework_binding.sql',
            'anchor': 'CREATE UNIQUE INDEX uq_lessons_included_physical_slot',
            'changed': 'CREATE INDEX uq_lessons_included_physical_slot',
            'counterexample': 'NONUNIQUE_INCLUDED_PHYSICAL_SLOT_INDEX',
            'ruleSource': '.containsExactly(201, 409);',
            'messagePattern': '\\s*Expecting actual:\\s*\\[201, 201\\]\\s*to contain exactly \\(and in same order\\):\\s*\\[201, 409\\]\\s*but some elements were not found:\\s*\\[409\\]\\s*and others were not expected:\\s*\\[201\\]\\s*',
        },
        'auth-service': {
            'package': 'ru.rutcampustrack.auth.integration.SubjectDeletionProofHttpIT',
            'method': 'loginOtpCannotAuthorizeSubjectDeletionAndRemainsUsableForLogin',
            'source': 'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SubjectDeletionProofHttpIT.java',
            'production': 'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/SubjectDeletionProofService.java',
            'anchor': '        if (!MessageDigest.isEqual(\n                p.codeHash().getBytes(StandardCharsets.US_ASCII),\n                hash(request.challengeId() + ":" + request.code())\n                        .getBytes(StandardCharsets.US_ASCII))) {\n            // Consumed proof retries cannot exhaust or mutate the already accepted command.\n            if (p.receipt() == null)\n                jdbc.update(\n                        "UPDATE subject_deletion_proofs SET attempts=attempts+1 WHERE"\n                                + " challenge_hash=?",\n                        hash(request.challengeId()));\n            return new Verification(false, null);\n        }\n',
            'changed': '',
            'counterexample': 'OMITTED_CHALLENGE_SCOPED_SUBJECT_PROOF_CODE_GUARD',
            'ruleSource': 'assertPrivate(denied, 409);',
            'messagePattern': '\\s*expected:\\s*409\\s+but was:\\s*200\\s*',
            'helperOracle': 'assertThat(response.getStatusCode().value()).isEqualTo(expected);',
        },
        'academic-service': {
            'package': 'ru.rutcampustrack.academic.admin.AdminAccessAuthorityIT',
            'method': 'unknownAndNullPermissionElementsAreRejectedWithoutAuthorityMutation',
            'source': 'services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/admin/AdminAccessAuthorityIT.java',
            'production': 'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/admin/AdminAccessService.java',
            'anchor': '        operations.adminAccess(target, input, expectedVersion, false);',
            'changed': '        operations.adminAccess(target, new AdminAccessInput(input.mode(), input.permissions().stream().filter(java.util.Objects::nonNull).filter(AdminPermission.allCodes()::contains).toList()), expectedVersion, false);',
            'counterexample': 'SILENTLY_FILTERED_UNKNOWN_AND_NULL_ADMIN_PERMISSION_ELEMENTS',
            'ruleSource': '.andExpect(status().isBadRequest())',
            'messagePattern': 'Status expected:<400> but was:<200>',
        },
    }
    if service in boundary_rules:
        accepted_boundary = boundary_rules[service]
        for key in ('source', 'production'):
            accepted_boundary[key] = pathlib.Path(accepted_boundary[key])
        proof['sourceAdmissionCheck'] = 'READ_SOURCE'
        boundary_source = snapshot_source(accepted_boundary['source'])
        proof['sourceAdmissionCheck'] = 'READ_PRODUCTION'
        boundary_current = snapshot_source(accepted_boundary['production'])
        proof['sourceAdmissionCheck'] = 'GIT_HEAD'
        assert boundary_source == subprocess.check_output(['git', 'show', 'HEAD:' + accepted_boundary['source'].as_posix()])
        for path in (accepted_boundary['source'], accepted_boundary['production']):
            proof['sourceAdmissionCheck'] = ('CLEAN_TEST' if path == accepted_boundary['source']
                                             else 'CLEAN_PRODUCTION')
            assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(path)]) == b''
        proof['sourceAdmissionCheck'] = 'FRAME'
        accepted_boundary['ruleLine'] = method_oracle_line(
            boundary_source, accepted_boundary['method'], accepted_boundary['ruleSource'])
        if 'helperOracle' in accepted_boundary:
            accepted_boundary['helperLine'] = method_oracle_line(
                boundary_source, 'assertPrivate', accepted_boundary['helperOracle'])
        # Flyway migration bytes remain an immutable integrity boundary.
        if accepted_boundary['production'].suffix == '.sql':
            assert hashlib.sha256(boundary_current).hexdigest() == '8d02720f48b67e21c78373b214adb414a2f435a8016c35987839d330b6042fcc'
        boundary_base = replacements.get(accepted_boundary['production'], boundary_current)
        boundary_anchor = accepted_boundary['anchor'].encode()
        proof['sourceAdmissionCheck'] = 'ANCHOR'
        assert boundary_base.count(boundary_anchor) == 1
        boundary_negative = boundary_base.replace(boundary_anchor, accepted_boundary['changed'].encode(), 1)
        proof.pop('sourceAdmissionCheck', None)
        replacements[accepted_boundary['production']] = boundary_negative
        boundary_file = accepted_boundary['source'].name
        proof['acceptedBoundaryRule'] = {
            'counterexample': accepted_boundary['counterexample'],
            'testMethod': accepted_boundary['package'] + '.' + accepted_boundary['method'],
            'testSourceSha256': hashlib.sha256(boundary_source).hexdigest(),
            'productionSourceSha256': hashlib.sha256(boundary_current).hexdigest(),
            'counterexampleSourceSha256': hashlib.sha256(boundary_negative).hexdigest(),
            'ruleFrame': accepted_boundary['package'] + '.' + accepted_boundary['method'] + '(' + boundary_file + ':' + str(accepted_boundary['ruleLine']) + ')'}

    if scoped_read is not None:
        assert snapshot_source(scoped_read['source']) == subprocess.check_output(['git', 'show', 'HEAD:' + scoped_read['source'].as_posix()])
        assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(scoped_read['source'])]) == b''
        replacements[scoped_read['production']] = scoped_read['negative']
        proof[scoped_read['proofKey']] = {
            'counterexample': scoped_read['counterexample'],
            'testMethod': scoped_read['package'] + '.' + scoped_read['method'],
            'testSourceSha256': scoped_read['testSha'],
            'productionSourceSha256': scoped_read['productionSha'],
            'counterexampleSourceSha256': hashlib.sha256(scoped_read['negative']).hexdigest(),
            'ruleFrame': scoped_read['package'] + '.' + scoped_read['method'] + '(' + scoped_read['file'] + ':' + str(scoped_read['ruleLine']) + ')'}

    for path in replacements:
        assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(path)]) == b''
        current[path] = snapshot_source(path)
    proof['testMethod'] = package + '.' + method
    proof['testSourceSha256'] = hashlib.sha256(snapshot_source(test_source)).hexdigest()
    proof['restoredSourceSha256'] = {str(p): hashlib.sha256(v).hexdigest() for p, v in current.items()}
    proof['counterexampleSourceSha256'] = {str(p): hashlib.sha256(v).hexdigest() for p, v in replacements.items()}
    for source, name in [(results, 'previous-results'), (reports, 'previous-reports')]:
        if source.exists():
            shutil.move(source, private / name)
    for path, content in replacements.items():
        path.write_bytes(content)
    proof['phase'] = 'RED_RUN'
    selectors = ['--tests', package + '.' + method]
    if additional is not None:
        selectors += ['--tests', additional['package'] + '.' + additional['method']]
    if audit is not None:
        selectors += ['--tests', audit['package'] + '.' + audit['method']]
    if admin_rights is not None:
        selectors += ['--tests', admin_rights['package'] + '.' + admin_rights['method']]
    if scoped_read is not None:
        selectors += ['--tests', scoped_read['package'] + '.' + scoped_read['method']]
    if progress is not None:
        selectors += ['--tests', progress['package'] + '.' + progress['method']]
    if subject_digest is not None:
        selectors += ['--tests', subject_digest['package'] + '.' + subject_digest['method']]
    if accepted_boundary is not None:
        selectors += ['--tests', accepted_boundary['package'] + '.' + accepted_boundary['method']]
    if initial_membership is not None:
        selectors += ['--tests', initial_membership['package'] + '.' + initial_membership['method']]
    if spring_completion is not None:
        selectors += ['--tests', spring_completion['package'] + '.' + spring_completion['method']]
    with (private / 'red.private.log').open('wb') as log:
        run = subprocess.run(['./gradlew', ':services:' + service + ':' + app.name + ':integrationTest',
                              '--no-daemon', *(['--no-build-cache'] if audit is not None or service == 'auth-service' or scoped_read is not None or progress is not None else []), *selectors],
                             stdout=log, stderr=subprocess.STDOUT, timeout=360)
    proof['nativeExit'] = run.returncode
    assert run.returncode != 0
    proof['phase'] = 'XML_RULE'
    result = results / ('TEST-' + package + '.xml')
    assert result.is_file() and result.stat().st_size <= 33554432
    data = result.read_bytes()
    assert b'<!DOCTYPE' not in data.upper() and b'<!ENTITY' not in data.upper()
    suite = ET.fromstring(data)
    cases = suite.findall('testcase')
    if service == 'attendance-service':
        assert suite.tag == 'testsuite' and suite.get('name') == package
        assert {key: suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': '3', 'failures': '3', 'errors': '0', 'skipped': '0'}
        assert len(cases) == 3
        assert {case.get('name') for case in cases} == {method + '()', scoped_read['method'] + '()', accepted_boundary['method'] + '()'}
        assert all(case.get('classname') == package for case in cases)
        selected = [case for case in cases if case.get('name') == method + '()']
        assert len(selected) == 1
        case = selected[0]
    else:
        assert len(cases) == 1 and cases[0].get('name') == method + '()'
        case = cases[0]
    assert not case.findall('error') and not case.findall('skipped') and len(case.findall('failure')) == 1
    failure = case.find('failure')
    if service == 'auth-service':
        assert suite.tag == 'testsuite' and suite.get('name') == package
        assert {key: suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': '1', 'failures': '1', 'errors': '0', 'skipped': '0'}
        assert case.get('classname') == package
        auth_failure_categories = {
            'org.opentest4j.AssertionFailedError': 'ASSERTION',
            'java.lang.AssertionError': 'ASSERTION',
            'ru.rutcampustrack.auth.session.AuthSessionException': 'AUTH_SESSION',
            'ru.rutcampustrack.auth.exception.PasswordResetException': 'PASSWORD_RESET',
            'ru.rutcampustrack.auth.exception.OtpExpiredException': 'OTP_EXPIRED',
            'ru.rutcampustrack.auth.exception.OtpRateLimitException': 'OTP_RATE_LIMIT',
            'java.lang.IllegalStateException': 'ILLEGAL_STATE',
            'java.lang.IllegalArgumentException': 'ILLEGAL_ARGUMENT',
            'java.lang.NullPointerException': 'NULL_POINTER',
            'java.lang.ExceptionInInitializerError': 'INITIALIZER',
            'java.lang.NoClassDefFoundError': 'CLASS_INITIALIZATION',
            'org.testcontainers.containers.ContainerLaunchException': 'CONTAINER_START',
            'org.rnorth.ducttape.RetryCountExceededException': 'CONTAINER_RETRY',
            'org.springframework.amqp.AmqpConnectException': 'AMQP_CONNECT',
            'org.springframework.amqp.AmqpIOException': 'AMQP_IO',
            'org.springframework.dao.DataAccessException': 'DATABASE',
            'org.postgresql.util.PSQLException': 'DATABASE',
            'org.springframework.web.client.ResourceAccessException': 'HTTP_TRANSPORT',
            'org.springframework.web.client.RestClientException': 'HTTP_CONVERSION',
            'org.springframework.web.client.UnknownContentTypeException': 'HTTP_CONVERSION',
            'org.springframework.http.converter.HttpMessageConversionException': 'HTTP_CONVERSION',
            'org.springframework.http.converter.HttpMessageNotReadableException': 'HTTP_CONVERSION',
            'org.springframework.jdbc.BadSqlGrammarException': 'DATABASE',
            'org.springframework.dao.EmptyResultDataAccessException': 'DATABASE',
            'org.springframework.dao.DataIntegrityViolationException': 'DATABASE',
        }
        proof['failureCategory'] = auth_failure_categories.get(failure.get('type'), 'OTHER')
        auth_named_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(package + '.' + method)
            + r'\(AuthOtpFlowIT\.java:([1-9][0-9]{0,5})\)[ \t]*$', failure.text or '')
        proof['namedMethodFrameCount'] = len(auth_named_lines)
        proof['expectedRuleFrameMatched'] = bool(
            re.search(r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
                      + re.escape(proof['ruleFrame']) + r'[ \t]*$', failure.text or ''))
        if len(auth_named_lines) == 1:
            proof['observedNamedMethodLine'] = int(auth_named_lines[0])
    assert failure.get('type') in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}
    message = failure.get('message', '')
    if service == 'schedule-service':
        assert re.search(r'expected:\s*true\s+but was:\s*false(?:\s|$)', message)
        lines = test_source.read_text().splitlines()
        target = 'assertThat(response.hasPreviousRoom()).isEqualTo(previous != null);'
        assert sum(target in line for line in lines) == 1
        line_number = next(i for i, line in enumerate(lines, 1) if target in line)
        assert 'assertRoomProjection(LessonRoomChangeIT.java:' + str(line_number) + ')' in (failure.text or '')
        proof.update(expectedPreviousRoomPresent=True, observedPreviousRoomPresent=False)
    elif service == 'academic-service':
        assert suite.tag == 'testsuite' and suite.get('name') == package
        assert {key: suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': '1', 'failures': '1', 'errors': '0', 'skipped': '0'}
        assert case.get('classname') == package
        message = message.removeprefix(failure.get('type') + ': ')
        assert re.fullmatch(r'\s*Expecting value to be true but was false\s*', message)
        named_frame = re.escape(package + '.' + method)
        named_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + named_frame
            + r'\(UserArchiveLifecycleIT\.java:([1-9][0-9]{0,5})\)[ \t]*$',
            failure.text or '')
        proof['namedMethodFrameCount'] = len(named_lines)
        proof['expectedRuleFrameMatched'] = bool(
            re.search(r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
                      + re.escape(proof['ruleFrame']) + r'[ \t]*$', failure.text or ''))
        if len(named_lines) == 1:
            (observed_line,) = named_lines
            proof['observedNamedMethodLine'] = int(observed_line)
            proof['uniqueNamedMethodPlainLayout'] = bool(
                re.search(r'(?m)^[ \t]*at[ \t]+' + named_frame
                          + r'\(UserArchiveLifecycleIT\.java:' + observed_line + r'\)[ \t]*$',
                          failure.text or ''))
        assert proof['namedMethodFrameCount'] == 1 and proof['expectedRuleFrameMatched']
        proof.update(affectedRows=1, expectedRetentionEqual=True, observedRetentionEqual=False)
    elif service == 'auth-service':
        assert suite.tag == 'testsuite' and suite.get('name') == package
        assert {key: suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': '1', 'failures': '1', 'errors': '0', 'skipped': '0'}
        assert case.get('classname') == package
        message = message.removeprefix(failure.get('type') + ': ')
        assert re.fullmatch(r'\s*Expecting value to be true but was false\s*', message)
        named_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(package + '.' + method)
            + r'\(AuthOtpFlowIT\.java:([1-9][0-9]{0,5})\)[ \t]*$', failure.text or '')
        proof['namedMethodFrameCount'] = len(named_lines)
        proof['expectedRuleFrameMatched'] = bool(
            re.search(r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
                      + re.escape(proof['ruleFrame']) + r'[ \t]*$', failure.text or ''))
        assert proof['namedMethodFrameCount'] == 1 and proof['expectedRuleFrameMatched']
        proof.update(expectedNoOtpDelivery=True, observedNoOtpDelivery=False)
    elif service == 'notification-service':
        assert suite.tag == 'testsuite' and suite.get('name') == package
        assert {key: suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': '1', 'failures': '1', 'errors': '0', 'skipped': '0'}
        assert case.get('classname') == package
        message = message.removeprefix(failure.get('type') + ': ')
        assert re.fullmatch(r'\s*expected:\s*"PENDING"\s+but was:\s*"COMPLETED"\s*', message)
        named_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(package + '.' + method)
            + r'\(NotificationStateV2IT\.java:([1-9][0-9]{0,5})\)[ \t]*$', failure.text or '')
        proof['namedMethodFrameCount'] = len(named_lines)
        proof['expectedRuleFrameMatched'] = bool(
            re.search(r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
                      + re.escape(proof['ruleFrame']) + r'[ \t]*$', failure.text or ''))
        assert proof['namedMethodFrameCount'] == 1 and proof['expectedRuleFrameMatched']
        proof.update(expectedIntentState='PENDING', observedIntentState='COMPLETED')
    else:
        assert '[headman budget increased limit]' in message
        assert re.search(r'expected:\s*7\s+but was:\s*5(?:\s|$)', message)
        proof.update(expectedLimit=7, observedLimit=5)
    if additional is not None:
        identity_result = results / ('TEST-' + additional['package'] + '.xml')
        assert identity_result.is_file() and 0 < identity_result.stat().st_size <= 33554432
        identity_data = identity_result.read_bytes()
        assert b'<!DOCTYPE' not in identity_data.upper() and b'<!ENTITY' not in identity_data.upper()
        identity_suite = ET.fromstring(identity_data)
        assert identity_suite.tag == 'testsuite' and identity_suite.get('name') == additional['package']
        assert {key: identity_suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': '1', 'failures': '1', 'errors': '0', 'skipped': '0'}
        identity_cases = identity_suite.findall('testcase')
        assert len(identity_cases) == 1
        identity_case = identity_cases[0]
        assert identity_case.get('classname') == additional['package'] and identity_case.get('name') == additional['method'] + '()'
        assert not identity_case.findall('error') and not identity_case.findall('skipped') and len(identity_case.findall('failure')) == 1
        identity_failure = identity_case.find('failure')
        assert identity_failure.get('type') in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}
        identity_message = identity_failure.get('message', '').removeprefix(identity_failure.get('type') + ': ')
        label = 'Adding a role must preserve the existing account identity and credentials'
        assert re.fullmatch(r'\s*\[' + re.escape(label) + r'\]\s*Expecting value to be true but was false\s*', identity_message)
        identity_named_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(additional['package'] + '.' + additional['method'])
            + r'\(RestApiIT\.java:([1-9][0-9]{0,5})\)[ \t]*$', identity_failure.text or '')
        identity_rule = proof['additionalRule']
        identity_rule['namedMethodFrameCount'] = len(identity_named_lines)
        identity_rule['expectedRuleFrameMatched'] = bool(
            re.search(r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
                      + re.escape(identity_rule['ruleFrame']) + r'[ \t]*$', identity_failure.text or ''))
        assert identity_rule['namedMethodFrameCount'] == 1 and identity_rule['expectedRuleFrameMatched']
        identity_rule.update(state='EXPECTED_RULE_FAILURE', affectedRows=1,
                             expectedIdentityEqual=True, observedIdentityEqual=False,
                             tests=1, failures=1, errors=0, skipped=0,
                             restoredGreen='REQUIRED_IN_FOLLOWING_ORDINARY_INTEGRATION_GATE')
    if audit is not None:
        audit_result = results / ('TEST-' + audit['package'] + '.xml')
        assert audit_result.is_file() and 0 < audit_result.stat().st_size <= 33554432
        audit_data = audit_result.read_bytes()
        assert b'<!DOCTYPE' not in audit_data.upper() and b'<!ENTITY' not in audit_data.upper()
        audit_suite = ET.fromstring(audit_data)
        assert audit_suite.tag == 'testsuite' and audit_suite.get('name') == audit['package']
        assert {key: audit_suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': '1', 'failures': '1', 'errors': '0', 'skipped': '0'}
        audit_cases = audit_suite.findall('testcase')
        assert len(audit_cases) == 1
        audit_case = audit_cases[0]
        assert audit_case.get('classname') == audit['package'] and audit_case.get('name') == audit['method'] + '()'
        assert not audit_case.findall('error') and not audit_case.findall('skipped') and len(audit_case.findall('failure')) == 1
        audit_failure = audit_case.find('failure')
        assert audit_failure.get('type') in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}
        audit_message = audit_failure.get('message', '').removeprefix(audit_failure.get('type') + ': ')
        audit_values = re.fullmatch(r'\s*expected:\s*([1-9][0-9]{0,18})(L?)\s+but was:\s*([1-9][0-9]{0,18})(L?)\s*', audit_message)
        assert audit_values is not None
        expected_actor, expected_suffix, observed_actor, observed_suffix = audit_values.groups()
        assert expected_suffix == observed_suffix
        assert 0 < int(expected_actor) <= 9223372036854775807 and 0 < int(observed_actor) <= 9223372036854775807
        assert expected_actor != observed_actor
        audit_named_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(audit['package'] + '.' + audit['method'])
            + r'\(HomeworkEditLifecycleIT\.java:([1-9][0-9]{0,5})\)[ \t]*$', audit_failure.text or '')
        audit_rule = proof['auditRule']
        audit_rule['namedMethodFrameCount'] = len(audit_named_lines)
        audit_rule['expectedRuleFrameMatched'] = bool(
            re.search(r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
                      + re.escape(audit_rule['ruleFrame']) + r'[ \t]*$', audit_failure.text or ''))
        assert audit_rule['namedMethodFrameCount'] == 1 and audit_rule['expectedRuleFrameMatched']
        audit_rule.update(state='EXPECTED_RULE_FAILURE', expectedActorDiffersFromObservedCreator=True,
                          tests=1, failures=1, errors=0, skipped=0,
                          restoredGreen='REQUIRED_IN_FOLLOWING_ORDINARY_INTEGRATION_GATE')
    if admin_rights is not None:
        assert run.returncode == 1
        admin_result = results / ('TEST-' + admin_rights['package'] + '.xml')
        assert admin_result.is_file() and 0 < admin_result.stat().st_size <= 33554432
        admin_data = admin_result.read_bytes()
        assert b'<!DOCTYPE' not in admin_data.upper() and b'<!ENTITY' not in admin_data.upper()
        admin_suite = ET.fromstring(admin_data)
        assert admin_suite.tag == 'testsuite' and admin_suite.get('name') == admin_rights['package']
        assert {key: admin_suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': '1', 'failures': '1', 'errors': '0', 'skipped': '0'}
        admin_cases = admin_suite.findall('testcase')
        assert len(admin_cases) == 1
        admin_case = admin_cases[0]
        assert admin_case.get('classname') == admin_rights['package'] and admin_case.get('name') == admin_rights['method'] + '()'
        assert not admin_case.findall('error') and not admin_case.findall('skipped') and len(admin_case.findall('failure')) == 1
        admin_failure = admin_case.find('failure')
        assert admin_failure.get('type') in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}
        admin_message = admin_failure.get('message', '').removeprefix(admin_failure.get('type') + ': ')
        assert re.fullmatch(r'\s*Expecting code to raise a throwable\.\s*', admin_message)
        admin_named_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(admin_rights['package'] + '.' + admin_rights['method'])
            + r'\(AdminAttendanceReportIT\.java:([1-9][0-9]{0,5})\)[ \t]*$', admin_failure.text or '')
        admin_rule = proof['adminRightsRule']
        admin_rule['namedMethodFrameCount'] = len(admin_named_lines)
        admin_rule['expectedRuleFrameMatched'] = bool(
            re.search(r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
                      + re.escape(admin_rule['ruleFrame']) + r'[ \t]*$', admin_failure.text or ''))
        assert admin_rule['namedMethodFrameCount'] == 1 and admin_rule['expectedRuleFrameMatched']
        admin_rule.update(state='EXPECTED_RULE_FAILURE', expectedThrowableMissing=True,
                          tests=1, failures=1, errors=0, skipped=0,
                          restoredGreen='REQUIRED_IN_FOLLOWING_ORDINARY_INTEGRATION_GATE')
    if scoped_read is not None:
        assert run.returncode == 1
        scoped_result = results / ('TEST-' + scoped_read['package'] + '.xml')
        assert scoped_result.is_file() and 0 < scoped_result.stat().st_size <= 33554432
        scoped_data = scoped_result.read_bytes()
        assert b'<!DOCTYPE' not in scoped_data.upper() and b'<!ENTITY' not in scoped_data.upper()
        scoped_suite = ET.fromstring(scoped_data)
        total = '3' if service == 'attendance-service' else '1'
        assert scoped_suite.tag == 'testsuite' and scoped_suite.get('name') == scoped_read['package']
        assert {key: scoped_suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': total, 'failures': total, 'errors': '0', 'skipped': '0'}
        scoped_cases = scoped_suite.findall('testcase')
        expected_names = {scoped_read['method'] + '()'}
        if service == 'attendance-service':
            expected_names.update({method + '()', accepted_boundary['method'] + '()'})
        assert len(scoped_cases) == int(total)
        assert {case.get('name') for case in scoped_cases} == expected_names
        assert all(case.get('classname') == scoped_read['package'] for case in scoped_cases)
        selected = [case for case in scoped_cases if case.get('name') == scoped_read['method'] + '()']
        assert len(selected) == 1
        scoped_case = selected[0]
        assert not scoped_case.findall('error') and not scoped_case.findall('skipped') and len(scoped_case.findall('failure')) == 1
        scoped_failure = scoped_case.find('failure')
        assert scoped_failure.get('type') in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}
        scoped_message = scoped_failure.get('message', '').removeprefix(scoped_failure.get('type') + ': ')
        assert re.fullmatch(r'\s*Expecting code to raise a throwable\.\s*', scoped_message)
        scoped_named_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(scoped_read['package'] + '.' + scoped_read['method'])
            + r'\(' + re.escape(scoped_read['file']) + r':([1-9][0-9]{0,5})\)[ \t]*$', scoped_failure.text or '')
        scoped_rule = proof[scoped_read['proofKey']]
        scoped_rule['namedMethodFrameCount'] = len(scoped_named_lines)
        scoped_rule['expectedRuleFrameMatched'] = bool(
            re.search(r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
                      + re.escape(scoped_rule['ruleFrame']) + r'[ \t]*$', scoped_failure.text or ''))
        assert scoped_rule['namedMethodFrameCount'] == 1 and scoped_rule['expectedRuleFrameMatched']
        scoped_rule.update(state='EXPECTED_RULE_FAILURE', expectedDenialMissing=True,
                           tests=1, failures=1, errors=0, skipped=0,
                           restoredGreen='REQUIRED_IN_FOLLOWING_ORDINARY_INTEGRATION_GATE')

    if subject_digest is not None:
        assert run.returncode == 1
        digest_result = results / ('TEST-' + subject_digest['package'] + '.xml')
        assert digest_result.is_file() and not digest_result.is_symlink() and 0 < digest_result.stat().st_size <= 33554432
        digest_data = digest_result.read_bytes()
        assert b'<!DOCTYPE' not in digest_data.upper() and b'<!ENTITY' not in digest_data.upper()
        digest_suite = ET.fromstring(digest_data)
        assert digest_suite.tag == 'testsuite' and digest_suite.get('name') == subject_digest['package']
        assert {key: digest_suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': '2', 'failures': '2', 'errors': '0', 'skipped': '0'}
        digest_cases = digest_suite.findall('testcase')
        assert len(digest_cases) == 2
        assert {case.get('name') for case in digest_cases} == {subject_digest['method'] + '()', accepted_boundary['method'] + '()'}
        assert all(case.get('classname') == subject_digest['package'] for case in digest_cases)
        digest_selected = [case for case in digest_cases if case.get('name') == subject_digest['method'] + '()']
        assert len(digest_selected) == 1
        digest_case = digest_selected[0]
        assert not digest_case.findall('error') and not digest_case.findall('skipped') and len(digest_case.findall('failure')) == 1
        digest_failure = digest_case.find('failure')
        assert digest_failure.get('type') in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}
        digest_message = digest_failure.get('message', '').removeprefix(digest_failure.get('type') + ': ')
        assert re.fullmatch(r'\s*expected:\s*409\s+but was:\s*200\s*', digest_message)
        digest_named_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(subject_digest['package'] + '.' + subject_digest['method'])
            + r'\(SubjectDeletionProofHttpIT\.java:([1-9][0-9]{0,5})\)[ \t]*$', digest_failure.text or '')
        digest_rule = proof['subjectDigestRule']
        digest_rule['namedMethodFrameCount'] = len(digest_named_lines)
        digest_rule['expectedRuleFrameMatched'] = bool(
            re.search(r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
                      + re.escape(digest_rule['ruleFrame']) + r'[ \t]*$', digest_failure.text or ''))
        digest_rule['expectedHelperFrameMatched'] = len(re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(digest_rule['helperFrame']) + r'[ \t]*$', digest_failure.text or '')) == 1
        assert (digest_rule['namedMethodFrameCount'] == 1 and digest_rule['expectedRuleFrameMatched']
                and digest_rule['expectedHelperFrameMatched'])
        digest_rule.update(state='EXPECTED_RULE_FAILURE', expectedStatus=409, observedStatus=200,
                           tests=1, failures=1, errors=0, skipped=0,
                           restoredGreen='REQUIRED_IN_FOLLOWING_ORDINARY_INTEGRATION_GATE')

    if progress is not None:
        assert run.returncode == 1
        progress_result = results / ('TEST-' + progress['package'] + '.xml')
        assert progress_result.is_file() and 0 < progress_result.stat().st_size <= 33554432
        progress_data = progress_result.read_bytes()
        assert b'<!DOCTYPE' not in progress_data.upper() and b'<!ENTITY' not in progress_data.upper()
        progress_suite = ET.fromstring(progress_data)
        assert progress_suite.tag == 'testsuite' and progress_suite.get('name') == progress['package']
        assert {key: progress_suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': '1', 'failures': '1', 'errors': '0', 'skipped': '0'}
        progress_cases = progress_suite.findall('testcase')
        assert len(progress_cases) == 1
        progress_case = progress_cases[0]
        assert progress_case.get('classname') == progress['package'] and progress_case.get('name') == progress['method'] + '()'
        assert not progress_case.findall('error') and not progress_case.findall('skipped') and len(progress_case.findall('failure')) == 1
        progress_failure = progress_case.find('failure')
        assert progress_failure.get('type') in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}
        progress_message = progress_failure.get('message', '').removeprefix(progress_failure.get('type') + ': ')
        assert re.fullmatch(r'JSON path "\$\.semesterProgress\.items\[0\]\.done" expected:<1> but was:<2>', progress_message)
        progress_named_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(progress['package'] + '.' + progress['method'])
            + r'\(LessonApiIT\.java:([1-9][0-9]{0,5})\)[ \t]*$', progress_failure.text or '')
        progress_rule = proof['semesterProgressRule']
        progress_rule['namedMethodFrameCount'] = len(progress_named_lines)
        progress_rule['expectedRuleFrameMatched'] = bool(
            re.search(r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
                      + re.escape(progress_rule['ruleFrame']) + r'[ \t]*$', progress_failure.text or ''))
        assert progress_rule['namedMethodFrameCount'] == 1 and progress_rule['expectedRuleFrameMatched']
        progress_rule.update(state='EXPECTED_RULE_FAILURE', expectedDone=1, observedDone=2,
                             tests=1, failures=1, errors=0, skipped=0,
                             restoredGreen='REQUIRED_IN_FOLLOWING_ORDINARY_INTEGRATION_GATE')

    if accepted_boundary is not None:
        assert run.returncode == 1
        boundary_result = results / ('TEST-' + accepted_boundary['package'] + '.xml')
        assert boundary_result.is_file() and not boundary_result.is_symlink() and 0 < boundary_result.stat().st_size <= 33554432
        boundary_data = boundary_result.read_bytes()
        assert b'<!DOCTYPE' not in boundary_data.upper() and b'<!ENTITY' not in boundary_data.upper()
        boundary_suite = ET.fromstring(boundary_data)
        expected_names = {accepted_boundary['method'] + '()'}
        if service == 'attendance-service':
            expected_names.update({method + '()', scoped_read['method'] + '()'})
        elif service == 'auth-service':
            expected_names.add(subject_digest['method'] + '()')
        total = str(len(expected_names))
        observed_counts = {key: boundary_suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')}
        observed_cases = boundary_suite.findall('testcase')
        proof['acceptedBoundaryRule'].update(
            observedSuiteCounts={key: int(value) if value is not None and re.fullmatch(r'(?:0|[1-9][0-9]?|100)', value) else 'UNKNOWN'
                                 for key, value in observed_counts.items()},
            expectedCount=len(expected_names),
            testcaseCount=len(observed_cases) if len(observed_cases) <= 100 else 'UNKNOWN',
            knownExpectedNamesExact={case.get('name') for case in observed_cases} == expected_names)
        assert boundary_suite.tag == 'testsuite' and boundary_suite.get('name') == accepted_boundary['package']
        assert {key: boundary_suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': total, 'failures': total, 'errors': '0', 'skipped': '0'}
        boundary_cases = boundary_suite.findall('testcase')
        assert len(boundary_cases) == len(expected_names)
        assert {case.get('name') for case in boundary_cases} == expected_names
        assert all(case.get('classname') == accepted_boundary['package'] for case in boundary_cases)
        boundary_selected = [case for case in boundary_cases if case.get('name') == accepted_boundary['method'] + '()']
        assert len(boundary_selected) == 1
        boundary_case = boundary_selected[0]
        assert not boundary_case.findall('error') and not boundary_case.findall('skipped') and len(boundary_case.findall('failure')) == 1
        boundary_failure = boundary_case.find('failure')
        assert boundary_failure.get('type') in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}
        boundary_message = boundary_failure.get('message', '').removeprefix(boundary_failure.get('type') + ': ')
        assert re.fullmatch(accepted_boundary['messagePattern'], boundary_message)
        boundary_named_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(accepted_boundary['package'] + '.' + accepted_boundary['method'])
            + r'\(' + re.escape(accepted_boundary['source'].name) + r':([1-9][0-9]{0,5})\)[ \t]*$', boundary_failure.text or '')
        assert len(boundary_named_lines) <= 16
        proof['acceptedBoundaryRule']['observedRuleLines'] = [int(line) for line in boundary_named_lines]
        assert boundary_named_lines == [str(accepted_boundary['ruleLine'])]
        if 'helperLine' in accepted_boundary:
            helper_frame = accepted_boundary['package'] + '.assertPrivate(' + accepted_boundary['source'].name + ':' + str(accepted_boundary['helperLine']) + ')'
            assert len(re.findall(r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?' + re.escape(helper_frame) + r'[ \t]*$', boundary_failure.text or '')) == 1
        proof['acceptedBoundaryRule'].update(
            state='EXPECTED_RULE_FAILURE', namedMethodFrameCount=1, expectedRuleFrameMatched=True,
            tests=1, failures=1, errors=0, skipped=0,
            restoredGreen='REQUIRED_IN_FOLLOWING_ORDINARY_INTEGRATION_GATE')

    if initial_membership is not None:
        assert run.returncode == 1
        backfill_result = results / ('TEST-' + initial_membership['package'] + '.xml')
        assert backfill_result.is_file() and not backfill_result.is_symlink() and 0 < backfill_result.stat().st_size <= 33554432
        backfill_data = backfill_result.read_bytes()
        assert b'<!DOCTYPE' not in backfill_data.upper() and b'<!ENTITY' not in backfill_data.upper()
        backfill_suite = ET.fromstring(backfill_data)
        assert backfill_suite.tag == 'testsuite' and backfill_suite.get('name') == initial_membership['package']
        assert {key: backfill_suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': '1', 'failures': '1', 'errors': '0', 'skipped': '0'}
        backfill_cases = backfill_suite.findall('testcase')
        assert len(backfill_cases) == 1
        backfill_case = backfill_cases[0]
        assert backfill_case.get('classname') == initial_membership['package'] and backfill_case.get('name') == initial_membership['method'] + '()'
        assert not backfill_case.findall('error') and not backfill_case.findall('skipped') and len(backfill_case.findall('failure')) == 1
        backfill_failure = backfill_case.find('failure')
        assert backfill_failure.get('type') in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}
        backfill_message = backfill_failure.get('message', '').removeprefix(backfill_failure.get('type') + ': ')
        assert re.fullmatch(r'\s*\[late initial member must have a stored attendance record\]\s*Expecting value to be true but was false\s*', backfill_message)
        backfill_named_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(initial_membership['package'] + '.' + initial_membership['method'])
            + r'\(EventConsumerIT\.java:([1-9][0-9]{0,5})\)[ \t]*$', backfill_failure.text or '')
        assert backfill_named_lines == [str(initial_membership['ruleLine'])]
        proof['initialMembershipRule'].update(
            state='EXPECTED_RULE_FAILURE', namedMethodFrameCount=1, expectedRuleFrameMatched=True,
            tests=1, failures=1, errors=0, skipped=0,
            restoredGreen='REQUIRED_IN_FOLLOWING_ORDINARY_INTEGRATION_GATE')

    if spring_completion is not None:
        assert run.returncode == 1
        spring_result = results / ('TEST-' + spring_completion['package'] + '.xml')
        assert spring_result.is_file() and not spring_result.is_symlink() and 0 < spring_result.stat().st_size <= 33554432
        spring_data = spring_result.read_bytes()
        assert b'<!DOCTYPE' not in spring_data.upper() and b'<!ENTITY' not in spring_data.upper()
        spring_suite = ET.fromstring(spring_data)
        assert spring_suite.tag == 'testsuite' and spring_suite.get('name') == spring_completion['package']
        assert {key: spring_suite.get(key) for key in ('tests', 'failures', 'errors', 'skipped')} == {
            'tests': '1', 'failures': '1', 'errors': '0', 'skipped': '0'}
        spring_cases = spring_suite.findall('testcase')
        assert len(spring_cases) == 1
        spring_case = spring_cases[0]
        assert spring_case.get('classname') == spring_completion['package'] and spring_case.get('name') == spring_completion['method'] + '()'
        assert not spring_case.findall('error') and not spring_case.findall('skipped') and len(spring_case.findall('failure')) == 1
        spring_failure = spring_case.find('failure')
        assert spring_failure.get('type') in {'org.opentest4j.AssertionFailedError', 'java.lang.AssertionError'}
        spring_message = spring_failure.get('message', '').removeprefix(spring_failure.get('type') + ': ')
        spring_observed_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(spring_completion['package'] + '.' + spring_completion['method'])
            + r'\(GroupPromotionCycleIT\.java:([1-9][0-9]{0,5})\)[ \t]*$', spring_failure.text or '')
        spring_status = re.fullmatch(r'Status expected:<([1-5][0-9]{2})> but was:<([1-5][0-9]{2})>', spring_message)
        spring_kind = 'MOCKMVC_HTTP_STATUS' if spring_status is not None else 'OTHER'
        if spring_status is None and spring_observed_lines == [str(spring_completion['ruleLine'])]:
            spring_status = re.fullmatch(r'\s*expected:\s*([1-5][0-9]{2})\s+but was:\s*([1-5][0-9]{2})\s*', spring_message)
            if spring_status is not None:
                spring_kind = 'ASSERTJ_INTEGER_STATUS'
        proof['springCompletionRule'].update(
            observedRuleLines=[int(line) for line in spring_observed_lines] if len(spring_observed_lines) <= 16 else 'UNKNOWN',
            messageKind=spring_kind,
            expectedHttpStatus=int(spring_status.group(1)) if spring_status is not None else 'UNKNOWN',
            actualHttpStatus=int(spring_status.group(2)) if spring_status is not None else 'UNKNOWN')
        assert re.fullmatch(r'\s*expected:\s*409\s+but was:\s*200\s*', spring_message)
        spring_named_lines = re.findall(
            r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'
            + re.escape(spring_completion['package'] + '.' + spring_completion['method'])
            + r'\(GroupPromotionCycleIT\.java:([1-9][0-9]{0,5})\)[ \t]*$', spring_failure.text or '')
        assert spring_named_lines == [str(spring_completion['ruleLine'])]
        proof['springCompletionRule'].update(
            state='EXPECTED_RULE_FAILURE', namedMethodFrameCount=1, expectedRuleFrameMatched=True,
            tests=1, failures=1, errors=0, skipped=0,
            restoredGreen='REQUIRED_IN_FOLLOWING_ORDINARY_INTEGRATION_GATE')

    proof.update(state='EXPECTED_RULE_FAILURE', tests=1, failures=1, errors=0, skipped=0,
                 restoredGreen='REQUIRED_IN_FOLLOWING_ORDINARY_INTEGRATION_GATE')
finally:
    for path, content in current.items():
        path.write_bytes(content)
        assert path.read_bytes() == content
        assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(path)]) == b''
    # Admission snapshots also cover test sources and unmodified dependencies.
    # Restoration is checked against this run's bytes, never an obsolete source pin.
    for path, content in source_snapshots.items():
        assert path.read_bytes() == content
    if additional is not None and current:
        proof['additionalRule']['sourceRestoredExact'] = True
    if audit is not None and current:
        proof['auditRule']['sourceRestoredExact'] = True
    if admin_rights is not None and current:
        assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(admin_rights['source'])]) == b''
        proof['adminRightsRule']['sourceRestoredExact'] = True
    if service == 'auth-service' and current:
        proof['sourceRestoredExact'] = True
    if service == 'notification-service' and current:
        proof['sourceRestoredExact'] = True
    if service == 'academic-service' and current:
        assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(test_source)]) == b''
        proof['sourceRestoredExact'] = True
    if scoped_read is not None and current:
        for path in (scoped_read['source'], scoped_read['production']):
            assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(path)]) == b''
        proof[scoped_read['proofKey']]['sourceRestoredExact'] = True
    if subject_digest is not None and current:
        for path in (subject_digest['source'], subject_digest['production']):
            assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(path)]) == b''
        proof['subjectDigestRule']['sourceRestoredExact'] = True
    if progress is not None and current:
        assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(progress['source'])]) == b''
        proof['semesterProgressRule']['sourceRestoredExact'] = True

    if accepted_boundary is not None and current:
        for path in (accepted_boundary['source'], accepted_boundary['production']):
            assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(path)]) == b''
        proof['acceptedBoundaryRule']['sourceRestoredExact'] = True

    if initial_membership is not None and current:
        for path in (initial_membership['source'], initial_membership['production']):
            assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(path)]) == b''
        proof['initialMembershipRule']['sourceRestoredExact'] = True

    if spring_completion is not None and current:
        assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(spring_completion['source'])]) == b''
        for path, values in spring_completion['production'].items():
            assert subprocess.check_output(['git', 'status', '--porcelain', '--', str(path)]) == b''
        proof['springCompletionRule']['sourceRestoredExact'] = True

    for source, name in [(results, 'red-results'), (reports, 'red-reports')]:
        if source.exists():
            shutil.move(source, private / name)
    output = pathlib.Path('build/ci-test-outcomes') / service
    output.mkdir(parents=True, exist_ok=True)
    (output / 'changed-domain-regression-safe.json').write_text(json.dumps(proof, indent=2) + '\n')
    if not os.environ.get('RCT_LOCAL_RELEASE_REVISION'):shutil.rmtree(private)
PY_DOMAIN_RED
