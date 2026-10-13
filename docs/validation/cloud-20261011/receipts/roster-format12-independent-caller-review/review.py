from pathlib import Path
import ast,hashlib,json,os,re,subprocess,zipfile
P=Path(__file__).parent;A=Path('/workspace/scratch/rct-validation/roster_academic_auth_backend');W=Path('/workspace/rct-worktrees/roster-academic-auth-backend')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
ready=json.loads((A/'format-source-ready.safe.json').read_text());plan=json.loads((A/'format-plan.safe.json').read_text());manifest=json.loads((A/'semantic-source13-manifest.safe.json').read_text())
for n,value in ready['script_hashes'].items():assert sha(A/n)==value
semantic=json.loads(Path('/workspace/scratch/rct-validation/roster_academic_backend_implementation_source_review/review.safe.json').read_text())
assert semantic['frozen_review_commit']==plan['base']==manifest['source_commit']=='895d404f23beb47c93ca210be6f9161cd09945ec'
reviewed={r['path']:r['sha256'] for r in semantic['source_rows']};assert manifest['files']==reviewed
targets=manifest['java_targets'];assert len(targets)==len(set(targets))==12 and set(targets)=={n for n in reviewed if n.endswith('.java')}
init=(A/'format-journal.init.gradle').read_text();init_targets=re.findall(r'"(services/[^"\n]+\.java)"',init);assert init_targets==targets
assert 'target(rootProject.files(javaTargets))' in init and 'actualTargets != javaTargets.toSet()' in init and 'googleJavaFormat' not in init and 'clear' not in init and 'replaceStep' not in init
root=(W/'build.gradle.kts').read_bytes();base=subprocess.check_output(['git','-C',str(W),'show',plan['base']+':build.gradle.kts']);assert root==base
assert b'id("com.diffplug.spotless") version "7.0.4"' in root and b'googleJavaFormat("1.25.2").aosp()' in root
assert plan['gradle_arguments'][-1]=='formatJava' and plan['gradle_check_arguments'][-1]=='formatJavaCheck'
assert plan['gradle_arguments'][:-1]==plan['gradle_check_arguments'][:-1]
meta=[]
for n,value in {**manifest['files'],**manifest['protected_files']}.items():
 p=W/n;s=p.lstat();assert sha(p)==value and not p.is_symlink() and s.st_uid==1000 and s.st_nlink==1
 meta.append({'path':n,'uid':s.st_uid,'gid':s.st_gid,'mode':oct(s.st_mode&0o777),'nlink':s.st_nlink,'sha256':value})
def function(p,name):return ast.get_source_segment(p.read_text(),next(n for n in ast.parse(p.read_text()).body if isinstance(n,ast.FunctionDef) and n.name==name))
same={n:function(A/'run-format-owned.py',n)==function(A/'run-red-owned.py',n) for n in ['digest','utc','write','observe','status']};assert all(same.values())
same_child={n:function(A/'logged-format-child.py',n)==function(A/'logged-red-child.py',n) for n in ['digest','private_json','identity']};assert all(same_child.values())
driver=(A/'run-format-owned.py').read_text();child=(A/'logged-format-child.py').read_text()
assert driver.index('write("parent-authority.private.json"')<driver.index('result = guard.run_owned(')
assert child.index('plan, run = admit_child(scope)')<child.index('with log.open("xb")')
assert child.index('fresh_check_dispatch(scope, plan, run)')<child.index('*plan["gradle_check_arguments"]')
assert 'digest(scope / "format-journal.init.gradle") == plan["init_sha256"]' in child
assert 'tracked_changed).issubset' in child and 'changed.issubset' in child
controls=[]
expected=set(targets)
for label,actual,allowed in [('exact12',set(targets),True),('missing1',set(targets[:-1]),False),('extraRootConfig',expected|{'build.gradle.kts'},False),('extraGenerated',expected|{'services/x/build/generated/Stub.java'},False),('sameCountForeign',set(targets[:-1])|{'proto/academic.proto'},False)]:
 observed=actual==expected;assert observed==allowed;controls.append({'target_guard':label,'accepted':observed})
for label,changed,allowed in [('onlyTargets',expected,True),('empty',set(),True),('proto',{'proto/academic.proto'},False),('protectedTLS',set(manifest['protected_files']),False),('RootConfig',{'build.gradle.kts'},False)]:
 observed=changed.issubset(expected);assert observed==allowed;controls.append({'post_apply_guard':label,'accepted':observed})
result={'verdict':'APPROVE_SOURCE','semantic_review_reused':'81ced93df19d02a43d7711f406ec60f70ec40fbb50469e784f9a2f4612660eca','semantic_commit':plan['base'],'source_manifest_sha256':sha(A/'semantic-source13-manifest.safe.json'),'source_pins':ready['script_hashes'],'target_set_exact':targets,'canonical_rule':'Root build file byte exact; Spotless7.0.4/googleJavaFormat1.25.2/AOSP/steps unchanged; scratch init replaces only Java targets with exact12 and checks actual resolved target set before processing.','metadata':meta,'parent_functions_byte_exact':same,'child_existing_admission_primitives_byte_exact':same_child,'selected_finite_set_oracles':controls,'gate_order':'Fresh sealed Root grant/private source closure/semantic13+protected2 and init pins/live direct parent birth+cmdline clock before apply; allowed changed subset and fresh parent/grant/source/clock recheck before second Gradle check. Parent envelope before dispatch; no STARTED race.','bounded_scope':'One owned540 phase contains apply then check, cleanup>=60/TERM15/KILL10,4GiB disk floor/2GiB growth/4GiB sampled ownedRSS/growth/15GiB shared stop/1GiB reserve/8MiB private log; unchanged b78/exact births and cleanup.','findings':[],'native':'NOT_RUN','limits':['Source approval is not a fresh runtime grant/actual capacity/formatter PASS or semantic-equivalence receipt.','Actual resolved target marker12, two native outcomes, exact changed-path/mode/source snapshots and old13/protected2/config preservation must be retained after run.','Formatter can alter import layout; prove final token/literal/comment/qualified import equivalence to895 before reusing semantic approval. No blanket AST normalization or postformat product PASS.','Path resolution/O_NOFOLLOW/private600/live-parent checks provide normal accidental-bypass containment; no adversarial same-UID cryptographic isolation.','Private envelope/raw formatter log must stay outside SAFE artifacts; sampled resource monitoring is not hard OS allocation containment.']}
os.umask(0o077)
with (P/'review.safe.json').open('x') as f:json.dump(result,f,indent=2);f.write('\n')
with (P/'REPORT.md').open('x') as f:f.write('APPROVE_SOURCE: formatter caller/init only. Exact12 handwritten Java targets match independently approved semantic13 ledger; Root Spotless7.0.4/google1.25.2 AOSP and steps/config byte exact. Init changes target list only, checks actual target equality. Parent fresh source/grant/identity gate precedes apply; child checks allowed delta and fresh owned authority before formatJavaCheck. Ten finite set-oracle controls and unchanged status/observe/privacy identity primitives checked; no runtime/native executed.\n\nFresh Root capacity/grant, native marker+apply/check statuses, exact post-run path/mode snapshots and final mechanical-equivalence evidence remain required. Semantic81ced review reused; no full backend review replay.\n')
files=[p for p in P.iterdir() if p.is_file()]
with (P/'manifest.safe.json').open('x') as f:json.dump({'verdict':'APPROVE_SOURCE','files':[{'path':p.name,'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted(files)]},f,indent=2);f.write('\n')
with zipfile.ZipFile(P/'safe-source-review.zip','x',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(files)+[P/'manifest.safe.json']:z.write(p,p.name)
print(json.dumps({'verdict':'APPROVE_SOURCE','manifest_sha256':sha(P/'manifest.safe.json'),'zip_sha256':sha(P/'safe-source-review.zip'),'zip_bytes':(P/'safe-source-review.zip').stat().st_size,'set_controls':len(controls)}))
