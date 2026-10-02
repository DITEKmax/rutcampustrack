from pathlib import Path
import importlib.util,argparse,json,sys,os
HERE=Path.cwd().resolve()
work=(HERE/'.agent/recovery-zh1002-no-files').resolve()
assert work.is_relative_to(HERE) and not work.exists()
base='rct-recovery-zh1002-no-files'
projects=[base+'-src',base+'-dst']
s=importlib.util.spec_from_file_location('drill',HERE/'scripts/test-recovery.py');d=importlib.util.module_from_spec(s);s.loader.exec_module(d)
r=d.r
os.umask(0o077)
try:
    for project in projects:
        for cmd in (["docker","ps","-aq"],["docker","network","ls","-q"],["docker","volume","ls","-q"]):
            r.require(not r.run(cmd+["--filter","label=com.docker.compose.project="+project]).strip(),'Existing owned target refused')
    for image in ('postgres:16','mongo:7.0'):r.run(['docker','image','inspect',image])
    work.mkdir(mode=0o700)
    r.write_json(work/'owned-projects.json',projects)
    for project in projects:
        password=project+'-synthetic-only'
        (work/(project+'.env')).write_text(f'POSTGRES_ACADEMIC_PASSWORD={password}\nPOSTGRES_SCHEDULE_PASSWORD={password}\nMONGO_ROOT_PASSWORD={password}\nRCT_RECOVERY_PG_IMAGE=postgres:16\nRCT_RECOVERY_MONGO_IMAGE=mongo:7.0\n',encoding='utf-8')
    src,dst=projects
    r.run(d.compose(work,src,'up','-d','--pull','never','--wait','--wait-timeout','120'))
    source=d.target(work,src);source.no_files=True;source.files_dir=None
    secret=r.credentials(source.env_file)
    for container,db,key in ((source.academic_container,'academic_db','POSTGRES_ACADEMIC_PASSWORD'),(source.schedule_container,'schedule_db','POSTGRES_SCHEDULE_PASSWORD')):
        r.pg_query(container,secret[key],db,"CREATE TABLE recovery_binary_probe(id bigserial PRIMARY KEY, payload bytea NOT NULL); INSERT INTO recovery_binary_probe(payload) VALUES (decode('000102ff','hex'));")
    r.mongo_query(source.mongo_container,secret['MONGO_ROOT_PASSWORD'],"for(const n of ['attendance_db','notification_db'])db.getSiblingDB(n).request_attachments.insertOne({_id:1,data:BinData(0,'AAEC/w==')}); print(JSON.stringify(true));")
    source.output=work/'bundle'
    r.backup(source)
    manifest=json.loads((source.output/'manifest.json').read_text())
    assert manifest['files_mode']=='database-only'
    r.run(d.compose(work,src,'stop','--timeout','10'))
    r.run(d.compose(work,dst,'up','-d','--pull','never','--wait','--wait-timeout','120'))
    dest=d.target(work,dst);dest.bundle=source.output
    try:r.restore(dest)
    except r.RecoveryError as e:
        assert 'mode does not match' in str(e),str(e)
        print('PASS wrong files mode refused before target writes')
    else:raise AssertionError('Wrong mode accepted')
    assert not dest.files_dir.exists()
    dest.no_files=True;dest.files_dir=None
    r.restore(dest)
    assert not (work/(src+'-files')).exists() and not (work/(dst+'-files')).exists()
    r.write_json(work/'PASS.json',{'result':'PASS','revision':'86e35277','projects':projects,
      'files_mode':'database-only','bundle_manifest_sha256':r.digest(source.output/'manifest.json'),
      'checks':['PG BYTEA exact inventory','both Mongo BSON Binary exact inventory','wrong files mode refused before write','no filesystem payload directories created']})
    print('PASS minimal DB-only Binary roundtrip; all artifacts retained, cleanup separate')
except Exception as e:
    print('FAIL minimal DB-only roundtrip: '+str(e),file=sys.stderr);sys.exit(1)
