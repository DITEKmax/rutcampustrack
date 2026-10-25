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
 ownership();args=['exec','-i']
 if password is not None:args+=['-e','PGPASSWORD='+password]
 args += [PROJECT+'-postgres-academic-1','psql','-X','-A','-t','-q','-U',user,'-d',db,'-v','ON_ERROR_STOP=1']
 if password is not None:args+=['-h','127.0.0.1']
 return docker(*args,input=sql,expected=expected)
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
def source():
 assert call(['git','-C',str(REPO),'rev-parse','HEAD']).stdout.strip()==S['sourceSha'],'Pinned source changed'
 assert not call(['git','-C',str(REPO),'diff','--name-only','HEAD']).stdout.strip(),'Tracked source edits; freeze a clean published checkout'
 for line in (ROOT/'source-inputs.SHA256SUMS').read_text().splitlines():
  digest,name=line.split('  ',1);assert sha(REPO/name)==digest,'Frozen local source input changed'
 for a in P['artifacts']:assert sha(a['path'])==a['sha256'],'Built local JAR changed'
 for tag,image_id in S['images'].items():assert json.loads(docker('image','inspect','--format','{{json .Id}}',tag).stdout)==image_id,'Local image pin changed; no pull or replacement'
def resources():
 info={k:int(v.split()[0])*1024 for k,v in (line.split(':',1) for line in pathlib.Path('/proc/meminfo').read_text().splitlines())}
 maximum=info['MemTotal'];available=info['MemAvailable'];cg=pathlib.Path('/sys/fs/cgroup')
 if (cg/'memory.max').exists() and (cg/'memory.max').read_text().strip()!='max':
  bound=int((cg/'memory.max').read_text());current=int((cg/'memory.current').read_text());maximum=min(maximum,bound);available=min(available,bound-current)
 return {'memoryMaximum':maximum,'memoryAvailable':available,'memoryCurrent':maximum-available,'diskAvailable':shutil.disk_usage(ROOT).free,'cpuCount':os.cpu_count()}
def pending():
 assert pg("BEGIN READ ONLY; SELECT count(*) FROM group_deletion_operations WHERE phase NOT IN ('COMPLETED','CANCELLED'); ROLLBACK;",'academic_db').stdout.strip()=='0','Nonterminal original operation; inspect status only'
def admission(phase,go,continuation=False):
 g=json.loads(pathlib.Path(go).read_text());assert g.get('approved') is True and g['phase']==phase and g['rootHead']==S['sourceSha'] and g['sourceCommit']==S['sourceSha']
 assert g['packageSha256']==package_hash() and g['ownOrigin']=='http://127.0.0.1:14250'
 source();ownership();r=resources();assert r['diskAvailable']>=2684354560,'Early disk reserve; HOLD, no retry'
 assert r['memoryMaximum']>=12884901888 and r['memoryAvailable']>=1073741824,'Local memory budget insufficient'
 if phase=='native-case':assert g.get('capacityQENV010Applied') is True and (PRIVATE/'fresh-runtime-preflight.json').exists()
 return g
