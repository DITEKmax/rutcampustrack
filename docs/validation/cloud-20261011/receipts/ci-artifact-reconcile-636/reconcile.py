import collections
import hashlib
import json
import pathlib
import shutil
import stat
import subprocess
import zipfile

ROOT = pathlib.Path('/workspace/rutcampustrack')
OUT = pathlib.Path(__file__).parent
INPUTS = json.loads((OUT / 'inputs.json').read_text())
HEAD = '63647fc9b63175e1e32b0773d57affc637ea1065'
CURRENT = 'eec3e0a864626160d33ae29da2cf3b0640e30caa'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def write(name, value):
    (OUT / name).write_text(json.dumps(value, indent=2) + '\n')

def git_bytes(rev, path):
    p = subprocess.run(['git', 'show', rev + ':' + path], cwd=ROOT, capture_output=True)
    return p.stdout if p.returncode == 0 else None

archives = []
cases = []
reports = []
bridges = {}
for artifact in INPUTS['artifacts']:
    reusable = OUT / 'immutable-artifacts' / (str(artifact['id']) + '.zip')
    p = reusable if reusable.exists() else pathlib.Path(artifact['local']['path'])
    data = p.read_bytes()
    assert sha(data) == artifact['digest'].removeprefix('sha256:')
    assert len(data) == artifact['size_in_bytes']
    target = OUT / 'immutable-artifacts' / (str(artifact['id']) + '.zip')
    target.parent.mkdir(exist_ok=True)
    if target.exists():
        assert target.read_bytes() == data
    else:
        shutil.copyfile(p, target)
    target.chmod(0o444)
    with zipfile.ZipFile(p) as z:
        members = z.infolist()
        assert [i.filename for i in members] == ['safe-outcomes.json']
        i = members[0]
        assert not i.is_dir() and not stat.S_ISLNK(i.external_attr >> 16)
        assert i.file_size < 4 * 1024 * 1024
        payload = z.read(i)
        report = json.loads(payload)
        assert set(report) == {'schemaVersion','metadata','collection','acceptance','xml','cases','summary'}
        assert report['schemaVersion'] == 1 and report['acceptance'] == 'NOT_ASSESSED'
        assert report['metadata']['prHeadSha'] == HEAD
        assert report['metadata']['runId'] == '38184430139'
        assert report['metadata']['event'] == 'pull_request'
        assert report['metadata']['testStepOutcome'] == 'success'
        observed = collections.Counter(c['status'] for c in report['cases'])
        assert all(observed[k] == v for k,v in report['summary'].items())
        suites = {(x['ordinal'], s['ordinal']):s for x in report['xml'] for s in x['suites']}
        for x in report['xml']:
            assert x['state'] == 'PARSED'
            for suite in x['suites']:
                selected = [c for c in report['cases'] if c['xmlOrdinal'] == x['ordinal'] and c['suiteOrdinal'] == suite['ordinal']]
                counts = collections.Counter(c['status'] for c in selected)
                assert all(counts[k] == v for k,v in suite['observed'].items())
                declared = suite['declared']
                consistent = declared['tests'] == len(selected) and declared['failures'] == counts['FAILED'] and declared['errors'] == counts['ERROR'] and declared['skipped'] == counts['SKIPPED']
                assert consistent == suite['countsConsistent']
        for c in report['cases']:
            assert set(c) <= {'xmlOrdinal','suiteOrdinal','ordinal','classname','method','status','identity','source','sourceSha256'}
            safe = {**c,'artifactId':artifact['id'],'metadata':report['metadata'],'originalXmlSha256':report['xml'][c['xmlOrdinal']-1]['sha256']}
            cases.append(safe)
            if 'source' in c and c['source'] not in bridges:
                path = c['source']
                before = git_bytes(HEAD,path)
                current = git_bytes(CURRENT,path)
                work = (ROOT/path).read_bytes() if (ROOT/path).is_file() else None
                bridges[path] = {'source':path,'artifactSourceSha256':c['sourceSha256'],'headSha256':sha(before) if before is not None else None,'currentSha256':sha(current) if current is not None else None,'workingSha256':sha(work) if work is not None else None,'headMatchesArtifact':before is not None and sha(before)==c['sourceSha256'],'currentMatchesArtifact':current is not None and sha(current)==c['sourceSha256']}
        archives.append({'artifactId':artifact['id'],'artifactName':artifact['name'],'originalArchive':str(target.relative_to(OUT)),'sha256':sha(data),'bytes':len(data),'githubDigestVerified':True,'members':[{'path':'safe-outcomes.json','sha256':sha(payload),'bytes':len(payload)}]})
        reports.append({'artifactId':artifact['id'],'metadata':report['metadata'],'summary':report['summary'],'xmlCount':len(report['xml']),'suiteCount':len(suites),'collection':report['collection'],'inconsistentSuites':sum(not s['countsConsistent'] for s in suites.values()),'ordinalOnly':sum(c['identity']=='ORDINAL_ONLY' for c in report['cases'])})
write('artifact-qualification.json',reports)
write('all-safe-cases.json',cases)
write('test-source-identity.json',list(bridges.values()))
write('transfer-manifest.json',{'producerHeadSha':HEAD,'currentReviewedSha':CURRENT,'workflowRunId':38184430139,'event':'pull_request','source':'Native GitHub workflow artifact download, Sediment-backed materialization','archives':archives,'originalXMLAvailability':'Not included by design. Original XML SHA and case outcomes retained by reviewed privacy collector; original ZIP bytes preserved exactly. No independent reparse of original XML claimed.','runtimeReruns':0})
print(json.dumps({'artifacts':len(reports),'cases':len(cases),'summaries':reports,'testSources':len(bridges),'headSourceMismatches':[b['source'] for b in bridges.values() if not b['headMatchesArtifact']],'currentSourceChanges':[b['source'] for b in bridges.values() if not b['currentMatchesArtifact']]},indent=2))
