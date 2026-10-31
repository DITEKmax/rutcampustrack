set -euo pipefail
RCT_PRODUCER_HEAD_SHA="$(python3 scripts/local_release_context.py revision)"
export RCT_PRODUCER_HEAD_SHA
export RCT_PROBE_SERVICE="${RCT_CI_SERVICE:-${RCT_LOCAL_RELEASE_SERVICE:-}}"
umask 077
git -c core.fileMode=false diff --exit-code -- services proto event-schemas
python3 - <<'PY_CACHE_COMMIT'
import hashlib, json, os, pathlib, re, shutil, subprocess, tempfile
import xml.etree.ElementTree as ET
app = pathlib.Path('services/academic-service/academic-app')
group = app / 'src/main/java/ru/rutcampustrack/academic/group/GroupService.java'
listener = app / 'src/main/java/ru/rutcampustrack/academic/group/GroupCacheInvalidationListener.java'
test = app / 'src/test/java/ru/rutcampustrack/academic/group/GroupCacheCommitIT.java'
fixed_sha = {group: '72da10b12c096cb86981f4504e5215a83f4fe36dc50677f356b690d91c68d122',
             listener: '793de84ed45cb798c13ff72a4e49ada7f6d3a9000bbcc2495a315bf89feb1ef4'}
package = 'ru.rutcampustrack.academic.group.GroupCacheCommitIT'
commit = 'concurrentReadBeforeOuterCommitCannotLeaveAnOldCachedGroupAfterCommit'
rollback = 'outerRollbackRetainsValidCachedGroupAndRoster'
private = pathlib.Path(tempfile.mkdtemp(prefix='rct-cache-commit-', dir=os.environ['RUNNER_TEMP']))
output = pathlib.Path('build/ci-test-outcomes/academic-service/cache-commit-safe.json')
output.parent.mkdir(parents=True, exist_ok=True)
proof = {'schema': 1, 'source_sha': subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
         'counterexample': 'ORIGINAL_IMMEDIATE_CACHE_EVICTION_WITHOUT_AFTER_COMMIT_LISTENER',
         'state': 'SOURCE_ADMISSION', 'source_restored_exact': False}
original = {}
primary = None
def suite():
    path = app / 'build/test-results/integrationTest' / ('TEST-' + package + '.xml')
    assert path.is_file() and not path.is_symlink() and 0 < path.stat().st_size <= 2*1024*1024
    data = path.read_bytes()
    assert b'<!DOCTYPE' not in data.upper() and b'<!ENTITY' not in data.upper()
    result = ET.fromstring(data)
    assert result.tag == 'testsuite' and result.get('name') == package
    return result
def clear_results():
    for relative in ['build/test-results/integrationTest','build/reports/integrationTest']:
        directory = app / relative
        if directory.exists():
            if os.environ.get('RCT_LOCAL_RELEASE_REVISION'):
                retained=private / ('native-'+str(len(list(private.glob('native-*')))))
                shutil.move(directory,retained)
            else:shutil.rmtree(directory)
def run_case(selection, filename):
    clear_results()
    with (private/filename).open('wb') as log:
        return subprocess.run(['./gradlew',':services:academic-service:academic-app:integrationTest',
            '--tests',selection,'--rerun-tasks','--max-workers=1','--no-daemon'],
            stdout=log,stderr=subprocess.STDOUT,timeout=360).returncode
try:
    for path, expected in fixed_sha.items():
        data = path.read_bytes()
        assert hashlib.sha256(data).hexdigest() == expected
        assert data == subprocess.check_output(['git','show','HEAD:'+path.as_posix()])
        original[path] = data
    assert hashlib.sha256(test.read_bytes()).hexdigest() == 'eebae375221a9e45c7b77e38ddd4e6b5b24cc45e275f9542bc31dc547478a999'
    assert test.read_bytes() == subprocess.check_output(['git','show','HEAD:'+test.as_posix()])
    assert test.read_text().splitlines()[85].strip() == '.isEqualTo("УИТ-311");'
    group_anchor = (b'    public Group updateGroup(Long id, UpdateGroupRequest request) {\n'
                    b'        return availability().updateGroup(id, request);\n'
                    b'    }\n')
    group_changed = (b'    @org.springframework.cache.annotation.Caching(\n'
                     b'            evict = {\n'
                     b'                @org.springframework.cache.annotation.CacheEvict(value = "groups", key = "#id"),\n'
                     b'                @org.springframework.cache.annotation.CacheEvict(value = "group_members", key = "#id")\n'
                     b'            })\n' + group_anchor)
    assert original[group].count(group_anchor) == 1
    old_group = original[group].replace(group_anchor, group_changed, 1)
    assert hashlib.sha256(old_group).hexdigest() == 'cf37b54c4a05e5073b5f4b6db2e61c26ca6b9212eddeeba0cfac324995667a6d'
    assert old_group.replace(group_changed, group_anchor, 1) == original[group]
    anchor = (b'        Long groupId = ((GroupUpdatedEvent.Payload) event.getPayload()).groupId();\n'
              b'        evict(caches.getCache("groups"), groupId);\n'
              b'        evict(caches.getCache("group_members"), groupId);\n')
    assert original[listener].count(anchor) == 1
    old_listener = original[listener].replace(anchor,
        b'        // Controlled pre-correction semantics: no post-commit cache invalidation.\n')
    assert hashlib.sha256(old_listener).hexdigest() == '4132368a730525b79ac443f0dbe20d56639393106bc85d1e2b041fbc29cc4a95'
    proof['state'] = 'RED_EXECUTION'
    group.write_bytes(old_group); listener.write_bytes(old_listener)
    assert run_case(package+'.'+commit, 'red.private.log') == 1
    red = suite()
    assert {k:red.get(k) for k in ['tests','failures','errors','skipped']} == {'tests':'1','failures':'1','errors':'0','skipped':'0'}
    cases = red.findall('testcase')
    assert len(cases) == 1 and cases[0].get('classname') == package and cases[0].get('name') == commit+'()'
    assert not cases[0].findall('error') and not cases[0].findall('skipped')
    failures = cases[0].findall('failure'); assert len(failures) == 1
    failure = failures[0]
    assert failure.get('type') in {'org.opentest4j.AssertionFailedError','java.lang.AssertionError'}
    message = failure.get('message','').removeprefix(failure.get('type')+': ')
    assert re.fullmatch(r'\s*\[group read after outer commit returns the committed name\]\s*expected:\s*"УИТ-311"\s+but was:\s*"ИВТ-211"\s*', message)
    frame = package+'.'+commit+'(GroupCacheCommitIT.java:86)'
    assert len(re.findall(r'(?m)^[ \t]*at[ \t]+(?:[^ \t\r\n()]{1,256}/)?'+re.escape(frame)+r'[ \t]*$',failure.text or '')) == 1
    proof['red'] = {'state':'EXPECTED_COMMITTED_DB_NEW_CACHED_OLD_ASSERTION','tests':1,'failures':1,'errors':0,'skipped':0}
except BaseException as error:
    primary = error
finally:
    for path, data in original.items(): path.write_bytes(data)
    proof['source_restored_exact'] = len(original) == 2 and all(hashlib.sha256(p.read_bytes()).hexdigest() == h for p,h in fixed_sha.items())
try:
    assert proof['source_restored_exact']
    if os.environ.get('RCT_LOCAL_RELEASE_REVISION') and isinstance(primary, KeyboardInterrupt):raise primary
    green_code = run_case(package, 'green.private.log')
    green = suite()
    assert green_code == 0 and {k:green.get(k) for k in ['tests','failures','errors','skipped']} == {'tests':'2','failures':'0','errors':'0','skipped':'0'}
    assert {c.get('name') for c in green.findall('testcase')} == {commit+'()',rollback+'()'}
    proof['green'] = {'state':'COMMIT_AND_ROLLBACK_PASS','tests':2,'failures':0,'errors':0,'skipped':0}
except BaseException as error:
    if primary is None: primary = error
finally:
    clear_results()
    proof['state'] = 'PASS' if primary is None else 'CONTROL_OR_GREEN_FAILURE'
    output.write_text(json.dumps(proof,indent=2)+'\n')
    if not os.environ.get('RCT_LOCAL_RELEASE_REVISION'):shutil.rmtree(private)
if primary is not None: raise primary
PY_CACHE_COMMIT
