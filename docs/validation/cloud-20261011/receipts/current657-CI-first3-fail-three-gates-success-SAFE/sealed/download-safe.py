"""Current exact-name SAFE outcomes only; bounded archive, closed fields, no raw logs/XML."""
from pathlib import Path, PurePosixPath
import collections, hashlib, io, json, os, re, traceback, urllib.request, zipfile

OUT=Path(__file__).parent
INPUT=OUT/'.download-request.private.json'
HEAD='6576bd1f0d0181f90ebb40f6293de1a05f60625e'
LIMIT=32*1024*1024
MODULES={'api-gateway':'services/api-gateway','auth-service':'services/auth-service/auth-app',
 'academic-service':'services/academic-service/academic-app','schedule-service':'services/schedule-service/schedule-app',
 'attendance-service':'services/attendance-service/attendance-app','notification-service':'services/notification-service/notification-app'}
STATUSES={'PASSED','FAILED','ERROR','SKIPPED','NOT_EXECUTED'}
TARGETS={'academic-service':('ru.rutcampustrack.academic.integration.AcademicGrpcIT','enumDatabaseAndHttpBoundaryRemainCanonicalUnderTurkishLocale','dd0c466926911d99879fcdb59dd94641ca810bb714992d8fda7bc470b409010d'),
 'schedule-service':('ru.rutcampustrack.schedule.integration.RecurringScheduleItemIT','capNarrowingCommitIsFreshBeforeBlockedPhysicalInsert','b40de8a6bda5bdee47206c8d34a436dba8ae3012b9e5cb531b41a1e3553daaa0')}
CLASSES={'OTHER','AssertionError','CannotAcquireLockException','DeadlockLoserDataAccessException','PessimisticLockingFailureException','DataIntegrityViolationException','BadSqlGrammarException','DataAccessResourceFailureException','UncategorizedSQLException','CannotGetJdbcConnectionException','TransactionSystemException','IllegalStateException','SQLException','PSQLException'}
SQL={'NONE','OTHER','40P01','55P03','40001','23502','23503','23505','23514','P0001'}
def save(name,b):
 fd=os.open(OUT/name,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'wb') as f:f.write(b)
def integer(n):return isinstance(n,int) and not isinstance(n,bool) and 0<=n<=100000
def validate(v,artifact,task,service,kind):
 if v['collection']=='COLLECTION_ERROR':
  assert v=={'schemaVersion':1,'collection':'COLLECTION_ERROR','acceptance':'NOT_ASSESSED'}
  return {'collection':'COLLECTION_ERROR','task':task,'actualCheckout':'UNKNOWN'}
 assert set(v)=={'schemaVersion','metadata','collection','acceptance','xml','cases','summary'} and v['schemaVersion']==1
 assert v['acceptance']=='NOT_ASSESSED' and v['collection'] in {'NO_RESULTS','RESULTS','INCOMPLETE','UNINSPECTABLE_RESULTS'}
 m=v['metadata'];keys={'checkoutSha','prHeadSha','service','task','job','runId','runAttempt','event','testStepOutcome'}
 expected_job='java-coverage' if kind=='coverage' else ('java-unit-test' if task=='test' else 'java-integration-test')
 if kind=='coverage':keys.add('testStepScope');assert m['testStepScope']=='unit+integration+report'
 assert set(m)==keys and m['job']==expected_job and m['prHeadSha']==HEAD and m['runId']==str(artifact['workflow_run']['id'])
 assert m['event']=='pull_request' and m['runAttempt']=='1' and m['service']==service and m['task']==task
 assert re.fullmatch('[0-9a-f]{40}',m['checkoutSha']) and m['testStepOutcome'] in {'success','failure','cancelled','skipped','UNKNOWN'}
 assert set(v['summary'])==STATUSES and all(integer(n) for n in v['summary'].values())
 xml_counts=collections.Counter();xml_indexes=set();suite_indexes=set();declared_consistent=True
 for x in v['xml']:
  assert set(x)<={'ordinal','sha256','suites','state'} and integer(x['ordinal']) and x['ordinal']>0 and x['ordinal'] not in xml_indexes
  xml_indexes.add(x['ordinal']);assert x['sha256'] is None or re.fullmatch('[0-9a-f]{64}',x['sha256'])
  assert x['state'] in {'PARSED','MALFORMED_XML','UNINSPECTABLE_XML'}
  for s in x['suites']:
   assert set(s)=={'ordinal','declared','observed','countsConsistent'} and integer(s['ordinal'])
   assert set(s['declared'])=={'tests','failures','errors','skipped'} and all(n is None or integer(n) for n in s['declared'].values())
   assert set(s['observed'])==STATUSES and all(integer(n) for n in s['observed'].values()) and isinstance(s['countsConsistent'],bool)
   declared_consistent &= s['countsConsistent'];xml_counts.update(s['observed']);suite_indexes.add((x['ordinal'],s['ordinal']))
 case_keys={'xmlOrdinal','suiteOrdinal','ordinal','classname','method','status','identity','source','sourceSha256','failureDiagnostic'}
 for c in v['cases']:
  assert set(c)<=case_keys and c['status'] in STATUSES and c['identity'] in {'SOURCE_METHOD','ORDINAL_ONLY'}
  assert all(integer(c[k]) and c[k]>0 for k in ['xmlOrdinal','suiteOrdinal','ordinal']) and (c['xmlOrdinal'],c['suiteOrdinal']) in suite_indexes
  assert c['classname'] is None or re.fullmatch('[A-Za-z0-9_.$]+',c['classname'])
  assert c['method'] is None or re.fullmatch('[A-Za-z_$][A-Za-z0-9_$]*',c['method'])
  if 'source' in c:
   p=PurePosixPath(c['source']);assert not p.is_absolute() and '..' not in p.parts and c['source'].startswith(MODULES[service]+'/src/test/java/') and c['source'].endswith('.java')
   assert re.fullmatch('[0-9a-f]{64}',c['sourceSha256'])
  if c['identity']=='SOURCE_METHOD':assert c['classname'] and c['method'] and c.get('source')
  if 'failureDiagnostic' in c:
   d=c['failureDiagnostic'];assert service=='schedule-service' and task=='integrationTest' and c['status']=='FAILED'
   assert (c['classname'],c['method'],c['sourceSha256'])==TARGETS[service]
   if d['state']=='ABSENT_OR_UNRECOGNIZED':assert d=={'state':'ABSENT_OR_UNRECOGNIZED','stage':'UNKNOWN'}
   else:
    assert set(d)=={'state','stage','capturedClass','sqlState','barrier'} and d['state']=='CLOSED_MARKER'
    assert d['capturedClass'] in CLASSES and d['sqlState'] in SQL and d['barrier'] in {'NOT_OBSERVED','TIMED_OUT','INTERRUPTED'}
    assert d['stage']==('UNKNOWN' if d['barrier']=='NOT_OBSERVED' else 'AWAIT_BARRIER')
 counts=collections.Counter(c['status'] for c in v['cases']);assert all(counts[s]==v['summary'][s]==xml_counts[s] for s in STATUSES)
 target=[]
 if task=='integrationTest' and service in TARGETS:
  target=[c for c in v['cases'] if (c['classname'],c['method'])==TARGETS[service][:2]]
  if v['collection']=='RESULTS':assert len(target)==1 and target[0]['sourceSha256']==TARGETS[service][2]
  if target:assert len(target)==1 and target[0]['sourceSha256']==TARGETS[service][2]
 return {'metadata':m,'collection':v['collection'],'counts':v['summary'],'xml_case_summary_consistent':True,'declared_consistent':declared_consistent,
  'target':target[0] if target else None,'failed_or_error_cases':[c for c in v['cases'] if c['status'] in {'FAILED','ERROR'}],
  'source_acceptance':'NOT_PROMOTED','historical96_CAP_cause':'UNKNOWN'}
try:
 a=json.loads(INPUT.read_text());match=re.fullmatch(r'safe-(unit|integration|coverage)-outcomes-(api-gateway|auth-service|academic-service|schedule-service|attendance-service|notification-service)-1',a['name'])
 assert match and a['workflow_run']['head_sha']==HEAD and a['workflow_run']['id'] in {38329143416,38329143628}
 kind,service=match.groups();assert (kind=='coverage')==(a['workflow_run']['id']==38329143628)
 assert 0<a['size_in_bytes']<=LIMIT and not a['expired']
 with urllib.request.urlopen(a['private_url'],timeout=30) as r:data=r.read(LIMIT+1)
 assert len(data)==a['size_in_bytes']<=LIMIT and a['digest']=='sha256:'+hashlib.sha256(data).hexdigest()
 results=[];bodies=[]
 with zipfile.ZipFile(io.BytesIO(data)) as z:
  infos=z.infolist();assert len(infos)==(2 if kind=='coverage' else 1) and len(set(i.filename for i in infos))==len(infos)
  assert sum(i.file_size for i in infos)<=LIMIT and not any(i.flag_bits&1 for i in infos)
  for i in infos:
   task=('test' if kind=='unit' else 'integrationTest') if kind!='coverage' else i.filename.split('/')[0]
   assert task in {'test','integrationTest'} and i.filename==('safe-outcomes.json' if kind!='coverage' else task+'/safe-outcomes.json')
   b=z.read(i);v=json.loads(b);results.append(validate(v,a,task,service,kind));bodies.append((task,b))
  assert z.testzip() is None
 for task,b in bodies:save(a['name']+'-'+task+'.json',b)
 save(a['name']+'.zip',data)
 projection={'artifact_id':a['id'],'name':a['name'],'archive_bytes':len(data),'archive_sha256':hashlib.sha256(data).hexdigest(),'producer':HEAD,'results':results,'raw_XML_logs_read':False}
 save(a['name']+'.safe.json',(json.dumps(projection,indent=2)+'\n').encode())
 print(json.dumps({'SAFE_VERIFIED':True,'artifact':a['name'],'id':a['id'],'results':results}))
except Exception as error:
 print(json.dumps({'SAFE_COLLECTION_BLOCKED':True,'error_type':type(error).__name__,'http_status':getattr(error,'code',None),'own_line':next((f.lineno for f in reversed(traceback.extract_tb(error.__traceback__)) if f.filename==__file__),None)}));raise SystemExit(1)
finally:INPUT.unlink(missing_ok=True)
