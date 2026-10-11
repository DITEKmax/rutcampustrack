import datetime,hashlib,json,os,pathlib,subprocess,sys
base=pathlib.Path(__file__).parent
wt=pathlib.Path('/workspace/rct-worktrees/file-boundaries')
run=sys.argv[1]
args=sys.argv[2:]
command=['python3','/workspace/rct-cloud-setup/run-gradle.py',*args]
env=dict(os.environ)
for key in ('DOCKER_CONTEXT','DOCKER_TLS','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH'):env.pop(key,None)
env['DOCKER_HOST']='unix:///var/run/docker.sock'
env['RCT_FILES_EVIDENCE_DIR']=str(base/(run+'-artifacts'))
paths=['services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java','services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapFileBoundaryIT.java','services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapFileFixture.java']
record={'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'run':run,'cwd':str(wt),'argv':command,'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=wt,text=True).strip(),'hashes':{p:hashlib.sha256((wt/p).read_bytes()).hexdigest() for p in paths},'lease':'FILES2 max-workers1','owned_resource_label':'rct.package=file-boundaries-20261011','status':'RUNNING'}
(base/(run+'-prelaunch.json')).write_text(json.dumps(record,indent=2)+'\n')
with (base/'attempts.ndjson').open('a') as f:f.write(json.dumps(record)+'\n')
with (base/'CURRENT.md').open('a') as f:f.write('\nActive '+run+'; exactargv/prelaunchhashes '+run+'-prelaunch.json, FILES2max1. NativePID recordednext; no duplicate launcher.\n')
with (base/(run+'.log')).open('w') as log:
    child=subprocess.Popen(command,cwd=wt,env=env,stdout=log,stderr=subprocess.STDOUT)
    record['native_launcher_pid']=child.pid
    (base/(run+'-prelaunch.json')).write_text(json.dumps(record,indent=2)+'\n')
    print(json.dumps({'run':run,'native_launcher_pid':child.pid}),flush=True)
    code=child.wait()
record.update({'completed_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'exit_code':code,'status':'TERMINAL'})
(base/(run+'-terminal.json')).write_text(json.dumps(record,indent=2)+'\n')
with (base/'attempts.ndjson').open('a') as f:f.write(json.dumps(record)+'\n')
print(json.dumps({'run':run,'exit_code':code}),flush=True)
sys.exit(code)
