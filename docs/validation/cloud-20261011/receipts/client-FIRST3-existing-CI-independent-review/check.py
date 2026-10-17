"""Independent portable-FIRST3 source/static controls. No target JS/native imports or runtime."""
import datetime,hashlib,json,re,stat,subprocess,zipfile
from pathlib import Path,PurePosixPath
import yaml
HERE=Path(__file__).parent
PACK=HERE.parent/'client_real_acceptance/ci-first3-db177'
WT=Path('/workspace/rct-worktrees/client-ci-first3')
ROOT=Path('/workspace/rutcampustrack')
BASE='db177beeb9660f5a17d7fadf660e024e28f9f4b9'
COMMIT='f5a0d60000332f6560856e527e91e3defcff8a54'
checks=[]
def sha(b):return hashlib.sha256(b).hexdigest()
def git(*args):return subprocess.run(['/usr/bin/git','-C',str(ROOT),*args],env={'PATH':'/usr/bin:/bin','LANG':'C.UTF-8','GIT_OPTIONAL_LOCKS':'0'},check=True,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,timeout=30).stdout
assert sha((PACK/'manifest.safe.json').read_bytes())=='cb7cc19e14d52ece50ba2cb515e0f0f0b376c2479e7e59255968ac41ac11dc8d'
m=json.loads((PACK/'manifest.safe.json').read_text())
assert m['base']==BASE and m['commit']==COMMIT and len(m['files'])==9
zpath=PACK/'first3-ci-source.safe.zip'
assert sha(zpath.read_bytes())=='705151b0e25cfdbf7da4e311d73f222ddbe7fcad362d71cc5b84cb63333f8bdd'
entries={}
with zipfile.ZipFile(zpath) as z:
 assert len(z.infolist())==len(set(z.namelist()))==10 and z.testzip() is None
 for info in z.infolist():
  p=PurePosixPath(info.filename);assert not p.is_absolute() and '..' not in p.parts and str(p)==info.filename
  assert stat.S_IFMT(info.external_attr>>16) in (0,stat.S_IFREG);entries[info.filename]=z.read(info)
 for r in m['files']:assert len(entries[r['path']])==r['bytes'] and sha(entries[r['path']])==r['sha256']
 assert entries['manifest.safe.json']==(PACK/'manifest.safe.json').read_bytes()
for p in ['.github/workflows/ci.yml','tests/e2e/playwright.first3.config.ts','tests/e2e/first3-specs/mobile-first3.spec.ts','tests/e2e/fixtures/first3-observer.ts']:assert git('show',COMMIT+':'+p)==entries[p]
checks.append('all9_manifest_members_ZIP10_CRC_paths_exact4_frozen_Git_sourcecopies')
workflow=yaml.load(entries['.github/workflows/ci.yml'],Loader=yaml.BaseLoader)
old=yaml.load(git('show',BASE+':.github/workflows/ci.yml'),Loader=yaml.BaseLoader)
job=workflow['jobs']['e2e-auth'];oldjob=old['jobs']['e2e-auth']
def step(name):return next(s for s in job['steps'] if s.get('name')==name)
new_rest={k:v for k,v in workflow.items() if k!='jobs'};old_rest={k:v for k,v in old.items() if k!='jobs'}
assert new_rest==old_rest
assert {k:v for k,v in workflow['jobs'].items() if k!='e2e-auth'}=={k:v for k,v in old['jobs'].items() if k!='e2e-auth'}
assert job['timeout-minutes']==oldjob['timeout-minutes']=='30'
for name in ['Generate self-signed TLS cert','Link synthetic Telegram teacher in the ephemeral database','Install Playwright + browsers','Run auth-flow @smoke specs (chromium only)','Stop isolated e2e containers']:
 assert step(name)==next(s for s in oldjob['steps'] if s.get('name')==name)
checks.append('all_original_triggers_otherjobs_gates9smoke_TLS_seed_install_cleanup_exact_30min')
boot=step('Boot isolated Vue auth stack')['run'];assert boot.count('up -d --build --wait --wait-timeout 480')==1
assert job['steps'].index(step('Run accepted FIRST3 with closed diagnostics (chromium only)'))>job['steps'].index(step('Run auth-flow @smoke specs (chromium only)'))
assert 'continue-on-error' not in step('Run accepted FIRST3 with closed diagnostics (chromium only)')
checks.append('FIRST3_after_successful_smoke_blocking_oneboot480')
expected=['${{ env.RCT_FOCUS3_RUN }}/artifacts/safe-results.json']+['${{ env.RCT_FOCUS3_RUN }}/supervision/observations-'+r+'.safe.json' for r in ['student','headman','helper']]
def safe_upload(j):
 uploads=[s for s in j['steps'] if s.get('uses','').startswith('actions/upload-artifact@')]
 return len(uploads)==1 and uploads[0]['with']['path'].splitlines()==expected
assert safe_upload(job) and not safe_upload(oldjob)
for unsafe in ['playwright-report/**','docker-logs/**','${{ env.RCT_FOCUS3_RUN }}/**','${{ env.RCT_FOCUS3_RUN }}/artifacts/error-context.md']:
 bad=json.loads(json.dumps(job));next(s for s in bad['steps'] if s.get('uses','').startswith('actions/upload-artifact@'))['with']['path']=unsafe;assert not safe_upload(bad)
checks.append('exact4_canonical_SAFE_JSON_upload_paths_4unsafe_mutations_rejected')
config=entries['tests/e2e/playwright.first3.config.ts'].decode();wrapper=entries['tests/e2e/first3-specs/mobile-first3.spec.ts'].decode();observer=entries['tests/e2e/fixtures/first3-observer.ts']
r1=HERE.parent/'client_real_acceptance/focused3-preparation-5ced-r1'
before=(r1/'observer.ts').read_bytes();assert sha(before)=='4650d61052bbc6f5bb7171e529389da11331898e8aecdd4da807c69a2e26df40'
assert observer==before.replace(b"'/workspace/rutcampustrack/tests/e2e/node_modules/@playwright/test'",b"'@playwright/test'").replace(b"'/workspace/rutcampustrack/tests/e2e/fixtures/mobile-dataset'",b"'./mobile-dataset'")
checks.append('reviewed4650_observer_only2_portable_import_substitutions')
assert "require('../specs/mobile-real-server.spec.ts')" in wrapper and wrapper.index('test.beforeEach')<wrapper.index('test.afterEach')<wrapper.index('require(')
assert "import { test } from '../fixtures/mobile-dataset'" in wrapper
assert "workers: 1" in config and 'retries: 0' in config and "project.name === 'chromium'" in config
assert all(config.count(k+": 'off'")==2 for k in ['trace','video','screenshot'])
assert "reporter: [['./fixtures/safe-reporter.ts']]" in config
assert config.count('    teachingInstant,')==2
checks.append('protected_dataset_safe_reporter_serial1_retry0_clock_worker_project_traceoff')
spec=(WT/'tests/e2e/specs/mobile-real-server.spec.ts').read_text()
grep=re.search(r'grep: /(.*)/,',config).group(1)
selected=[i for i,line in enumerate(spec.splitlines(),1) if (match:=re.search(r"test\('([^']+)'",line)) and re.search(grep,match.group(1))]
assert selected==[14,61,142]
assert (WT/'tests/e2e/specs/mobile-real-server.spec.ts').read_bytes()==git('show',BASE+':tests/e2e/specs/mobile-real-server.spec.ts')
checks.append('exact_original_FIRST3_14_61_142_assertion_bodies_unchanged')
compose=yaml.load((WT/'docker-compose.e2e.yml').read_text(),Loader=yaml.BaseLoader)
overlay=yaml.load((WT/'tests/e2e/infra/docker-compose.clock-fixture.yml').read_text(),Loader=yaml.BaseLoader)
assert len(compose['services'])==16 and set(overlay['services'])=={'academic-service','schedule-service','attendance-service','mobile-bff'}
for service,row in compose['services'].items():
 if 'build' in row:
  p=str(PurePosixPath(row['build']['context'])/row['build']['dockerfile']);git('show',BASE+':'+p)
checks.append('actual16_graph_ALL10_Dockerfile_context_relative_reachability_4educational_overlay_only')
academic=git('show',BASE+':services/academic-service/academic-app/Dockerfile').decode()
schedule=git('show',BASE+':services/schedule-service/schedule-app/Dockerfile').decode()
assert 'adduser -S -u 10001 app' in academic and 'adduser -S -u 10002 app' in schedule
assert 'USER app' in academic and 'USER app' in schedule
prepare=step('Prepare disposable FIRST3 clock and private artifacts')['run']
helper=(WT/'tests/e2e/backend-clock/prepare-clock.mjs').read_text()
assert 'umask 077' in prepare and '( umask 022' not in prepare and 'umask 022' not in prepare
assert 'mkdirSync(directory, { mode: 0o755 })' in helper
assert "chmodSync(join(directory, 'clock-fixture.jar'), 0o644)" in helper
assert "'disposable-target'" in helper and "'current-instant'" in helper
assert (0o755 & ~0o077)==0o700 and (0o644 & ~0o077)==0o600
# Regardless of owner UID, a0700 directory cannot permit both distinct non-root container UIDs.
for owner in [1000,1001,10001,10002]:assert sum(uid==owner for uid in [10001,10002])<2
checks.append('P2_CLOCK_MODE_exact_new077_to700_600_denies_distinctUID10001_10002_DAC_RAM')
assert (0o755 & ~0o022)==0o755 and (0o644 & ~0o022)==0o644 and (0o700 & ~0o077)==0o700
checks.append('scoped_public_clock022_private_artifact077_permission_positive_RAM_NOT_APPLIED')
assert 'while ([0, 6].includes(date.getUTCDay()))' in helper
assert "clock.install" not in config+wrapper and 'setFixedTime' not in config+wrapper
dataset=(WT/'tests/e2e/fixtures/mobile-dataset.ts').read_text()
assert 'date: this.today' in dataset and 'teachingInstant' in dataset
headman=(ROOT/'frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue').read_text()
journal=(ROOT/'frontends/mobile-core/src/features/headman-journal/HeadmanJournalScreen.vue').read_text()
app=(ROOT/'frontends/pwa-vue/src/App.vue').read_text();hc=app[app.index('<HeadmanScheduleScreen'):app.index('<TeacherFeatureOwner')]
assert 'const selectedDate = ref(props.selectedDate || moscowToday())' in headman
assert "return new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' })" in headman
assert ':initial-date="selectedDate"' in headman and 'api.listLessons(props.groupId, selectedDate.value, selectedDate.value)' in journal
assert ':selected-date=' not in hc
headman_case=spec[spec.index("test('HEADMAN marks"):spec.index("test('STUDENT helper")]
assert "name: 'Посещаемость'" in headman_case and 'select-date' not in headman_case
date=datetime.date(2026,10,17);teaching=date
while teaching.weekday() in [5,6]:teaching+=datetime.timedelta(days=1)
assert date.isoformat()=='2026-10-17' and teaching.isoformat()=='2026-10-19' and date!=teaching
checks.append('P2_weekend_browserSat_vs_teachingMonday_exact_HEADMAN_dayquery_source_RAM')
for p in ['tests/e2e/.env.ci','tests/e2e/fixtures/users.ts','tests/e2e/fixtures/telegram.ts','tests/e2e/infra/seed-telegram.sql']:
 assert git('show',BASE+':'+p)==(WT/p).read_bytes() # No values or hashes of credential fixtures recorded.
checks.append('public_synthetic_signer_admin_input_byte_preservation_boolean_only')
out={'verdict':'CHANGES_REQUESTED','findings':[{'id':'CI-F3-CLOCK-MODE-001','priority':2,'cause':'new prepare outer077 creates700 clockdir/600 marker+instant for distinct nonroot consumers'},{'id':'CI-F3-DATE-COHERENCE-002','priority':2,'cause':'weekend teachingMonday dataset/domain versus browserSatSun HEADMAN selectedDate'}],'checks':checks,'checks_count':len(checks),'source_manifest_sha256':sha((PACK/'manifest.safe.json').read_bytes()),'actual_Node_types_discovery_formatter_browser_CI':'NOT_RUN','target_imports_native_private_reads_or_sourcefix':False,'Dockerfile_initial_guessed_path_hypothesis':'RETRACTED actual10 context-relative paths present','selected_requested_model':'gpt-6.1-sol/high','actual_model_effort':'UNKNOWN'}
(HERE/'checks.safe.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps({'verdict':out['verdict'],'groups':len(checks),'findings':2,'native':False}))
