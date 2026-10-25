#!/usr/bin/env python3
"""Finite owner-local PR3 handoff. Never launches or connects to the cloud stand."""
import argparse,datetime,getpass,hashlib,importlib.util,ipaddress,json,os,pathlib,re,secrets,shutil,signal,socket,subprocess,sys,time

HERE=pathlib.Path(__file__).resolve().parent
REPO=HERE.parents[2]
STATE=REPO/'.pr3-local';ROOT=STATE/'runtime';PRIVATE=ROOT/'private'
PROJECT='rct-pr3-local';DOCKER=['docker','--host=unix:///var/run/docker.sock']
PORTS=[14250,14251,14252,18443]
APP_MODULES={'auth':'auth-service/auth-app','academic':'academic-service/academic-app','schedule':'schedule-service/schedule-app','attendance':'attendance-service/attendance-app','notification':'notification-service/notification-app','mobile-bff':'mobile-bff/mobile-bff-app','api-gateway':'api-gateway'}
ENV={k:v for k,v in os.environ.items() if k in {'PATH','HOME','USER','LOGNAME','LANG','LC_ALL','TERM','TMPDIR','JAVA_HOME'}}

def sha(p):return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()
def run(args,input=None,timeout=60):
 r=subprocess.run(args,cwd=REPO,env=ENV,input=input,text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
 if r.returncode:raise RuntimeError('Scoped local command failed; private output withheld. Preserve state; do not repeat mutation.')
 return r.stdout
def write(p,value):
 p=pathlib.Path(p);p.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
 fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
 with os.fdopen(fd,'w') as f:f.write(value if isinstance(value,str) else json.dumps(value,indent=2)+'\n');f.flush();os.fsync(f.fileno())
 d=os.open(p.parent,os.O_RDONLY)
 try:os.fsync(d)
 finally:os.close(d)
def platform():
 assert sys.platform=='linux' and REPO==pathlib.Path.home()/'rutcampustrack-pr3','Use only the new ~/rutcampustrack-pr3 checkout on owner Ubuntu/WSL'
 assert not STATE.is_symlink() and REPO.resolve()==REPO,'Symlink checkout/state is not admitted'
 for exe in ['docker','openssl','java','node','npm','git']:
  assert shutil.which(exe),'Missing required local tool: '+exe
 assert run(['node','--version']).strip()=='v24.14.0','Use Node24.14.0'
 assert run(['npm','--version']).strip()=='11.9.0','Use npm11.9.0'
 java=subprocess.run(['java','-version'],capture_output=True,text=True,env=ENV).stderr
 assert re.search(r'version "21[.\"]',java),'JDK21 required'
 assert run(DOCKER+['info','--format','{{.OSType}}']).strip()=='linux','Local Linux Docker Engine required'
def ports_free():
 for port in PORTS:
  with socket.socket() as s:s.bind(('127.0.0.1',port))
def source_check(source_sha):
 assert re.fullmatch('[0-9a-f]{40}',source_sha),'Exact published40-character source SHA required'
 assert run(['git','rev-parse','HEAD']).strip()==source_sha,'Checkout does not match owner-pinned SHA'
 assert not run(['git','diff','--name-only','HEAD']).strip(),'Tracked source must be clean'
 assert not run(['git','ls-files','--others','--exclude-standard','--','services','frontends','proto','tests/e2e/pr3-local']).strip(),'Untracked executable/source inputs are not admitted'
def load_runtime():
 sys.path.insert(0,str(ROOT));import runtime;return runtime
def available():
 info={k:int(v.split()[0])*1024 for k,v in (line.split(':',1) for line in pathlib.Path('/proc/meminfo').read_text().splitlines())}
 maxmem=info['MemTotal'];mem=info['MemAvailable'];cg=pathlib.Path('/sys/fs/cgroup')
 if (cg/'memory.max').exists() and (cg/'memory.max').read_text().strip()!='max':
  limit=int((cg/'memory.max').read_text());maxmem=min(maxmem,limit);mem=min(mem,limit-int((cg/'memory.current').read_text()))
 return {'freeBytes':shutil.disk_usage(REPO).free,'memoryMaximum':maxmem,'memoryAvailable':mem,'cpuCount':os.cpu_count()}
def prepare(args):
 platform();source_check(args.source_sha);ports_free();assert not STATE.exists(),'Existing local state is retained; no rekey/reset/second fixture'
 budget=available();assert budget['freeBytes']>=7516192768 and budget['memoryMaximum']>=12884901888 and budget['memoryAvailable']>=5368709120 and budget['cpuCount']>=4,'After-JAR preflight requires7GiB disk/12GiB total/5GiB available/4CPU'
 # Inspect metadata only. Never adopt a pre-existing project, volume or overlapping network.
 ids=run(DOCKER+['ps','-aq']).splitlines();baseline=[]
 fmt='{"Id":{{json .Id}},"Name":{{json .Name}},"Image":{{json .Image}},"Mounts":{{json .Mounts}},"Running":{{json .State.Running}},"Project":{{json (index .Config.Labels "com.docker.compose.project")}},"Service":{{json (index .Config.Labels "com.docker.compose.service")}},"Owner":{{json (index .Config.Labels "rct.pr3.local.owner")}}}'
 if ids:baseline=[json.loads(x) for x in run(DOCKER+['inspect','--format',fmt,*ids]).splitlines()]
 assert not any(x['Project']==PROJECT or x['Name'].startswith('/'+PROJECT+'-') for x in baseline),'Existing namespace is not ours; preserve it'
 for x in baseline:x['Mounts']=sorted(x['Mounts'],key=lambda m:json.dumps(m,sort_keys=True))
 assert not run(DOCKER+['volume','ls','-q','--filter','name='+PROJECT]).strip(),'Existing local volumes must not be adopted'
 nets=run(DOCKER+['network','ls','-q']).splitlines()
 if nets:
  for net in json.loads(run(DOCKER+['network','inspect',*nets])):
   for cfg in net.get('IPAM',{}).get('Config') or []:
    if cfg.get('Subnet'):assert not ipaddress.ip_network(cfg['Subnet']).overlaps(ipaddress.ip_network('172.31.35.0/24')),'Local subnet overlaps another namespace'
 jars=[]
 for name,module in APP_MODULES.items():
  p=REPO/'services'/module/'build/libs'/(name+'-app-0.1.0.jar' if name!='api-gateway' else 'api-gateway-0.1.0.jar')
  assert p.is_file() and not p.is_symlink(),'Build required JAR first: '+str(p.relative_to(REPO))
  jars.append({'service':name,'path':str(p),'sha256':sha(p),'bytes':p.stat().st_size})
 assert (REPO/'frontends/node_modules/vite/bin/vite.js').is_file() and (REPO/'tests/e2e/node_modules/@playwright/test/package.json').is_file(),'Install the two locked npm workspaces before prepare'
 image_tags={x['image'] for x in json.loads((HERE/'templates/compose.template.json').read_text())['services'].values()}
 images={tag:json.loads(run(DOCKER+['image','inspect','--format','{{json .Id}}',tag])) for tag in sorted(image_tags)}
 try:import bcrypt
 except ImportError:raise RuntimeError('Install Ubuntu python3-bcrypt; use /usr/bin/python3')
 password=getpass.getpass('Новый текущий пароль Admin1 только этого локального стенда: ')
 assert len(password)>=12 and password==getpass.getpass('Повтори пароль: '),'Choose and confirm at least12characters'
 assert len(password.encode())<=72,'bcrypt input must be at most72UTF8bytes'
 password_hash=bcrypt.hashpw(password.encode(),bcrypt.gensalt(rounds=10)).decode()
 STATE.mkdir(mode=0o700);ROOT.mkdir(mode=0o700);PRIVATE.mkdir(mode=0o700)
 for f in (HERE/'runtime').iterdir():
  if f.is_file():write(ROOT/f.name,f.read_text())
 for f in (HERE/'templates').iterdir():
  if f.is_file() and f.name!='compose.template.json':write(ROOT/f.name,f.read_text())
 write(PRIVATE/'foreign-baseline.json',baseline)
 contract=json.loads((HERE/'source-contract.json').read_text());services=contract['minimumServices'];nonce=secrets.token_hex(16)
 settings={'repo':str(REPO),'sourceSha':args.source_sha,'ownerNonce':nonce,'services':services,'images':images,'localGate':'NOT_RUN','referenceContractSha256':sha(HERE/'source-contract.json')}
 write(ROOT/'settings.json',settings)
 write(ROOT/'pins.json',{'sourceCommit':args.source_sha,'artifacts':jars,'project':PROJECT,'preflight':{'minimumFreshFreeBytes':7516192768,'earlyStopBytes':2684354560,'hardReserveBytes':2147483648,'sampleIntervalSeconds':.25}})
 names=run(['git','ls-files','--','services','proto','frontends','gradle','build.gradle.kts','settings.gradle.kts','tests/e2e/pr3-local']).splitlines()
 names=sorted(n for n in names if '/build/' not in n and '/node_modules/' not in n and '/dist/' not in n)
 write(ROOT/'source-inputs.SHA256SUMS',''.join(sha(REPO/n)+'  '+n+'\n' for n in names))
 frontend=sorted(n for n in names if any(n.startswith(x) for x in ['frontends/web-vue/src/','frontends/pwa-vue/src/','frontends/tma-vue/src/','frontends/mobile-core/src/','frontends/shared/']) and '/dev/' not in n and '.test.' not in n and '/__fixtures__/' not in n)
 write(ROOT/'frontend-inputs.SHA256SUMS',''.join(sha(REPO/n)+'  '+n+'\n' for n in frontend))
 write(ROOT/'bindings.json',{'state':'SOURCE_READY_FOR_INDEPENDENT_REVIEW','frontendRootHead':args.source_sha,'frontendInputs':len(frontend),'frontendInputsManifestSha256':sha(ROOT/'frontend-inputs.SHA256SUMS')})
 for folder in ['tls','keys','auth-secrets']:(PRIVATE/folder).mkdir(mode=0o700)
 values={k:secrets.token_urlsafe(32) for k in ['POSTGRES_ACADEMIC_PASSWORD','REDIS_PASSWORD','RABBITMQ_PASSWORD','GRPC_SECRET','INTERNAL_ISSUER_SECRET','TMA_BOT_TOKEN','GROUP_MONGO_ROOT_PASSWORD','GROUP_MONGO_RS_KEY','GROUP_MONGO_ATTENDANCE_PASSWORD','GROUP_MONGO_NOTIFICATION_PASSWORD']}
 values.update({'RABBITMQ_USER':'rct_pr3_local','LOCAL_UID':str(os.getuid()),'LOCAL_GID':str(os.getgid()),'LANE_ROOT':str(ROOT),'LANE_PRIVATE':str(PRIVATE),'GROUP_ROOT':str(ROOT),'GROUP_PRIVATE':str(PRIVATE),'CORS_ALLOWED_ORIGIN':'http://127.0.0.1:14250','ACADEMIC_EXTRA_JSON':'{}'})
 compose=json.loads((HERE/'templates/compose.template.json').read_text())
 for service in compose['services'].values():service.setdefault('labels',{})['rct.pr3.local.owner']=nonce
 for kind in ['volumes','networks']:
  for item in compose[kind].values():item.setdefault('labels',{})['rct.pr3.local.owner']=nonce
 compose['services']['postgres-academic']['secrets'].append({'source':'group-schedule-runtime-password','target':'rct-schedule-runtime-password'})
 for name,a in ((x['service'],x) for x in jars):values['GROUP_'+name.upper().replace('-','_')+'_JAR']=a['path']
 compose['services']['api-gateway']['volumes']=['${GROUP_API_GATEWAY_JAR}:/app/app.jar:ro']
 for key,secret in compose['secrets'].items():
  file=secret['file'].replace('${LANE_PRIVATE}',str(PRIVATE)).replace('${GROUP_PRIVATE}',str(PRIVATE));p=pathlib.Path(file)
  if not p.exists():write(p,secrets.token_urlsafe(32))
 # These exact directed bindings must be shared by both ends; no global secret discovery.
 values['MOBILE_BFF_TO_AUTH_SERVICE_TOKEN']=(PRIVATE/'auth-secrets/mobile-bff-to-auth-service-token').read_text()
 values['MOBILE_BFF_TO_ACADEMIC_SERVICE_TOKEN']=(PRIVATE/'auth-secrets/mobile-bff-to-academic-service-token').read_text()
 mongo_a=values['GROUP_MONGO_ATTENDANCE_PASSWORD'];mongo_n=values['GROUP_MONGO_NOTIFICATION_PASSWORD']
 for name,text in {'SPRING_DATA_MONGODB_URI':'mongodb://rct_group_attendance:'+mongo_a+'@mongo-attendance:27017/attendance_db?authSource=attendance_db&replicaSet=rs0','NOTIFICATION_SPRING_DATA_MONGODB_URI':'mongodb://rct_group_notification:'+mongo_n+'@mongo-attendance:27017/notification_db?authSource=notification_db&replicaSet=rs0'}.items():
  (PRIVATE/name).write_text(text);os.chmod(PRIVATE/name,0o600)
 vapid=json.loads(run(['node','-e',"const c=require('crypto').createECDH('prime256v1');c.generateKeys();process.stdout.write(JSON.stringify({public:c.getPublicKey().toString('base64url'),private:c.getPrivateKey().toString('base64url')}))"]))
 for name,key in [('VAPID_PUBLIC_KEY','public'),('VAPID_PRIVATE_KEY','private')]:
  (PRIVATE/('NOTIFICATION_'+name)).write_text(vapid[key]);os.chmod(PRIVATE/('NOTIFICATION_'+name),0o600)
 write(PRIVATE/'local.env',''.join(k+'='+v+'\n' for k,v in values.items()))
 write(ROOT/'compose.json',compose)
 # First-volume only: canonical repository init and additive isolated Schedule database.
 init=(REPO/'infra/postgres/init-roles.sh').read_text()
 init+='\npsql --username "$POSTGRES_USER" --dbname postgres --set ON_ERROR_STOP=1 <<\'SQL\'\nCREATE DATABASE schedule_db OWNER rct_migrator;\nREVOKE ALL ON DATABASE schedule_db FROM PUBLIC;\nCREATE ROLE rct_group_schedule_runtime LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;\nSQL\n'
 init+='RCT_GROUP_SCHEDULE_PASSWORD="$(cat /run/secrets/rct-schedule-runtime-password)"\nexport RCT_GROUP_SCHEDULE_PASSWORD\npsql --username "$POSTGRES_USER" --dbname schedule_db --set ON_ERROR_STOP=1 <<\'SQL\'\n\\getenv local_schedule_password RCT_GROUP_SCHEDULE_PASSWORD\nSELECT format(\'ALTER ROLE rct_group_schedule_runtime PASSWORD %L\', :\'local_schedule_password\') \\gexec\nGRANT CONNECT ON DATABASE schedule_db TO rct_migrator,rct_group_schedule_runtime;\nREVOKE ALL ON SCHEMA public FROM PUBLIC;\nALTER SCHEMA public OWNER TO rct_migrator;\nGRANT USAGE ON SCHEMA public TO rct_runtime,rct_group_schedule_runtime;\nSQL\nunset RCT_GROUP_SCHEDULE_PASSWORD\n'
 write(ROOT/'init-roles.sh',init);os.chmod(ROOT/'init-roles.sh',0o755)
 (ROOT/'callbacks').mkdir(mode=0o700)
 for name in ['afterMigrate.sql','afterMigrate__zz_group_runtime.sql']:write(ROOT/'callbacks'/name,(ROOT/name).read_text())
 for db in ['auth','academic']:
  d=ROOT/'baseline'/db;d.mkdir(parents=True,mode=0o700)
  original=(REPO/'tests/e2e/infra/baseline'/db/('afterMigrate__e2e_synthetic_'+db+'_baseline.sql')).read_text()
  if db=='auth':original=re.sub(r"synthetic_hash CONSTANT text := '[^']+';","synthetic_hash CONSTANT text := '"+password_hash+"';",original)
  else:original=original.replace("'Spring 2026', '2026-02-01', '2026-06-30'","'PR3 empty-realm baseline', '2000-02-01', '2000-06-30'")
  write(d/('afterMigrate__e2e_synthetic_'+db+'_baseline.sql'),original)
 tls=PRIVATE/'tls'
 run(['openssl','req','-x509','-newkey','rsa:3072','-nodes','-keyout',str(tls/'ca.key'),'-out',str(tls/'ca.crt'),'-days','7','-subj','/CN=rct-pr3-local-CA'])
 for name in ['auth','academic','schedule','attendance','server']:
  cn=name+'-service' if name!='server' else 'localhost';ext=tls/(name+'.ext')
  write(ext,'subjectAltName=DNS:'+cn+(',DNS:localhost,IP:127.0.0.1' if name=='server' else '')+'\nextendedKeyUsage=serverAuth\n')
  run(['openssl','req','-newkey','rsa:3072','-nodes','-keyout',str(tls/(name+'-server.key' if name!='server' else 'server.key')),'-out',str(tls/(name+'.csr')),'-subj','/CN='+cn])
  run(['openssl','x509','-req','-in',str(tls/(name+'.csr')),'-CA',str(tls/'ca.crt'),'-CAkey',str(tls/'ca.key'),'-CAcreateserial','-out',str(tls/(name+'-server.crt' if name!='server' else 'server.crt')),'-days','7','-extfile',str(ext)])
 run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:3072','-out',str(PRIVATE/'keys/private.key')])
 run(['openssl','pkey','-in',str(PRIVATE/'keys/private.key'),'-pubout','-out',str(PRIVATE/'keys/public.key')])
 for p in PRIVATE.rglob('*'):
  if p.is_file():os.chmod(p,0o600)
 # PostgreSQL initializes as UID999, not the host UID. Exact secret mounts are
 # readable inside that owned container; host parent directories remain0700.
 for item in compose['services']['postgres-academic']['secrets']:
  key=item['source'] if isinstance(item,dict) else item
  file=compose['secrets'][key]['file'].replace('${LANE_PRIVATE}',str(PRIVATE)).replace('${GROUP_PRIVATE}',str(PRIVATE))
  os.chmod(file,0o444)
 os.chmod(ROOT/'init-group-mongo.js',0o444)
 config={'baseURL':'http://127.0.0.1:14250','groupName':'ГДЛ-199','controlGroupName':'ГДЛ-198','admin':{'login':'admin','password':password}}
 write(PRIVATE/'native-config.json',config);password=None;password_hash=None
 frontend_config=(HERE/'templates/frontend-config.ts').read_text().replace('__REPO__',str(REPO)).replace('__STATE__',str(ROOT))
 write(ROOT/'frontend-config.ts',frontend_config)
 inputs=sorted(p for p in ROOT.rglob('*') if p.is_file() and PRIVATE not in p.parents and p.name not in ['SOURCE.SHA256SUMS','PACKAGE-SHA256'])
 write(ROOT/'SOURCE.SHA256SUMS',''.join(sha(p)+'  '+str(p.relative_to(ROOT))+'\n' for p in inputs))
 write(ROOT/'PACKAGE-SHA256',sha(ROOT/'SOURCE.SHA256SUMS')+'\n')
 write(PRIVATE/'prepared.json',{'state':'NOT_RUN','sourceSha':args.source_sha,'sourceFiles':len(names),'frontendInputs':len(frontend),'packageSha256':sha(ROOT/'SOURCE.SHA256SUMS'),'foreignContainers':len(baseline),'servicesStarted':0,'AuthApiDbActions':0,'budget':budget})
 print('Prepared fresh local files only. Services/fixture/delete NOT_RUN. No cloud or old checkout read.')
def go(runtime,phase):
 value={'approved':True,'phase':phase,'rootHead':runtime.S['sourceSha'],'sourceCommit':runtime.S['sourceSha'],'packageSha256':runtime.package_hash(),'driverPackageSha256':runtime.package_hash(),'cohort':'ONE_DELETE_SIX_LIVE_VIEWS','ownOrigin':'http://127.0.0.1:14250','telegramContext':'CONTROLLED_OWN_ISOLATED_SIGNER','telegramSignerSource':str(PRIVATE/'local.env'),'telegramSignerSelector':'TMA_BOT_TOKEN','allowObservedTmaHeadmanSuccessorCleanup':True,'capacityQENV010Applied':(PRIVATE/'fresh-runtime-preflight.json').exists()}
 file=PRIVATE/(phase+'-owner-command.json');write(file,value);return file
def guarded(runtime,args,phase,timeout=300):
 log=PRIVATE/(phase+'-'+str(time.time_ns())+'.log');samples=[];stopped=False;interrupted=False
 with log.open('x') as out:
  os.chmod(log,0o600);proc=subprocess.Popen(args,cwd=ROOT,env=runtime.ENV,stdout=out,stderr=subprocess.STDOUT,start_new_session=True);start=time.monotonic()
  try:
   while proc.poll() is None:
    budget=runtime.resources();samples.append({'seconds':round(time.monotonic()-start,2),**budget})
    if budget['diskAvailable']<2684354560 or time.monotonic()-start>timeout:
     stopped=True;os.killpg(proc.pid,signal.SIGTERM);break
    time.sleep(.25)
  except KeyboardInterrupt:
   interrupted=True;stopped=True
   if proc.poll() is None:os.killpg(proc.pid,signal.SIGTERM)
  try:rc=proc.wait(timeout=10)
  except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);rc=proc.wait(timeout=5)
 write(PRIVATE/(phase+'-receipt-'+str(time.time_ns())+'.json'),{'exit':rc,'guardStopped':stopped,'interrupted':interrupted,'samples':samples,'privateLog':str(log)})
 if stopped and phase=='start':
  rows=runtime.ownership();ids=[x['Id'] for x in rows if x['Project']==PROJECT]
  if ids:run(DOCKER+['stop','--time','2',*ids],timeout=60)
  write(PRIVATE/('startup-guard-closure-'+str(time.time_ns())+'.json'),{'exactOwnedIdsStopped':ids,'volumesDeleted':False,'foreignResourcesChanged':False})
 if interrupted:raise KeyboardInterrupt()
 assert rc==0 and not stopped,'Scoped process failed/guarded; preserve original intent and inspect; no blind retry'
def start(runtime):
 runtime.source();runtime.ownership();ports_free();assert not (PRIVATE/'start-intent.json').exists(),'Start was already attempted; inspect retained owned services'
 budget=runtime.resources();assert budget['diskAvailable']>=7516192768 and budget['memoryAvailable']>=5368709120,'Fresh after-JAR7GiB disk/5GiB available RAM required'
 for image in {x['image'] for x in json.loads((ROOT/'compose.json').read_text())['services'].values()}:run(DOCKER+['image','inspect',image])
 write(PRIVATE/'fresh-runtime-preflight.json',{'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sourceSha':runtime.S['sourceSha'],**budget})
 write(PRIVATE/'start-intent.json',{'state':'INTENT','sourceSha':runtime.S['sourceSha']})
 guarded(runtime,runtime.compose('up','-d','--pull','never'),'start',300)
 deadline=time.monotonic()+360
 while time.monotonic()<deadline:
  rows=runtime.ownership();own=[r for r in rows if r['Project']==PROJECT]
  if len(own)==12:
   states=[json.loads(run(DOCKER+['inspect','--format','{{json .State}}',x['Id']])) for x in own]
   assert not any(x['Status'] in ['exited','dead'] for x in states),'Own service exited; no automatic restart/retry'
   if all(x.get('Health',{}).get('Status')=='healthy' for x in states):
    write(PRIVATE/'services-ready.json',{'state':'SERVICES_READY_UI_NOT_RUN','sourceSha':runtime.S['sourceSha'],'ids':[x['Id'] for x in own]});print('Own12services healthy; fixture/UI delete NOT_RUN.');return
  assert runtime.resources()['diskAvailable']>=2684354560,'Early reserve; preserve data, no further startup'
  time.sleep(.25)
 raise RuntimeError('Bounded readiness expired; preserve state, do not repeat start')
def frontends(runtime):
 runtime.source();runtime.ownership();assert (PRIVATE/'services-ready.json').exists();children=[]
 write(PRIVATE/'frontend-intent.json',{'clients':['web','pwa','tma'],'fixtureMode':False})
 try:
  for client in ['web','pwa','tma']:
   env=runtime.ENV.copy();env.update(RCT_LOCAL_CLIENT=client,VITE_MOBILE_FIXTURE_MODE='false',VITE_API_PROXY_TARGET='https://127.0.0.1:18443',VITE_PUBLIC_BASE='/',NODE_EXTRA_CA_CERTS=str(PRIVATE/'tls/ca.crt'),NODE_OPTIONS='--max-old-space-size=512')
   log=(PRIVATE/('frontend-'+client+'.log')).open('x');os.chmod(log.name,0o600)
   proc=subprocess.Popen(['node',str(REPO/'frontends/node_modules/vite/bin/vite.js'),'--config',str(ROOT/'frontend-config.ts'),'--mode','development'],cwd=REPO/'frontends'/(client+'-vue'),env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True);children.append((proc,log))
  print('Three own source servers running in this terminal; use a second terminal for fixture/case. Ctrl-C closes only these child groups.')
  deadline=time.monotonic()+1800
  while time.monotonic()<deadline:
   assert all(p.poll() is None for p,_ in children),'Own frontend exited; no automatic restart'
   assert runtime.resources()['diskAvailable']>=2684354560,'Early disk reserve; owned frontends stop, data preserved'
   time.sleep(.25)
 finally:
  for proc,log in children:
   if proc.poll() is None:
    os.killpg(proc.pid,signal.SIGTERM)
    try:proc.wait(timeout=10)
    except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait(timeout=5)
   log.close()
  write(PRIVATE/('frontend-closed-'+str(time.time_ns())+'.json'),{'ownedPids':[p.pid for p,_ in children],'otherProcessesTouched':False})
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['prepare','start','frontends','fixture','case','inspect','summary','stop']);p.add_argument('--source-sha');args=p.parse_args()
 if args.action=='prepare':
  if not args.source_sha:p.error('prepare requires --source-sha with the exact published40-character SHA')
  prepare(args);return
 platform();runtime=load_runtime();runtime.source();runtime.ownership()
 if args.action=='start':start(runtime)
 elif args.action=='frontends':frontends(runtime)
 elif args.action=='fixture':
  for port in PORTS[:3]:
   with socket.socket() as s:assert s.connect_ex(('127.0.0.1',port))==0,'All3source servers required'
  g=go(runtime,'calendar-setup');guarded(runtime,['node','calendar-local.cjs',str(g),str(PRIVATE/'native-config.json')],'calendar-setup',120)
  g=go(runtime,'fixture-api');guarded(runtime,['node','fixture-api.cjs',str(g),str(PRIVATE/'native-config.json')],'fixture-api',300)
  g=go(runtime,'fixture-rows');guarded(runtime,[sys.executable,'seed-owned-rows.py','--go',str(g)],'fixture-rows',120)
 elif args.action=='case':
  assert (PRIVATE/'fixture-rows-receipt.json').exists(),'Own target/control fixtures required'
  g=go(runtime,'native-case');guarded(runtime,['node','native-cohort.cjs','execute-once',str(g),str(PRIVATE/'native-config.json')],'native-case',600)
  receipt=json.loads((PRIVATE/'native-case-result.json').read_text());assert receipt['allPassed'] and receipt['cleanupComplete']
  print('LOCAL case reports COMPLETED with exact own cleanup; share only the redacted summary described in README. Cloud/production acceptance is not claimed.')
 elif args.action=='inspect':
  # No Auth, login, resolve/POST or retry. Only original local row metadata/digests.
  files={f.name:True for f in PRIVATE.glob('*.json')};result={'sourceSha':runtime.S['sourceSha'],'privateFilesPresent':files,'budget':runtime.resources(),'localGate':'NOT_RUN_UNTIL_OWNER_OUTPUT_REVIEWED'}
  if (PRIVATE/'fixture-state.json').exists():
   s=json.loads((PRIVATE/'fixture-state.json').read_text());result['fixtureStep']=s['step']
   if s.get('groupId'):
    group=int(s['groupId']);assert group>1
    result['originalOperations']=json.loads(runtime.pg("BEGIN READ ONLY; SELECT coalesce(jsonb_agg(jsonb_build_object('id',operation_id,'phase',phase,'groupId',group_id,'actorId',actor_id,'sessionId',session_id) ORDER BY created_at),'[]') FROM group_deletion_operations WHERE group_id="+str(group)+'; ROLLBACK;','academic_db').stdout)
  write(PRIVATE/('inspection-'+str(time.time_ns())+'.json'),result)
  print(json.dumps(result,indent=2))
 elif args.action=='summary':
  output={'sourceSha':runtime.S['sourceSha'],'driverPackageSha256':runtime.package_hash(),'gate':'NOT_RUN','cloudGroupGate':'NOT_RUN_BY_OWNER_DECISION','nativeTelegramProvider':'NOT_PROVEN','assembledCommonNginx':'SEPARATE_GATE'}
  file=PRIVATE/'receipt.json'
  if file.exists():
   r=json.loads(file.read_text());output.update(gate='OWNER_OUTPUT_AWAITING_REVIEW',state=r.get('state'),originalOperationId=r.get('operationId'),originalPhase=r.get('phase'),confirmRequests=r.get('confirmRequests'),checks=r.get('checks'),allPassed=r.get('allPassed',False),cleanupComplete=r.get('cleanupComplete',False),browserClosed=r.get('browserClosed',False),error=r.get('error'),blockedWrites=len(r.get('blockedWrites',[])))
  print(json.dumps(output,indent=2))
 elif args.action=='stop':
  assert (PRIVATE/'services-ready.json').exists(),'Incomplete startup: inspect exact owned IDs before further action'
  runtime.pending();rows=runtime.ownership();ids=[x['Id'] for x in rows if x['Project']==PROJECT]
  write(PRIVATE/('stop-intent-'+str(time.time_ns())+'.json'),{'ownedIds':ids,'volumesDeleted':False})
  if ids:run(DOCKER+['stop','--time','20',*ids],timeout=300)
  runtime.ownership();print('Stopped exact nonce-owned containers only; volumes/DB/keys/evidence retained. No down/prune/remove.')
if __name__=='__main__':
 try:main()
 except KeyboardInterrupt:print('Own foreground operation interrupted; preserve state and inspect original handles.',file=sys.stderr);sys.exit(130)
 except Exception as error:print('STOP: '+str(error),file=sys.stderr);sys.exit(1)
