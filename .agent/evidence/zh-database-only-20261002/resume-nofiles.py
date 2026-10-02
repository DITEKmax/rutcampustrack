from pathlib import Path
import importlib.util,json,sys
s=importlib.util.spec_from_file_location('d','scripts/test-recovery.py');d=importlib.util.module_from_spec(s);s.loader.exec_module(d);r=d.r
work=Path('.agent/recovery-zh1002-no-files').resolve();base='rct-recovery-zh1002-no-files';src=base+'-src';dst=base+'-dst'
try:
    source=d.target(work,src);source.no_files=True;source.files_dir=None;source.output=work/'bundle'
    c=r.credentials(source.env_file)
    assert r.mongo_query(source.mongo_container,c['MONGO_ROOT_PASSWORD'],"print(JSON.stringify(['attendance_db','notification_db'].map(n=>db.getSiblingDB(n).request_attachments.countDocuments({}))))")==[1,1]
    print('PASS existing corrected source fixture: both Mongo Binary docs present')
    r.backup(source)
    assert json.loads((source.output/'manifest.json').read_text())['files_mode']=='database-only'
    r.run(d.compose(work,src,'stop','--timeout','10'))
    r.run(d.compose(work,dst,'up','-d','--pull','never','--wait','--wait-timeout','120'))
    dest=d.target(work,dst);dest.bundle=source.output
    try:r.restore(dest)
    except r.RecoveryError as e:
        assert 'mode does not match' in str(e),str(e);print('PASS wrong files mode refused before target writes')
    else:raise AssertionError('Wrong mode accepted')
    assert not dest.files_dir.exists()
    dest.no_files=True;dest.files_dir=None;r.restore(dest)
    assert not (work/(src+'-files')).exists() and not (work/(dst+'-files')).exists()
    r.write_json(work/'PASS.json',{'result':'PASS','revision':'86e35277','projects':[src,dst],'files_mode':'database-only',
      'bundle_manifest_sha256':r.digest(source.output/'manifest.json'),'checks':['PG BYTEA exact inventory','both Mongo BSON Binary exact inventory','wrong files mode refused before write','no filesystem payload directories created']})
    print('PASS minimal DB-only Binary roundtrip; retained bundle; cleanup separate')
except Exception as e:
    print('FAIL resumed minimal DB-only roundtrip: '+str(e),file=sys.stderr);sys.exit(1)
