# Portable local adaptation; NOT_RUN until owner output.
"""Future admitted read-only supplement. No calendar/security cleanup or business writes."""
import argparse, json, pathlib, sys, time, uuid

ROOT = pathlib.Path(__file__).resolve().parent
V8 = ROOT
sys.path.insert(0, str(V8))


def main():
    p = argparse.ArgumentParser()
    p.add_argument('phase', choices=['quiesce', 'before', 'after', 'closed'])
    p.add_argument('--go', required=True)
    a = p.parse_args()
    import runtime
    runtime.admission('native-case', a.go, continuation=True)
    if a.phase == 'quiesce':
        stable = 0
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            pending = 0
            for db, table in [('academic_db', 'academic_outbox'), ('schedule_db', 'schedule_outbox')]:
                pending += int(runtime.pg('BEGIN READ ONLY; SELECT count(*) FROM ' + table + " WHERE status <> 'sent'; ROLLBACK;", db).stdout.strip())
            raw = runtime.docker('exec', runtime.PROJECT + '-rabbitmq-1', 'rabbitmqctl',
                                 '-p', 'rct-pr3-local', 'list_queues', '-q', 'messages_ready', 'messages_unacknowledged').stdout
            rows = [line.split() for line in raw.splitlines() if line.strip()]
            numeric = [r for r in rows if len(r) == 2 and all(v.isdigit() for v in r)]
            if not numeric:
                raise RuntimeError('Queue accounting unavailable; no deletion admission')
            stable = stable + 1 if pending == 0 and sum(int(v) for r in numeric for v in r) == 0 else 0
            if stable >= 3:
                return
            time.sleep(.25)
        raise RuntimeError('Own realm queues not quiescent; no deletion admission')
    actors = json.loads((ROOT / 'private/actors.json').read_text())
    fixture = json.loads((V8 / 'private/fixture-state.json').read_text())
    assert len(actors) == 7 and len({x['sid'] for x in actors}) == 7
    for x in actors:
        assert str(uuid.UUID(x['sid'])) == x['sid'] and str(int(x['userId'])) == x['userId']
    observed = []
    for actor in actors:
        assert actor['sid'] in actor['observedSids']
        allowed_successor = actor['client'] == 'TMA' and actor['role'] == 'HEADMAN'
        assert len(actor['observedSids']) <= (2 if allowed_successor else 1)
        for sid in actor['observedSids']:
            assert str(uuid.UUID(sid)) == sid
            assert sid in actor['qualifiedSids'], 'Every observed own SID needs canonical qualification before proof/cleanup'
            if sid != actor['sid']:
                assert allowed_successor and sid == actor['successorSid'] and sid == actor['currentSid']
            observed.append(sid)
    assert len(set(observed)) == len(observed) and 7 <= len(observed) <= 8
    sids = ','.join("'" + sid + "'" for sid in observed)
    assert str(uuid.UUID(fixture['headmanSessionId'])) == fixture['headmanSessionId']
    # Existing v8 postproof separately proves the original fixture Headman session's exact withdrawal.
    preserved_sids = sids + ",'" + fixture['headmanSessionId'] + "'"
    auth = json.loads(runtime.pg("""BEGIN READ ONLY; SELECT json_build_object(
      'sessions',(SELECT jsonb_agg(jsonb_build_object('sid',s.sid,'userId',s.user_id::text,
        'version',s.session_version,'selected',s.active_role_grant_id IS NOT NULL,
        'revoked',s.revoked_at IS NOT NULL) ORDER BY s.sid) FROM auth_sessions s WHERE s.sid IN (""" + sids + """)),
      'foreignSessionCore',(SELECT md5(coalesce(jsonb_agg(jsonb_build_object('sid',sid,
        'user',user_id,'version',session_version,'grant',active_role_grant_id,'revoked',revoked_at)
        ORDER BY sid)::text,'[]')) FROM auth_sessions WHERE sid NOT IN (""" + preserved_sids + "))); ROLLBACK;", 'auth_db').stdout)
    group_id = int(fixture['groupId'])
    assert group_id > 1
    academic = json.loads(runtime.pg("""BEGIN READ ONLY; SELECT json_build_object(
      'semesters',(SELECT md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY id)::text,'[]')) FROM semesters t),
      'operation',(SELECT json_build_object('id',operation_id,'phase',phase,'groupId',group_id,
        'actorId',actor_id,'sessionId',session_id) FROM group_deletion_operations WHERE group_id=""" + str(group_id) + " ORDER BY created_at DESC LIMIT 1)); ROLLBACK;", 'academic_db').stdout)
    assert len(auth['sessions']) == len(observed)
    by_sid = {x['sid']: x for x in auth['sessions']}
    for actor in actors:
        for sid in actor['observedSids']:
            assert by_sid[sid]['userId'] == actor['userId']
    value = {'auth': auth, 'academic': academic}
    before_path = ROOT / 'private/cohort-before.json'
    if a.phase == 'before':
        assert len(observed) == 7
        assert all(x['selected'] and not x['revoked'] for x in auth['sessions'])
        runtime.private_write(before_path, value)
        return
    before = json.loads(before_path.read_text())
    old = {x['sid']: x for x in before['auth']['sessions']}
    assert auth['foreignSessionCore'] == before['auth']['foreignSessionCore']
    assert academic['semesters'] == before['academic']['semesters'], 'Calendar metadata must remain unchanged until v8 restoration'
    for actor in actors:
        row, original = by_sid[actor['sid']], old[actor['sid']]
        if a.phase == 'closed':
            assert row['revoked'], 'Exact owned initial SID not closed; no blanket cleanup'
        elif actor['role'] == 'HEADMAN':
            assert not row['selected'] and not row['revoked'] and row['version'] == original['version'] + 1
        else:
            assert row == original, 'Retained Teacher/Admin session changed'
        for sid in actor['observedSids']:
            if sid == actor['sid']:
                continue
            successor = by_sid[sid]
            if a.phase == 'closed':
                assert successor['revoked'], 'Observed TMA successor SID remains live'
            else:
                assert not successor['revoked'] and successor['version'] == 1
    value['allPassed'] = True
    runtime.private_write(ROOT / ('private/cohort-' + a.phase + '.json'), value)


if __name__ == '__main__':
    try:
        main()
    except Exception:
        print('Read-only cohort proof stopped; preserve exact handles, no retry/reset.', file=sys.stderr)
        raise SystemExit(1)
