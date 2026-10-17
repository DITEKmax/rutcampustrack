from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path('/workspace/rutcampustrack')
OWN = Path('/workspace/scratch/rct-validation/export_merged_repository_review')
CURRENT = '2bca095380f72e8908a2511f2b10f0e795607f05'
BEFORE = 'c4d926b0c5a81ae00b639bd48dd22088991b0f2b'
AUTHOR = 'f09b70e383b1c29b31960b5da99647f8ae6530bf'
BASE = 'e51b97c5e9d2663aaf336899db3bb4d9463b9d88'
T001 = 'c115f61996fd415d20286a0787f4985b80a59a24'
REPO = 'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/SubjectRepository.java'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def blob(revision, path):
    return subprocess.check_output(['git', 'show', revision + ':' + path], cwd=ROOT)

def optional_blob(revision, path):
    exists = subprocess.run(['git', 'cat-file', '-e', revision + ':' + path], cwd=ROOT, stderr=subprocess.DEVNULL)
    return blob(revision, path) if exists.returncode == 0 else None

def save(name, data):
    (OWN / name).write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n')

OWN.mkdir(parents=True, exist_ok=True)
governing = ['AGENTS.md', 'services/AGENTS.md', 'tests/AGENTS.md', '.agent/orchestration-v2/RULES.md', '.agent/orchestration-v2/CURRENT.md', 'docs/agent-workflow.md', 'docs/validation/cloud-20261011/TASK-CONTRACT.md', 'docs/validation/cloud-20261011/CURRENT.md', 'docs/validation/cloud-20261011/decisions.md', 'docs/validation/cloud-20261011/questions.md', 'docs/validation/cloud-20261011/resources.md', 'docs/validation/cloud-20261011/packet-export-merged-repository-review.md', 'docs/validation/cloud-20261011/skills/project/rct-source-resolution/SKILL.md', 'docs/validation/cloud-20261011/skills/project/rct-verification/SKILL.md', 'docs/product/decisions/2026-09-23-teacher-replacement-and-group-attendance.md']
governing_rows = [{'path': p, 'bytes': (ROOT / p).stat().st_size, 'sha256': sha((ROOT / p).read_bytes())} for p in governing]
assert next(r['sha256'] for r in governing_rows if r['path'].endswith('/TASK-CONTRACT.md')) == '8cbd27d431815647886b2f39592905d3f1d4cb985643e5d9fa3f3f669b4df4a5'
save('governing-inputs.json', governing_rows)

versions = {label: blob(rev, REPO) for label, rev in [('merged', CURRENT), ('root_before', BEFORE), ('author', AUTHOR), ('base', BASE), ('accepted_t001', T001)]}
assert (ROOT / REPO).read_bytes() == versions['merged']
assert versions['root_before'] == versions['accepted_t001']
addition = b'    @Query(value = "select subject_id, lesson_type::text from subject_lesson_types where subject_id in (:ids) order by subject_id, lesson_type::text", nativeQuery = true)\n    List<Object[]> findLessonTypeCodes(@Param("ids") List<Long> ids);\n\n'
assert versions['merged'].count(addition) == versions['author'].count(addition) == 1
assert versions['merged'].replace(addition, b'') == versions['root_before']
assert versions['author'].replace(addition, b'') == versions['base']
teacher_start = b'    @Query("""\n            select s\n'
teacher_end = b'                                        Pageable pageable);'
teacher = versions['merged'][versions['merged'].index(teacher_start):versions['merged'].index(teacher_end) + len(teacher_end)]
assert teacher in versions['root_before'] and teacher in versions['accepted_t001']
for marker in [b"assignment.teacherId = :teacherId", b"assignment.semesterId = :semesterId", b"assignment.lifecycleState = 'ACTIVE'", b"operation.targetAssignmentId = assignment.id", b"operation.operationKind = 'ADOPT_UNASSIGNED'", b"operation.state in ('PREPARED', 'APPLIED')", b"assignment.validFrom <= :today", b"assignment.validUntilExclusive > :today"]:
    assert marker in teacher

private_sources = OWN / 'private-sources'
private_sources.mkdir(exist_ok=True)
for label in ['root_before', 'base', 'author']:
    (private_sources / (label + '.java')).write_bytes(versions[label])
merge = subprocess.run(['git', 'merge-file', '-p', str(private_sources / 'root_before.java'), str(private_sources / 'base.java'), str(private_sources / 'author.java')], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
assert merge.returncode == 0 and merge.stdout == versions['merged']
save('merged-source-proof.json', {'revision': CURRENT, 'root_before': BEFORE, 'source_author': AUTHOR, 'base': BASE, 't001_author': T001, 'path': REPO, 'versions': {k: {'sha256': sha(v), 'bytes': len(v)} for k, v in versions.items()}, 'teacher_query_block_sha256': sha(teacher), 'teacher_query_byte_identical_to_root_before_and_t001': True, 'batch_method_byte_identical_to_author': True, 'merged_minus_exact_batch_addition_equals_entire_root_before_file': True, 'author_minus_exact_batch_addition_equals_entire_base_file': True, 'native_git_merge_file_exit': merge.returncode, 'native_git_merge_file_matches_committed_root': True})

paths = subprocess.check_output(['git', 'diff', '--name-only', BASE, AUTHOR], cwd=ROOT, text=True).splitlines()
assert len(paths) == 97
equivalent = []
different = []
for path in paths:
    actual = blob(CURRENT, path)
    expected = blob(AUTHOR, path)
    assert (ROOT / path).read_bytes() == actual
    if actual == expected:
        equivalent.append({'path': path, 'sha256': sha(actual), 'exact_author_bytes': True})
    else:
        different.append(path)
assert different == [REPO] and len(equivalent) == 96
save('integrated-author-identity.json', {'revision': CURRENT, 'author': AUTHOR, 'exact_count': len(equivalent), 'paths': equivalent, 'intentional_merged_only': different})

dependencies = ['services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java', 'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectAssembler.java', 'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/AssignmentRepository.java', 'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Subject.java', 'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java', 'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/export/AdminRegistryExportService.java', 'services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClient.java', 'services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportService.java', 'services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectListDataIT.java', 'services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicGrpcIT.java', 'services/academic-service/academic-app/src/main/resources/db/migration/V25__student_subject_homework_foundation.sql', 'services/academic-service/academic-app/src/main/resources/db/migration/V34__assignment_replacement_operation.sql', 'services/academic-service/academic-app/src/main/resources/db/migration/V49__assignment_closure_unassigned.sql', 'proto/academic.proto']
dependency_rows = []
for path in dependencies:
    current = blob(CURRENT, path)
    assert (ROOT / path).read_bytes() == current
    before = optional_blob(BEFORE, path)
    author = optional_blob(AUTHOR, path)
    dependency_rows.append({'path': path, 'sha256': sha(current), 'bytes': len(current), 'existed_in_root_before': before is not None, 'unchanged_from_root_before': current == before, 'existed_in_author': author is not None, 'same_as_author': current == author})
save('dependency-inventory.json', dependency_rows)

receipts = ROOT / 'docs/validation/cloud-20261011/receipts'
reconciliation = json.loads((receipts / 'data-lecture-review/evidence-reconciliation.json').read_text())
native_xml_rows = []
for row in reconciliation['xml']:
    if row['suite'] != 'ru.rutcampustrack.academic.subject.SubjectListDataIT':
        continue
    path = Path(row['path'])
    original_bytes = path.read_bytes()
    assert sha(original_bytes) == row['sha256']
    suite = ET.fromstring(original_bytes)
    counts = {k: int(suite.get(k, '0')) for k in ['tests', 'failures', 'errors', 'skipped']}
    assert all(counts[k] == row[k] for k in counts)
    cases = []
    for case in suite.findall('testcase'):
        failure = case.find('failure')
        cases.append({'name': case.get('name'), 'outcome': 'FAIL' if failure is not None else 'SKIP' if case.find('skipped') is not None else 'PASS', 'failure_type': failure.get('type') if failure is not None else None})
    native_xml_rows.append({'attempt': row['attempt'], 'sha256': sha(original_bytes), 'counts': counts, 'cases': cases})
assert len(native_xml_rows) == 2
save('prior-t001-native-evidence.json', {'actual_native_xml': native_xml_rows, 'scope': 'Original red/green publication rule only; not execution of combined current repository', 'current_teacher_query_same_as_green_source': True})

ci = json.loads((receipts / 'ci-artifact-reconcile-636/l05-reconciliation.json').read_text())
test_path = 'services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectListDataIT.java'
assert sha(blob(CURRENT, test_path)) == ci['receipt']['sourceSha256']
prior_bridge = json.loads((receipts / 'ci-artifact-reconcile-636/l05-source-bridge.json').read_text())
prior_repo = next(row for row in prior_bridge['paths'] if row['path'] == REPO)
assert sha(blob(ci['receipt']['metadata']['prHeadSha'], REPO)) == prior_repo['producerSha256']
save('prior-l05-qualification.json', {'case': ci['caseId'], 'producer_head': ci['receipt']['metadata']['prHeadSha'], 'actual_checkout': ci['receipt']['metadata']['checkoutSha'], 'run_id': ci['receipt']['metadata']['runId'], 'test_source_sha256': ci['receipt']['sourceSha256'], 'test_source_still_exact': True, 'teacher_path_preserved': True, 'native_merge_tree_identity': 'Reused transferred native merge bridge; actual checkout tree not locally reopened or fetched by reviewer', 'producer_repository_sha256': prior_repo['producerSha256'], 'merged_repository_different_from_producer': sha(versions['merged']) != prior_repo['producerSha256'], 'current_integrated_runtime': 'NOT_RUN_BY_THIS_REVIEW', 'reuse_limit': 'Earlier specific L05 page-method result remains qualified; no current merged startup/full Academic PASS.'})

save('CURRENT.json', {'at': datetime.now(timezone.utc).isoformat(), 'verdict': 'SOURCE_REVIEW_COMPLETE_PENDING_REPORT', 'revision': CURRENT, 'runtime': 'NOT_RUN', 'root_source_writes': 0, 'children': 0, 'prior_ebsr_artifacts': 'READ_ONLY_UNCHANGED', 'next': 'Seal bounded APPROVE and recommend specific integrated Academic compile plus two existing cases.'})
print(json.dumps({'revision': CURRENT, 'repository_sha256': sha(versions['merged']), 'teacher_query_preserved_byte_exact': True, 'author_batch_preserved_byte_exact': True, 'native_three_way_merge_exact': True, 'exact_author_paths': 96, 'prior_t001_XML_verified': len(native_xml_rows), 'runtime': 'NOT_RUN'}, indent=2))
