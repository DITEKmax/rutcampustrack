from pathlib import Path
import json,hashlib,subprocess,collections
repo=Path('/workspace/rutcampustrack');out=Path('/workspace/scratch/rct-validation/source_manifest_recovery');a=json.loads((out/'affected-source-records.json').read_bytes());rev='3908fad9624f97aec8891fc79338ceb6b0616bf3'
tracked=set(subprocess.check_output(['git','ls-tree','-r','--name-only',rev],cwd=repo,text=True).splitlines())
rows=[]
for e in a:
 obs={'source':e['source'],'canonical_path':e['canonical_path'],'target':e['target'],'canonical_tracked_at_baseline':e['canonical_path'] in tracked,'target_tracked_at_baseline':e['target'] in tracked}
 for label in ('target','canonical_path'):
  p=repo/e[label];obs[label+'_exists']=p.is_file()
  if p.is_file():obs[label+'_current_sha256']=hashlib.sha256(p.read_bytes()).hexdigest()
 rows.append(obs)
paths=['docs/product','docs/implementation','docs/wireframes','docs/design','docs/research/reference-rutcampustrack-design/knowledge']
refs=[]
for path in paths:
 for p in (repo/path).rglob('*'):
  if not p.is_file() or p.suffix.lower() not in ('.md','.yaml','.yml','.json','.csv','.txt'):continue
  text=p.read_text(errors='replace')
  for e in a:
   needles={e['source'],e['target'],e['canonical_path'],e['source_path']}
   for needle in needles:
    if needle and needle in text:
     for n,line in enumerate(text.splitlines(),1):
      if needle in line:refs.append({'file':str(p.relative_to(repo)),'line':n,'source':e['source'],'matched_address':needle,'excerpt':line[:500]})
     break
(out/'target-observations.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n');(out/'accepted-source-inbound-refs.json').write_text(json.dumps(refs,ensure_ascii=False,indent=2)+'\n')
print('affected',len(a),'prefixes',dict(collections.Counter('repo-history' if e['source'].startswith('repo-history:') else 'inactive-prompts' if '/prompts/' in e['source'] else 'renders-qa-images' for e in a)))
print('canonical exists',sum(r['canonical_path_exists'] for r in rows),'canonical tracked',sum(r['canonical_tracked_at_baseline'] for r in rows),'target exists',sum(r['target_exists'] for r in rows),'target tracked',sum(r['target_tracked_at_baseline'] for r in rows))
print('missing canonical',[(r['source'],r['canonical_path']) for r in rows if not r['canonical_path_exists']]);print('inbound refs',len(refs));print(json.dumps(refs[:25],ensure_ascii=False,indent=2))
