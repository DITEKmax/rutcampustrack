"""Local-only admission/proof support. Imported only from generated owner-local state."""
import hashlib,json,os,pathlib,re,shutil,subprocess,time

ROOT=pathlib.Path(__file__).resolve().parent
S=json.loads((ROOT/'settings.json').read_text())
REPO=pathlib.Path(S['repo']); PRIVATE=ROOT/'private'; PROJECT='rct-pr3-local'
P=json.loads((ROOT/'pins.json').read_text())
ENV={k:v for k,v in os.environ.items() if k in {'PATH','HOME','USER','LOGNAME','LANG','LC_ALL','TERM','TMPDIR','JAVA_HOME'}}
DOCKER=['docker','--host=unix:///var/run/docker.sock']

def sha(p):return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()
def private_write(p,value):
 p=pathlib.Path(p);p.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
 fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
 with os.fdopen(fd,'w') as f:
  f.write(value if isinstance(value,str) else json.dumps(value,indent=2)+'\n');f.flush();os.fsync(f.fileno())
 d=os.open(p.parent,os.O_RDONLY)
 try:os.fsync(d)
 finally:os.close(d)
def call(args,input=None,expected=0,timeout=60):
 r=subprocess.run(args,input=input,text=True,env=ENV,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
 if expected is not None and r.returncode!=expected:raise RuntimeError('Local scoped command failed; private output withheld; preserve state')
 return r
def docker(*args,input=None,expected=0):return call(DOCKER+list(args),input,expected)
def compose(*args):return DOCKER+['compose','--project-name',PROJECT,'--env-file',str(PRIVATE/'local.env'),'-f',str(ROOT/'compose.json'),*args]
def pg(sql,db='postgres',user='rct_user',password=None,expected=0):
 # Only the exact owned PostgreSQL container. Secrets are input, never command output.
 ownership();psql=['-X','-A','-t','-q','-U',user,'-d',db,'-v','ON_ERROR_STOP=1']
 if password is None:return docker('exec','-i',PROJECT+'-postgres-academic-1','psql',*psql,input=sql,expected=expected)
 assert re.fullmatch(r'[A-Za-z0-9_-]{43}',password)
 # The DB password is one stdin line, never a Docker CLI argument/environment dump.
 return docker('exec','-i',PROJECT+'-postgres-academic-1','sh','-c','IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql "$@"','rct-pr3-local-psql',*psql,'-h','127.0.0.1',input=password+'\n'+sql,expected=expected)
def snapshot():
 ids=docker('ps','-aq').stdout.splitlines()
 if not ids:return []
 fmt='{"Id":{{json .Id}},"Name":{{json .Name}},"Image":{{json .Image}},"Mounts":{{json .Mounts}},"Running":{{json .State.Running}},"Project":{{json (index .Config.Labels "com.docker.compose.project")}},"Service":{{json (index .Config.Labels "com.docker.compose.service")}},"Owner":{{json (index .Config.Labels "rct.pr3.local.owner")}}}'
 rows=[json.loads(x) for x in docker('inspect','--format',fmt,*ids).stdout.splitlines()]
 for row in rows:row['Mounts']=sorted(row['Mounts'],key=lambda x:json.dumps(x,sort_keys=True))
 return rows
def ownership():
 baseline=json.loads((PRIVATE/'foreign-baseline.json').read_text());now=snapshot();byid={r['Id']:r for r in now}
 for old in baseline:
  assert byid.get(old['Id'])==old,'Foreign container ID/image/mount/state changed; HOLD'
 for row in now:
  if row['Project']==PROJECT:
   assert row['Owner']==S['ownerNonce'] and row['Service'] in S['services'],'Unknown namespace owner; never stop or adopt it'
   assert row['Name']=='/'+PROJECT+'-'+row['Service']+'-1','Owned service name changed'
   pin=S['servicePins'][row['Service']]
   assert row['Image']==S['images'][pin['image']],'Owned container image differs from frozen image ID'
   mounts=[]
   for m in row['Mounts']:
    assert m['Type'] in ['bind','volume'],'Unqualified mount type'
    key='Source' if m['Type']=='bind' else 'Name'
    mounts.append({'Type':m['Type'],key:m[key],'Destination':m['Destination'],'RW':m['RW']})
   assert sorted(mounts,key=lambda m:json.dumps(m,sort_keys=True))==pin['mounts'],'Owned mount set differs from exact frozen state/JAR/volume pins'
 for kind in ['volume','network']:
  ids=docker(kind,'ls','-q','--filter','name='+PROJECT).stdout.splitlines()
  if ids:
   for item in json.loads(docker(kind,'inspect',*ids).stdout):
    assert item.get('Labels',{}).get('rct.pr3.local.owner')==S['ownerNonce'],'Unknown local network/volume owner; never adopt or remove it'
 return now
def package_hash():
 manifest=ROOT/'SOURCE.SHA256SUMS'
 for line in manifest.read_text().splitlines():
  digest,name=line.split('  ',1);assert not pathlib.Path(name).is_absolute() and '..' not in pathlib.Path(name).parts
  assert sha(ROOT/name)==digest,'Local frozen driver/config changed'
 value=sha(manifest);assert value==(ROOT/'PACKAGE-SHA256').read_text().strip();return value
def inputs():
 file=PRIVATE/'input-bindings.json';assert not file.is_symlink() and sha(file)==(ROOT/'PRIVATE-INPUTS-SHA256').read_text().strip(),'Private input bindings changed'
 bindings=json.loads(file.read_text());assert bindings['sourceSha']==S['sourceSha'] and bindings['ownerNonce']==S['ownerNonce']
 for name,digest in bindings['files'].items():
  assert not pathlib.Path(name).is_absolute() and '..' not in pathlib.Path(name).parts
  p=PRIVATE/name;assert p.is_file() and not p.is_symlink() and sha(p)==digest,'Prepared private input changed; no value emitted'
def source():
 package_hash();inputs()
 assert call(['git','-C',str(REPO),'rev-parse','HEAD']).stdout.strip()==S['sourceSha'],'Pinned source changed'
 assert not call(['git','-C',str(REPO),'diff','--name-only','HEAD']).stdout.strip(),'Tracked source edits; freeze a clean published checkout'
 for line in (ROOT/'source-inputs.SHA256SUMS').read_text().splitlines():
  digest,name=line.split('  ',1);assert sha(REPO/name)==digest,'Frozen local source input changed'
 for a in P['artifacts']:assert sha(a['path'])==a['sha256'],'Built local JAR changed'
 for tag,image_id in S['images'].items():assert json.loads(docker('image','inspect','--format','{{json .Id}}',tag).stdout)==image_id,'Local image pin changed; no pull or replacement'
def minimum_memory_total():
 # Old prepared packages without either policy retain the original strict floor.
 if 'memoryAdmission' not in S and 'memoryAdmission' not in P:return 12884901888
 choices=[{'name':'default-12gib','explicit12gbWsl':False,'minimumTotalBytes':12884901888},{'name':'explicit-12gb-wsl','explicit12gbWsl':True,'minimumTotalBytes':12000000000}]
 for policy in [S.get('memoryAdmission'),P.get('memoryAdmission')]:
  assert type(policy) is dict and type(policy.get('explicit12gbWsl')) is bool and type(policy.get('minimumTotalBytes')) is int and policy in choices,'Unknown frozen memory admission policy; HOLD'
 assert S['memoryAdmission']==P['memoryAdmission'],'Mismatched frozen memory admission policy; HOLD'
 return S['memoryAdmission']['minimumTotalBytes']
def resources():
 info={k:int(v.split()[0])*1024 for k,v in (line.split(':',1) for line in pathlib.Path('/proc/meminfo').read_text().splitlines())}
 maximum=info['MemTotal'];available=info['MemAvailable'];cg=pathlib.Path('/sys/fs/cgroup')
 if (cg/'memory.max').exists() and (cg/'memory.max').read_text().strip()!='max':
  bound=int((cg/'memory.max').read_text());current=int((cg/'memory.current').read_text());maximum=min(maximum,bound);available=min(available,bound-current)
 return {'minimumMemoryMaximumBytes':minimum_memory_total(),'memoryMaximum':maximum,'memoryAvailable':available,'memoryCurrent':maximum-available,'diskAvailable':shutil.disk_usage(ROOT).free,'cpuCount':os.cpu_count()}
def ready():
 rows=[r for r in ownership() if r['Project']==PROJECT]
 assert len(rows)==len(S['services']) and {r['Service'] for r in rows}==set(S['services']) and all(r['Running'] for r in rows),'All exact owned services required before API'
 receipt=json.loads((PRIVATE/'services-ready.json').read_text());proof=json.loads((PRIVATE/'isolation-proof.json').read_text())
 assert receipt['sourceSha']==S['sourceSha'] and receipt['ownerNonce']==S['ownerNonce'] and set(receipt['ids'])=={r['Id'] for r in rows},'Ready IDs/source/nonce changed'
 assert proof['allPassed'] and proof['sourceSha']==S['sourceSha'] and proof['ownerNonce']==S['ownerNonce']
 states=[json.loads(x) for x in docker('inspect','--format','{{json .State.Health.Status}}',*[r['Id'] for r in rows]).stdout.splitlines()]
 assert len(states)==len(rows) and all(x=='healthy' for x in states),'Owned services are not currently healthy'
def wait_healthy():
 # The parent guarded process samples resources every250ms while this detached
 # health poll waits on Docker. A timeout/interrupt stops only its child group.
 deadline=time.monotonic()+350
 while time.monotonic()<deadline:
  own=[r for r in ownership() if r['Project']==PROJECT]
  if len(own)==len(S['services']):
   states=[json.loads(x) for x in docker('inspect','--format','{{json .State}}',*[r['Id'] for r in own]).stdout.splitlines()]
   assert len(states)==len(own),'Own health metadata unavailable'
   assert not any(x['Status'] in ['exited','dead'] for x in states),'Own service exited; no automatic restart'
   if all(x.get('Health',{}).get('Status')=='healthy' for x in states):return
  time.sleep(.25)
 raise RuntimeError('Bounded readiness expired; no retry')
def pending():
 assert pg("BEGIN READ ONLY; SELECT count(*) FROM group_deletion_operations WHERE phase NOT IN ('COMPLETED','CANCELLED'); ROLLBACK;",'academic_db').stdout.strip()=='0','Nonterminal original operation; inspect status only'
def isolation():
 """Same finite rolled-back own CRUD/cross-DB denial controls as accepted180f."""
 source();ownership();assert resources()['diskAvailable']>=2684354560
 password=(PRIVATE/'POSTGRES_SCHEDULE_PASSWORD').read_text();user='rct_group_schedule_runtime'
 own=pg("BEGIN; INSERT INTO shedlock(name,lock_until,locked_at,locked_by) VALUES('pr3-local-probe',now(),now(),'own'); UPDATE shedlock SET locked_by='own2' WHERE name='pr3-local-probe'; SELECT count(*) FROM shedlock WHERE name='pr3-local-probe'; DELETE FROM shedlock WHERE name='pr3-local-probe'; ROLLBACK;",'schedule_db',user,password).stdout
 assert own.strip()=='1';checks={'ownScheduleCrudRolledBack':True}
 for db in ['auth_db','academic_db']:
  result=pg('SELECT 1;',db,user,password,expected=None)
  assert result.returncode!=0 and 'permission denied for database' in result.stderr;checks[db+'ConnectDenied']=True
 for name,sql in [('ddl',"BEGIN; CREATE TABLE public.pr3_local_forbidden_probe(id integer); ROLLBACK;"),('truncate',"BEGIN; TRUNCATE group_deletion_participants; ROLLBACK;")]:
  result=pg(sql,'schedule_db',user,password,expected=None);assert result.returncode!=0 and 'permission denied' in result.stderr;checks[name+'Denied']=True
 role=json.loads(pg("BEGIN READ ONLY; SELECT json_build_object('noMembership',NOT EXISTS(SELECT 1 FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname='rct_group_schedule_runtime')),'noCreate',NOT has_schema_privilege('rct_group_schedule_runtime','public','CREATE'),'noTruncate',NOT has_table_privilege('rct_group_schedule_runtime','group_deletion_participants','TRUNCATE')); ROLLBACK;",'schedule_db').stdout);assert all(role.values());checks.update(role)
 for own,other,user,file in [('attendance_db','notification_db','rct_group_attendance','SPRING_DATA_MONGODB_URI'),('notification_db','attendance_db','rct_group_notification','NOTIFICATION_SPRING_DATA_MONGODB_URI')]:
  password=re.search(r':([^:@]+)@',(PRIVATE/file).read_text()).group(1)
  script="const d=db.getSiblingDB("+json.dumps(own)+");if(!d.auth("+json.dumps(user)+","+json.dumps(password)+"))throw Error('Own principal denied');let denied=false;try{d.getSiblingDB("+json.dumps(other)+").getCollectionNames()}catch(e){denied=e.code===13;}if(!denied)throw Error('Cross database accessible');const s=d.getMongo().startSession();const c=s.getDatabase("+json.dumps(own)+").event_consumer_processed;s.startTransaction();try{c.insertOne({_id:'pr3-local-isolation-probe'});if(c.countDocuments({_id:'pr3-local-isolation-probe'})!==1)throw Error('Own read denied');c.updateOne({_id:'pr3-local-isolation-probe'},{$set:{owned:true}});c.deleteOne({_id:'pr3-local-isolation-probe'});}finally{s.abortTransaction();s.endSession();}print(JSON.stringify({crossDatabaseDenied:true,ownCrudAborted:true}));"
  proof=json.loads(docker('exec','-i',PROJECT+'-mongo-attendance-1','mongosh','--quiet','--host','127.0.0.1',input=script).stdout.strip());assert all(proof.values());checks[own]=proof
 private_write(PRIVATE/'isolation-proof.json',{'allPassed':True,'sourceSha':S['sourceSha'],'ownerNonce':S['ownerNonce'],'checks':checks,'fixtureTargetWrites':0});print('Own isolated principal controls passed; probe transactions rolled back/aborted.')
def admission(phase,go,continuation=False):
 g=json.loads(pathlib.Path(go).read_text());assert g.get('approved') is True and g['phase']==phase and g['rootHead']==S['sourceSha'] and g['sourceCommit']==S['sourceSha']
 assert g['packageSha256']==package_hash() and g['ownOrigin']=='http://127.0.0.1:14250'
 source();ready();r=resources();assert r['diskAvailable']>=2684354560,'Early disk reserve; HOLD, no retry'
 assert r['memoryMaximum']>=r['minimumMemoryMaximumBytes'] and r['memoryAvailable']>=1073741824,'Local memory budget insufficient'
 if phase=='native-case':assert g.get('capacityQENV010Applied') is True and (PRIVATE/'fresh-runtime-preflight.json').exists()
 return g
