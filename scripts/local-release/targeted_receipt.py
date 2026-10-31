"""Bind selected native PASS to exact source, nonce, exits and private XML."""
from pathlib import Path
import hashlib
import json
import os
import re
import stat
import xml.etree.ElementTree as ET
from diagnostics import read

CLASSES={
    'ru.rutcampustrack.academic.grpc.RedisHeadmanRateLimiterIT':1,
    'ru.rutcampustrack.academic.subjectdeletion.SubjectDeletionHttpIT':14,
    'ru.rutcampustrack.academic.botcredentials.BotCredentialBrokerBridgeIT':1,
}
MODULE='services/academic-service/academic-app'
GATES=('setup','security-public-object-prepare','gitleaks','academic-targeted')
def sha(raw):return hashlib.sha256(raw).hexdigest()
def load(path):return json.loads(read(path,0))

def selected_xml(root,since):
    directory=root/MODULE/'build/test-results/integrationTest'
    if directory.resolve()!=directory:raise ValueError('Linked native XML directory')
    paths=sorted(directory.glob('TEST-*.xml'))
    if len(paths)!=len(CLASSES):raise ValueError('Exact selected native XML inventory required')
    result=[];observed=set()
    for path in paths:
        raw=read(path,since)
        if b'<!DOCTYPE' in raw.upper() or b'<!ENTITY' in raw.upper():raise ValueError('XML declaration')
        suite=ET.fromstring(raw);identity=suite.get('name')
        if suite.tag!='testsuite' or identity not in CLASSES or identity in observed:raise ValueError('Selected class identity')
        observed.add(identity);cases=suite.findall('testcase');expected=CLASSES[identity]
        if len(cases)!=expected or int(suite.get('tests','-1'))!=expected:raise ValueError('Selected native cases')
        if any(int(suite.get(k,'0'))!=0 for k in ('failures','errors','skipped')):raise ValueError('Selected class failure/skip')
        for case in cases:
            if case.get('classname')!=identity or any(case.find(k) is not None for k in ('failure','error','skipped')):
                raise ValueError('Selected case failure/skip/identity')
        result.append({'class':identity,'cases':expected,'path':str(path.relative_to(root)).replace('\\','/'),'sha256':sha(raw)})
    if observed!=set(CLASSES):raise ValueError('Missing selected class')
    return result

def make(root,audit,revision,nonce,results,result_raw=None):
    if set(results)!=set(GATES):raise ValueError('Targeted gates')
    for outcome in results.values():
        if outcome.get('status')!='PASS' or outcome.get('nativeExit')!=0 or outcome.get('cause') is not None or outcome.get('childrenReaped') is not True or outcome.get('sourceRestoredExact') is not True or outcome.get('freshNativeArtifacts') is not True or outcome.get('revision')!=revision or outcome.get('nonce')!=nonce:
            raise ValueError('Targeted native PASS required')
    identity=load(audit/'identity.safe.json')
    if identity.get('revision')!=revision or identity.get('nonce')!=nonce or identity.get('mode')!='targeted':raise ValueError('Targeted source nonce')
    artifacts=audit/'native-artifacts/academic-targeted'
    xml=selected_xml(artifacts,results['academic-targeted']['startedNs'])
    manifest=load(artifacts/'manifest.safe.json')
    for item in xml:
        if not any(m.get('path')==item['path'] and m.get('sha256')==item['sha256'] for m in manifest):raise ValueError('XML manifest binding')
    files=['identity.safe.json','result.safe.json','native-artifacts/academic-targeted/manifest.safe.json','gitleaks.sarif']+[g+'.safe.json' for g in GATES]
    sources=[]
    for identity in CLASSES:
        name=MODULE+'/src/test/java/'+identity.replace('.','/')+'.java'
        sources.append({'path':name,'sha256':sha((root/name).read_bytes())})
    return {'schema':1,'kind':'TARGETED_NATIVE_PASS','revision':revision,'nonce':nonce,
            'nativeExit':0,'cases':16,'classes':xml,'sources':sources,
            'bindings':[{'path':name,'sha256':sha(result_raw if name=='result.safe.json' and result_raw is not None else read(audit/name,0))} for name in files]}

def verify(root,audit,revision):
    if not audit.is_absolute() or audit.is_symlink() or audit.resolve()!=audit:raise ValueError('Exact private targeted audit required')
    info=audit.stat()
    if info.st_uid!=os.getuid() or stat.S_IMODE(info.st_mode)&0o077:raise ValueError('Private targeted audit owner/mode')
    receipt=load(audit/'targeted-pass.safe.json');result=load(audit/'result.safe.json')
    if result.get('accepted') is not True or result.get('mode')!='targeted' or result.get('fullReplay') is not False or result.get('hold') is not False or result.get('cancelled') is not False:raise ValueError('Accepted selected run required')
    nonce=receipt.get('nonce')
    if not isinstance(nonce,str) or re.fullmatch('[a-f0-9]{24}',nonce) is None:raise ValueError('Targeted nonce')
    if receipt!=make(root,audit,revision,nonce,result['gates']):raise ValueError('Targeted receipt binding')
    return {'revision':revision,'nonce':nonce,'receiptSha256':sha(read(audit/'targeted-pass.safe.json',0)),'cases':16}
