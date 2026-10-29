"""Finite projections of original native artifacts; raw logs/XML stay private."""
from pathlib import Path
import hashlib
import json
import os
import re
import stat
import xml.etree.ElementTree as ET

LIMIT=32*1024*1024

def read(path, since):
    info=path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_nlink!=1 or info.st_uid!=os.getuid() or not 0<info.st_size<=LIMIT:
        raise ValueError('Artifact admission')
    if info.st_mtime_ns<since:raise ValueError('Stale artifact')
    fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW)
    with os.fdopen(fd,'rb') as source:return source.read(LIMIT+1)

def junit(path,since):
    raw=read(path,since)
    if b'<!DOCTYPE' in raw.upper() or b'<!ENTITY' in raw.upper():raise ValueError('XML entities')
    tree=ET.fromstring(raw)
    suites=[tree] if tree.tag=='testsuite' else list(tree.findall('testsuite'))
    if tree.tag not in ('testsuite','testsuites') or not suites:raise ValueError('JUnit shape')
    result={k:0 for k in ('tests','failures','errors','skipped')}
    for suite in suites:
        for key in result:
            value=int(suite.get(key,'0'))
            if not 0<=value<=1000000:raise ValueError('JUnit count')
            result[key]+=value
    return result

def sarif(path,since):
    value=json.loads(read(path,since));runs=value['runs']
    if not isinstance(runs,list) or len(runs)>32:raise ValueError('SARIF shape')
    levels={k:0 for k in ('error','warning','note','none','OTHER')}
    for run in runs:
        results=run.get('results',[])
        if not isinstance(results,list) or len(results)>100000:raise ValueError('SARIF bound')
        for result in results:
            level=result.get('level','warning');levels[level if level in levels else 'OTHER']+=1
    return {'runs':len(runs),'findings':sum(levels.values()),'levels':levels}

def coverage(path,since):
    raw=read(path,since)
    if path.suffix=='.xml':
        if b'<!ENTITY' in raw.upper():raise ValueError('Coverage XML entities')
        # JaCoCo emits a standard public DTD; ElementTree never fetches it.
        tree=ET.fromstring(raw)
        if tree.tag=='coverage':return {'kind':'cobertura','linesValid':int(tree.get('lines-valid','0')),'linesCovered':int(tree.get('lines-covered','0'))}
        if tree.tag!='report':raise ValueError('Coverage shape')
        return {'kind':'jacoco','counters':[{ 'type':c.get('type') if c.get('type') in {'INSTRUCTION','BRANCH','LINE','COMPLEXITY','METHOD','CLASS'} else 'OTHER','missed':int(c.get('missed','0')),'covered':int(c.get('covered','0'))} for c in tree.findall('counter')]}
    value=json.loads(raw)['total']
    return {'kind':'istanbul','counters':{k:{f:value[k][f] for f in ('total','covered','skipped','pct') if type(value[k].get(f)) in (int,float)} for k in ('lines','statements','functions','branches') if k in value}}

def exported(report, name, audit):
    directory=audit/'public'
    directory.mkdir(mode=0o700,exist_ok=True)
    raw=(json.dumps(report,sort_keys=True,indent=2,allow_nan=False)+'\n').encode()
    if len(raw)>LIMIT:raise ValueError('Closed publisher report exceeds original bound')
    filename=name+'.safe.json'
    fd=os.open(directory/filename,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
    with os.fdopen(fd,'wb') as target:target.write(raw)
    return {'file':filename,'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}


def project(root,audit,since):
    result={'junit':{},'coverage':{},'sarif':{},'safeReports':{}}
    # Fixed authored locations only. No private .env, raw errors, framework trees or arbitrary globs.
    paths=[]
    for module in ['api-gateway','auth-service/auth-app','academic-service/academic-app','schedule-service/schedule-app','attendance-service/attendance-app','notification-service/notification-app','mobile-bff/mobile-bff-app']:
        base=root/'services'/module
        for task in ('test','integrationTest'):
            paths += [('junit',x) for x in sorted((base/'build/test-results'/task).glob('TEST-*.xml'))[:512]]
        paths += [('coverage',base/'build/reports/jacoco/test/jacocoTestReport.xml')]
    paths += [('junit',root/'services/notification-bot/junit.xml'),('junit',root/'frontends/test-results/local-release.xml'),
              ('coverage',root/'services/notification-bot/coverage.xml'),('coverage',root/'frontends/coverage/coverage-summary.json')]
    paths += [('sarif',audit/x) for x in ('gitleaks.sarif','trivy-fs.sarif','trivy-config.sarif')]
    for kind,path in paths:
        key=str(path.relative_to(root)) if path.is_relative_to(root) else path.name
        if kind=='junit' and path.name.startswith('TEST-'):
            prefix=str(path.parent.relative_to(root))+'/'
            key=prefix+str(sum(k.startswith(prefix) for k in result['junit']))
        try:result[kind][key]={'status':'COLLECTED',**globals()[kind](path,since)}
        except FileNotFoundError:result[kind][key]={'status':'MISSING'}
        except Exception:result[kind][key]={'status':'REJECTED'}
    for service in ('api-gateway','auth-service','academic-service','schedule-service','attendance-service','notification-service','mobile-bff'):
        for task in ('test','integrationTest'):
            path=root/'build/ci-test-outcomes'/service/task/'safe-outcomes.json';key=service+'/'+task
            try:
                value=json.loads(read(path,since))
                # This existing closed publisher is already uploaded by CI. Keep its full diagnostics.
                result['safeReports'][key]={'status':'COLLECTED',**exported(value,service+'-'+task,audit)}
            except FileNotFoundError:result['safeReports'][key]={'status':'MISSING'}
            except Exception:result['safeReports'][key]={'status':'REJECTED'}
    roles={
        'monitoring-delivery':root/'build/monitoring/delivery.json',
        'monitoring-exporters':root/'build/monitoring/exporters.json',
        'monitoring-pipeline':root/'build/monitoring/pipeline.json',
        'observability-apps':root/'build/monitoring/apps.json',
        'native-acceptance':audit/'native/acceptance.safe.json',
        'common-static':audit/'native/common/static.safe.json',
        'common-auth':audit/'native/common/auth.safe.json',
        'bootstrap-cli':audit/'bootstrap/bootstrap-safe.json',
    }
    for mode in ('controls','full','client','webkit','lesson-lifecycle','ui-batch','special13','homework-sensitivity'):
        roles['browser-'+mode]=audit/('browser-'+mode)/'result.safe.json'
    for service,name in [('attendance-service','reason-enum-regression-safe.json'),('schedule-service','oneoff-regression-safe.json'),('attendance-service','attendance-enum-regression-safe.json'),('academic-service','cache-commit-safe.json')]:
        roles[name]=root/'build/ci-test-outcomes'/service/name
    for service in ('schedule-service','attendance-service','academic-service','notification-service','auth-service'):
        roles['changed-domain-'+service]=root/'build/ci-test-outcomes'/service/'changed-domain-regression-safe.json'
    for name,path in roles.items():
        try:result['safeReports'][name]={'status':'COLLECTED',**exported(json.loads(read(path,since)),name,audit)}
        except FileNotFoundError:result['safeReports'][name]={'status':'MISSING'}
        except Exception:result['safeReports'][name]={'status':'REJECTED'}
    result['nativeLogs']={}
    for path in sorted(audit.glob('*.private.log'))[:32]:
        try:result['nativeLogs'][path.stem]=native_log(path)
        except Exception:result['nativeLogs'][path.stem]={'status':'UNAVAILABLE'}
    return result


def admit_gate(name,root,audit,since,revision=None,nonce=None):
    """Native success and fresh native artifacts are both required; coverage has no threshold."""
    try:
        if name.startswith('java-') and name!='java-verification':
            service=name.removeprefix('java-')
            module={'api-gateway':'api-gateway','mobile-bff':'mobile-bff/mobile-bff-app'}.get(service,service+'/'+service.removesuffix('-service')+'-app')
            for task in ('test','integrationTest'):
                report=json.loads(read(root/'build/ci-test-outcomes'/service/task/'safe-outcomes.json',since))
                if report.get('collection')!='RESULTS' or not report.get('cases'):return False
                if report.get('metadata',{}).get('event')!='LOCAL_CONTROL':return False
            coverage(root/'services'/module/'build/reports/jacoco/test/jacocoTestReport.xml',since)
        elif name=='bot':
            if junit(root/'services/notification-bot/junit.xml',since)['tests']<=0:return False
            coverage(root/'services/notification-bot/coverage.xml',since)
        elif name=='vue':
            if junit(root/'frontends/test-results/local-release.xml',since)['tests']<=0:return False
            coverage(root/'frontends/coverage/coverage-summary.json',since)
        elif name in ('gitleaks','trivy'):
            for file in (['gitleaks.sarif'] if name=='gitleaks' else ['trivy-fs.sarif','trivy-config.sarif']):sarif(audit/file,since)
        elif name in ('monitoring','observability-apps'):
            for file in (['delivery','exporters','pipeline'] if name=='monitoring' else ['apps']):json.loads(read(root/'build/monitoring'/(file+'.json'),since))
        elif name=='native-group-and-nginx':
            report=json.loads(read(audit/'native/acceptance.safe.json',since))
            if not (report['result']=='PASS' and report['group']=='PASS' and report['commonNginx']=='PASS' and report['ownServicesStopped'] and report['frontendChildrenClosed']):return False
        elif name.startswith('browser-'):
            report=json.loads(read(audit/name/'result.safe.json',since))
            if report.get('status')!='PASS' or report.get('cleanup') is not True:return False
            if report.get('revision')!=(revision or os.environ.get('RCT_LOCAL_RELEASE_REVISION')):return False
            if nonce is not None and report.get('nonce')!=nonce:return False
            if name=='browser-client':
                bootstrap=json.loads(read(audit/'bootstrap/bootstrap-safe.json',since))
                context=bootstrap.get('context',{})
                if bootstrap.get('status')!='PASS' or bootstrap.get('cleanup')!='PASS' or bootstrap.get('nativeInvoked') is not True:return False
                if context.get('event')!='LOCAL_CONTROL' or context.get('checkoutSha')!=revision or context.get('runIdentity')!='local-'+nonce:return False
        return True
    except Exception:return False


def native_log(path):
    # Finite source-authored classes only. No message, payload, arbitrary line or secret fingerprint.
    with path.open('rb') as source:
        head=source.read(8*1024*1024)
        source.seek(max(0,path.stat().st_size-8*1024*1024));tail=source.read(8*1024*1024)
    raw=head+b'\n'+tail
    patterns={
        'COMPILATION_FAILURE':rb'Compilation failed|compileJava FAILED|cannot find symbol',
        'DEPENDENCY_RESOLUTION_FAILURE':rb'Could not resolve all|Could not download|Could not resolve ',
        'DOCKER_UNAVAILABLE':rb'Could not find a valid Docker environment|Cannot connect to the Docker daemon',
        'IMAGE_PULL_FAILURE':rb'Could not pull image|pull access denied|ContainerFetchException',
        'CONTAINER_START_FAILURE':rb'ContainerLaunchException|Could not start container',
        'NO_MATCHING_TESTS':rb'No tests found for given includes|No matching tests found',
        'GRADLE_TASK_FAILURE':rb'Execution failed for task|BUILD FAILED',
        'SUDO_UNAVAILABLE':rb'sudo:.*(?:password|terminal|not found)',
        'TOOL_DOWNLOAD_FAILURE':rb'HTTP Error [45][0-9][0-9]|curl:|Tool.*pin mismatch',
    }
    result={'categories':[key for key,pattern in patterns.items() if re.search(pattern,raw)],'retainedPrivateBytes':path.stat().st_size}
    text=raw.decode('utf-8','replace')
    for field,allowed in RECOVERY_FIELDS.items():
        values=re.findall(r'(?m)^RECOVERY_FAILURE_'+field+r':([A-Z_]+)\r?$',text)
        result['recovery'+field.title()]=next((v for v in reversed(values) if v in allowed),None)
    return result

RECOVERY_FIELDS={'CLASS': ['RECOVERY_ERROR', 'JSON_DECODE_ERROR', 'UNICODE_DECODE_ERROR', 'OS_ERROR', 'VALUE_ERROR', 'KEY_ERROR', 'OTHER'], 'GUARD': ['BUNDLE_ACCESS_MISSING', 'BUNDLE_ARTIFACTS', 'BUNDLE_DATABASES', 'BUNDLE_DATABASE_ONLY_FILES', 'BUNDLE_DIRECTORY', 'BUNDLE_INTEGRITY', 'BUNDLE_MODE_MISMATCH', 'BUNDLE_REGULAR_FILES', 'CREDENTIAL_FILE_MISSING', 'DISTINCT_TARGETS', 'FILES_ARCHIVE_INVENTORY', 'FILES_ARCHIVE_MEMBER', 'FILES_ENTRY', 'FILES_MODE', 'FILES_SOURCE', 'FILES_SYMLINK', 'MANIFEST_FILES_MODE', 'MANIFEST_FORMAT', 'NOT_QUIESCED', 'OTHER', 'OUTPUT_EXISTS', 'OUTPUT_IN_SOURCE', 'PG_ACCESS_DATABASE', 'PG_ACCESS_DUPLICATE', 'PG_ACCESS_INCOMPLETE', 'PG_ACCESS_OBJECT', 'RECOVERY_ADMIN', 'RESTORE_ACADEMIC_INVENTORY', 'RESTORE_ACADEMIC_SCHEMA', 'RESTORE_AUTH_INVENTORY', 'RESTORE_AUTH_SCHEMA', 'RESTORE_FILES_TARGET_EXISTS', 'RESTORE_MONGO_FILES', 'RESTORE_MONGO_TARGET_NOT_FRESH', 'RESTORE_PG_TARGET_NOT_FRESH', 'RESTORE_PROJECT_PREFIX', 'RESTORE_SCHEDULE_INVENTORY', 'RESTORE_SCHEDULE_SCHEMA', 'RESTORE_TARGET_LABELS', 'SCHEMA_REFERENCE_EXISTS', 'SCHEMA_REFERENCE_ID_CHANGED', 'SCHEMA_REFERENCE_ID_MISSING', 'SOURCE_CHANGED', 'TARGET_NOT_RUNNING'], 'HINT': ['GUARD', 'INPUT_CREDENTIAL', 'INSPECT', 'MONGO_DUMP', 'MONGO_QUERY', 'MONGO_RESTORE', 'OTHER_TOOL', 'PG_DUMP', 'PG_QUERY', 'PG_RESTORE', 'PG_SCHEMA', 'RESTORE_DIFF_ACCESS_ACL', 'RESTORE_DIFF_ACCESS_ACL_COLUMN', 'RESTORE_DIFF_ACCESS_ACL_DATABASE', 'RESTORE_DIFF_ACCESS_ACL_DEFAULT', 'RESTORE_DIFF_ACCESS_ACL_FUNCTION', 'RESTORE_DIFF_ACCESS_ACL_RELATION', 'RESTORE_DIFF_ACCESS_ACL_SCHEMA', 'RESTORE_DIFF_ACCESS_ACL_TYPE', 'RESTORE_DIFF_ACCESS_OBJECT_SET', 'RESTORE_DIFF_ACCESS_ORDER', 'RESTORE_DIFF_ACCESS_OWNER', 'RESTORE_DIFF_OTHER_SHAPE', 'RESTORE_DIFF_SEQUENCES', 'RESTORE_DIFF_TABLES']}
