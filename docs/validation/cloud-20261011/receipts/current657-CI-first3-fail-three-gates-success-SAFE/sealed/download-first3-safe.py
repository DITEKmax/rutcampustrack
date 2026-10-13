"""Read only the four existing closed FIRST3 JSON outcomes, never logs or attachments."""
from pathlib import Path
import hashlib, io, json, os, re, subprocess, traceback, urllib.request, zipfile
OUT=Path(__file__).parent
REQ=OUT/'.download-request.private.json'
HEAD='6576bd1f0d0181f90ebb40f6293de1a05f60625e'
ROOT=Path('/workspace/rutcampustrack')
LIMIT=32*1024*1024
CODES={14:'STUDENT',61:'HEADMAN',142:'HELPER'}
STATES={'passed','failed','timedOut','skipped','interrupted'}
def integer(x,cap=1000000): return isinstance(x,int) and not isinstance(x,bool) and 0<=x<=cap
def source(p):return subprocess.check_output(['git','-C',str(ROOT),'show',HEAD+':'+p],timeout=10).decode()
def save(name,data):
    fd=os.open(OUT/name,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'wb') as f:f.write(data)
try:
    a=json.loads(REQ.read_text())
    assert a['name']=='first3-safe-1' and a['workflow_run']['id']==38329143416 and a['workflow_run']['head_sha']==HEAD and not a['expired']
    assert 0<a['size_in_bytes']<=LIMIT
    obs=source('tests/e2e/fixtures/first3-observer.ts')
    assert hashlib.sha256(obs.encode()).hexdigest()=='0a8f9d5d437223a8d71c8b917a39e6ca5548ba209775539caaee35a20548d718'
    quoted=lambda s:set(re.findall(r"'([^']*)'",s))
    bools=quoted(re.search(r'const boolKeys = new Set\(\[(.*?)\]\);',obs,re.S)[1])
    counts=quoted(re.search(r'const countKeys = new Set\(\[(.*?)\]\);',obs,re.S)[1])
    block=re.search(r'const enumValues:.*?= \{(.*?)\n\};',obs,re.S)[1]
    enums={k:quoted(v) for k,v in re.findall(r'(\w+): \[(.*?)\]',block,re.S)}
    enums['httpStatus']={str(x) for x in [200,201,202,204,301,302,303,307,308,400,401,403,404,409,412,422,429,500,502,503,504]}|{'OTHER'}
    endpoints={'LOGIN','LOGOUT','SESSION','ROLE_CHANGE','HOMEWORK_DEFAULT','HOMEWORK_EXPLICIT','LESSON_LIST','LESSON_ROSTER','HELPER_PERMISSIONS','MARK_WRITE','COMPLETION_WRITE'}
    phases={'BEGIN','UI_RESPONSE','FAILURE_READ','DOM','END'}
    files={Path(p).name for p in subprocess.check_output(['git','-C',str(ROOT),'ls-tree','-r','--name-only',HEAD,'--','tests/e2e'],timeout=10).decode().splitlines()}
    with urllib.request.urlopen(a['private_url'],timeout=30) as r:data=r.read(LIMIT+1)
    assert len(data)==a['size_in_bytes']<=LIMIT and a['digest']=='sha256:'+hashlib.sha256(data).hexdigest()
    expected={'artifacts/safe-results.json'}|{'supervision/observations-'+x.lower()+'.safe.json' for x in CODES.values()}
    bodies=[];result=None;observations={}
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        infos=z.infolist();assert 1<=len(infos)<=4 and len({i.filename for i in infos})==len(infos)
        assert {i.filename for i in infos}<=expected and sum(i.file_size for i in infos)<=LIMIT and not any(i.flag_bits&1 for i in infos)
        for i in infos:
            b=z.read(i);v=json.loads(b)
            if i.filename=='artifacts/safe-results.json':
                assert set(v)=={'status','runnerErrors','unsafeArtifact','suppressedStdoutBytes','suppressedStderrBytes','results'}
                assert v['status'] in {'passed','failed','timedout','interrupted'} and isinstance(v['unsafeArtifact'],bool)
                assert all(integer(v[k],2**53-1) for k in ['runnerErrors','suppressedStdoutBytes','suppressedStderrBytes'])
                assert len(v['results'])<=3 and len({r['line'] for r in v['results']})==len(v['results'])
                for row in v['results']:
                    assert set(row)=={'file','line','project','status','expectedStatus','durationMs','errorCount','errorLocations'}
                    assert row['file']=='mobile-real-server.spec.ts' and row['line'] in CODES and row['project']=='chromium'
                    assert row['status'] in STATES and row['expectedStatus']=='passed' and integer(row['errorCount'])
                    assert isinstance(row['durationMs'],(int,float)) and not isinstance(row['durationMs'],bool) and 0<=row['durationMs']<=1800000
                    for e in row['errorLocations']:assert set(e)=={'file','line'} and e['file'] in files and integer(e['line']) and e['line']>0
                result=v
            else:
                code=next(x for x in CODES.values() if i.filename.endswith('-'+x.lower()+'.safe.json'))
                assert set(v)=={'schema','caseCode','events','droppedEventCount'} and v['schema']=='RCT_FOCUS3_CLOSED_V1' and v['caseCode']==code
                assert integer(v['droppedEventCount']) and len(v['events'])<=97
                for e in v['events']:
                    assert set(e)<={'caseCode','phase','endpoint','facts'} and {'caseCode','phase','facts'}<=set(e)
                    assert e['caseCode']==code and e['phase'] in phases and ('endpoint' not in e or e['endpoint'] in endpoints)
                    for k,x in e['facts'].items():
                        assert (k in bools and isinstance(x,bool)) or (k in counts and integer(x)) or (k in enums and isinstance(x,str) and x in enums[k])
                observations[code]=v
            bodies.append((i.filename,b))
        assert z.testzip() is None
    for name,b in bodies:save('first3-safe-1-'+name.replace('/','-'),b)
    projection={'artifact_id':a['id'],'producer':HEAD,'run_id':38329143416,'event':'pull_request','actual_checkout':'NOT_PROVED_BY_BROWSER_SAFE_SCHEMA','archive_sha256':hashlib.sha256(data).hexdigest(),'archive_bytes':len(data),'member_names':[n for n,b in bodies],'results':result,'observations':observations,'missing_members':sorted(expected-{n for n,b in bodies}),'source_assertions':'UNCHANGED_ACCEPTED_FIRST3','raw_logs_XML_HTML_error_context_read':False,'buffer_limit':'POST_BUFFER_CLASSIFIER_NOT_HARD_RSS_BOUND','acceptance':'FINITE_CASES_ONLY_NO_PARENT_PROMOTION'}
    save('first3-safe-1.zip',data);save('first3-safe-1.safe.json',(json.dumps(projection,indent=2)+'\n').encode())
    print(json.dumps({'SAFE_VERIFIED':True,'artifact_id':a['id'],'results':result,'closed_503_events':{k:[e for e in v['events'] if e['facts'].get('httpStatus')=='503'] for k,v in observations.items()},'end':{k:[e for e in v['events'] if e['phase']=='END'] for k,v in observations.items()},'missing':projection['missing_members']}))
except Exception as error:
    print(json.dumps({'SAFE_COLLECTION_BLOCKED':True,'error_type':type(error).__name__,'http_status':getattr(error,'code',None),'own_line':next((f.lineno for f in reversed(traceback.extract_tb(error.__traceback__)) if f.filename==__file__),None)}));raise SystemExit(1)
finally:REQ.unlink(missing_ok=True)
